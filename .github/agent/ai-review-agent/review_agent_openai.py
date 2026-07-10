import os
import re
import time
import subprocess
from langchain_core.messages import SystemMessage
from typing import List, TypedDict
from pydantic import BaseModel, Field
from langchain_openai import ChatOpenAI
from langchain_core.prompts import ChatPromptTemplate
from langgraph.graph import StateGraph, START, END

# ==========================================
# 1. Data models (structured output)
# ==========================================
class ReviewFinding(BaseModel):
    file_name: str = Field(description="Name of the file in which the problem was found")
    line_number: str = Field(description="Affected line numbers or method")
    category: str = Field(description="Category: Clean Code, Naming, Architecture, Thread Safety, Performance, Docs/Logging, Framework, Testing")
    severity: str = Field(description="Severity: LOW, MEDIUM, HIGH")
    description: str = Field(description="Exact description of what violates the guidelines")
    code_snippet: str = Field(default="", description="The exact offending source line(s) from the diff, verbatim, ONLY when it is a single line or a few (<= 5) lines. Empty string if the finding spans many lines or a whole class/method.")
    suggestion: str = Field(description="Concrete improvement suggestion or code snippet")

class ReviewResult(BaseModel):
    findings: List[ReviewFinding] = Field(description="List of all code smells or errors found")
    summary: str = Field(description="A short, general summary of the review")

# ==========================================
# 2. LangGraph State
# ==========================================
class ReviewState(TypedDict):
    git_diff: str
    findings: List[ReviewFinding]
    summary: str
    markdown_report: str

# ==========================================
# 3. Nodes (the steps in the workflow)
# ==========================================
def _split_diff_by_file(diff: str) -> List[str]:
    """Splits a unified git diff into per-file chunks.

    Reviewing each file in its own request keeps every LLM call small, which
    avoids upstream gateway timeouts (504) on large diffs and lets one slow or
    failing file be skipped without aborting the whole review.
    """
    if not diff.strip():
        return []
    # Split right before each "diff --git " line (kept, not removed) without
    # matching the marker mid-line, so it stays correct even for diffs of
    # this very script (which contains that literal string).
    chunks = re.split(r"(?m)^(?=diff --git )", diff)
    return [chunk for chunk in chunks if chunk]


# File paths matching any of these regexes are skipped entirely: they contain
# documentation, instructions, example/template code (e.g. placeholders like
# "XXX") or generated artefacts that must not be judged against production-code
# rules (naming, architecture, etc.), which would otherwise produce
# false-positive findings.
_EXCLUDED_FILE_PATTERNS = [
    re.compile(r"\.md$", re.IGNORECASE),
    re.compile(r"^\.github/instructions/"),
    re.compile(r"^\.github/agent/"),
    re.compile(r"^openapi\.yaml$"),  # auto-generated via 'mvn verify -P generate-doc', not hand-written
]


def _extract_file_path(chunk: str) -> str:
    """Extracts the 'b/...' target file path from a per-file diff chunk's header line."""
    match = re.search(r"^diff --git a/\S+ b/(\S+)", chunk, re.MULTILINE)
    return match.group(1) if match else ""


def _is_excluded(file_path: str) -> bool:
    """Checks whether a file path matches one of the documentation/instructions exclusion patterns."""
    return any(pattern.search(file_path) for pattern in _EXCLUDED_FILE_PATTERNS)


def _build_llm() -> ChatOpenAI:
    """Creates the configured chat model, failing early on missing configuration."""
    adesso_api_key = os.environ.get("ADESSO_API_KEY")
    adesso_base_url = os.environ.get("ADESSO_BASE_URL")
    # LLM_MODEL_NAME is the generic name; QWEN_MODEL_NAME is kept as a fallback
    # for backwards compatibility since the model behind this proxy is
    # interchangeable (e.g. Qwen, Gemma, ...).
    llm_model_name = os.environ.get("LLM_MODEL_NAME", os.environ.get("QWEN_MODEL_NAME", "qwen"))

    if not adesso_api_key or not adesso_base_url:
        raise ValueError("ERROR: Please set ADESSO_API_KEY and ADESSO_BASE_URL as environment variables!")

    return ChatOpenAI(
        model=llm_model_name,
        temperature=0,
        api_key=adesso_api_key,
        base_url=adesso_base_url,
        max_tokens=int(os.environ.get("LLM_MAX_TOKENS", "5000")), # Must be high enough for the full JSON; truncated output cannot be parsed
        timeout=float(os.environ.get("LLM_TIMEOUT_SECONDS", "300")), # Fail fast instead of hanging
        max_retries=int(os.environ.get("LLM_MAX_RETRIES", "3")), # Retry transient errors (e.g. 504) with backoff
        # http_client=httpx.Client(verify=False) # <--- Disables SSL verification for corporate proxies
    )


def analyze_diff_node(state: ReviewState) -> ReviewState:
    """Node 1: Analyzes the code against the swiyu/adesso guidelines.

    The diff is reviewed file-by-file so each request to the model stays small.
    This prevents 504 gateway timeouts on large changesets and makes the run
    resilient: if a single file times out, its error is recorded and the
    remaining files are still reviewed.
    """
    print("-> Analyzing git diff with the configured LLM (adesso AI Hub)...")

    llm = _build_llm()

    # Enforce structured output
    #structured_llm = llm.with_structured_output(ReviewResult)
    structured_llm = llm.with_structured_output(ReviewResult, method="json_mode")

    system_prompt = """You are a strict but constructive Senior Java/Spring Boot code reviewer for the swiyu-verifier project.
    Review ONLY the changes in the provided unified git diff, strictly against the project guidelines below.

    ## Review rules (only report REAL violations)
    1. Clean Code (SoC/SRP): high cohesion, low coupling. Flag "god classes", classes > ~200 LOC, or methods doing more than one logical task (mixing validation + mapping + I/O + business rules).
    2. Naming Conventions: enforce suffixes *Controller, *Service, *Repository. Flag ANY '*Interface' suffix. Test names must follow 'MethodName_StateUnderTest_ExpectedBehavior' (unit) or 'given_when_then' (integration/application); flag generic names like 'test2()'.
    3. Architecture & Layering:
       - @RestController must live in a '..web..' package, end with 'Controller', and carry a @Tag annotation with a unique 'IF-xxx' interface code.
       - @Service must live in '..service..' and end with 'Service'; repositories belong in '..domain..'.
       - Controllers must ONLY handle HTTP concerns (parsing, headers, status codes, basic validation, delegation). They must NOT access repositories directly or contain persistence/business logic.
       - Business orchestration belongs in '..service..'; repository access happens from the service/domain side.
       - No dependency from 'verifier-service' to 'verifier-application'; no package cycles.
       - Mapping between DTO/service/domain must use dedicated mapper classes, NOT ad-hoc conversion in controllers. Do NOT introduce MapStruct. DTOs must stay transport-focused (no business logic).
    4. Spring & Dependency Injection: use constructor injection with final dependencies (@RequiredArgsConstructor). Flag field injection (@Autowired on fields). @Service/@Component beans must be stateless (no mutable shared state).
    5. Thread Safety: state mutations in singletons/Spring beans, race conditions, unsafe shared fields.
    6. Performance & Memory: N+1 JPA queries, inefficient loops, missing/incorrect caching, resource leaks, blocking calls.
    7. Error Handling: throw clean, specific domain exceptions in the service layer; translate them to HTTP responses via @ControllerAdvice in the web layer.
    8. Documentation & Logging: public classes/interfaces/methods need English JavaDoc explaining why/what (not redundant getters). Prefer @Slf4j with STRUCTURED logging (include identifiers/keys). FLAG any logging of secrets/PII (tokens, passwords, keys). Integration tests must have Javadoc describing what/why, boundary conditions, and expected result.
    9. Framework Usage: correct Spring Boot usage (annotations, transaction boundaries, validation).
    10. Testing Pyramid: unit tests must mock external dependencies and cover edge cases; do not push business-logic assertions into integration tests; do not decrease coverage without reason.
    11. Changelog: any user-facing or otherwise relevant change (feature, bug fix, behavioural/config/API change) must be accompanied by an entry in CHANGELOG.md under the '[NEXT]' section, grouped under Added/Fixed/Changed/Removed, and referencing the ticket number in parentheses (e.g. `(#949)`). Flag relevant code changes in the diff that do NOT include a corresponding CHANGELOG.md update or that omit the ticket reference. Pure refactorings, docs-only or merge commits are exempt.
    12. Configuration Documentation: whenever application properties / configuration or environment variables are added, renamed, or changed (e.g. in application.yml, application-*.yml, @ConfigurationProperties classes, or env-var mappings), the README.md must be updated to document the new/changed property. Flag any such configuration change in the diff that is not reflected in README.md. Do NOT flag pom.xml changes (dependency versions, plugin versions, build-only properties) under this rule - those are build/dependency management, not application configuration, and never require a README.md update.
    13. Database Migrations: new Flyway migration scripts (e.g. under db/migration) must be backwards compatible following the EMC (Expand-Migrate-Contract) pattern, so the previous application version keeps working against the new schema during a rolling deployment. Flag destructive or breaking changes in the same migration as the expand step, e.g. dropping/renaming columns or tables still used by the current version, adding NOT NULL columns without a default or backfill, or narrowing types. Such changes must be split into separate expand and contract migrations across releases. Also flag edits to already-released migration scripts (migrations must be immutable once released).
    14. Spelling & Language (scoped, not nitpicking): only flag typos that have real impact - in public API names, configuration/property keys, environment variable names, and in log or exception messages. All code comments and JavaDoc must be written in English; flag any non-English comment/JavaDoc. Do NOT report minor typos in local variables or general prose.

    ## Scope & discipline
    - Consider ONLY added/modified lines (starting with '+'). Ignore '-' lines unless a critical safeguard was removed.
    - Derive line numbers from the diff hunk headers ('@@ -a,b +c,d @@').
    - Judge ONLY what is visible in the diff; do NOT assume unseen code.
    - Do NOT report formatting, whitespace, or import ordering (handled by PMD/EditorConfig).
    - Do NOT flag JavaDoc, comments or @Schema/@ApiResponse descriptions merely for being verbose, long, or "could be shorter" - only flag them if they violate one of the 14 rules above (missing, wrong language, factually incorrect, or exposing secrets/PII). Verbosity/style alone is never a valid finding.
    - Prefer precision over quantity: no speculative or duplicate findings. If unsure, omit it.
    - Map severity to the review categories: HIGH = must fix (Critical), MEDIUM = should fix, LOW = optional/nice to have.
    - For each finding, if the problem is confined to a single line or a few (<= 5) lines, copy those exact source lines (without the leading '+' diff marker) verbatim into "code_snippet". If the finding spans many lines, a whole method or class, leave "code_snippet" as an empty string.

    ## Output
    - Respond in English only.
    - Keep the response compact to stay within the token limit: write "description" and "suggestion" as ONE short sentence each (no multi-paragraph explanations). Report at most the 10 most important findings per file, prioritising HIGH severity.
    - YOU MUST RETURN A VALID JSON OBJECT WITH THIS EXACT STRUCTURE AND NO OTHER FIELDS:
    {
      "summary": "A brief overall summary of the review.",
      "findings": [
        {
          "file_name": "path/to/file.java",
          "line_number": "line numbers or context",
          "category": "One of: Clean Code, Naming, Architecture, Thread Safety, Performance, Docs/Logging, Framework, Testing",
          "severity": "LOW, MEDIUM, or HIGH",
          "description": "What is wrong",
          "code_snippet": "The exact offending line(s), verbatim, only if <= 5 lines; otherwise empty string",
          "suggestion": "How to fix it"
        }
      ]
    }
    If there are no real violations, return an empty list for findings.
    """

    prompt = ChatPromptTemplate.from_messages([
        SystemMessage(content=system_prompt),
        ("human", "Here is the git diff:\n\n{diff}")
    ])
    chain = prompt | structured_llm

    all_chunks = _split_diff_by_file(state["git_diff"])

    # Skip documentation/instructions/example files upfront: applying Java
    # production-code rules (naming, architecture, ...) to them causes false
    # positives, e.g. flagging an intentional "XXX" placeholder in a template.
    chunks: List[str] = []
    excluded_files: List[str] = []
    for chunk in all_chunks:
        file_path = _extract_file_path(chunk)
        if file_path and _is_excluded(file_path):
            excluded_files.append(file_path)
        else:
            chunks.append(chunk)

    if excluded_files:
        print(f"-> Skipping {len(excluded_files)} documentation/instructions file(s): "
              f"{', '.join(excluded_files)}")

    print(f"-> Reviewing {len(chunks)} changed file(s) individually...")

    # Cap the size of a single file chunk so one huge file cannot trigger a
    # gateway timeout. Large files are truncated rather than dropped entirely.
    # Kept moderate (not 20000+): larger prompts take the model longer to
    # process, increasing the risk of hitting the upstream gateway's fixed
    # read-timeout (504), which our client-side `timeout` setting cannot override.
    max_chunk_chars = int(os.environ.get("MAX_FILE_DIFF_CHARS", "20000"))

    all_findings: List[ReviewFinding] = []
    reviewed = 0
    failed = 0
    for i, chunk in enumerate(chunks, 1):
        if len(chunk) > max_chunk_chars:
            # NOTE: only the first max_chunk_chars characters are reviewed; the
            # remainder of this file's diff is NOT seen by the model.
            print(f"   [{i}/{len(chunks)}] WARNING: diff truncated to {max_chunk_chars} chars, "
                  f"review may be incomplete for this file.")
            chunk = chunk[:max_chunk_chars] + "\n\n[... file diff truncated due to size ...]\n"
        try:
            start = time.monotonic()
            result = chain.invoke({"diff": chunk})
            elapsed = time.monotonic() - start
            all_findings.extend(result.findings)
            reviewed += 1
            print(f"   [{i}/{len(chunks)}] reviewed in {elapsed:.1f}s ({len(result.findings)} finding(s), "
                  f"{len(chunk)} chars).")
        except Exception as e:
            failed += 1
            print(f"   [{i}/{len(chunks)}] skipped due to error: {e}")

    summary = (
        f"Reviewed {reviewed} file(s), {failed} skipped due to errors. "
        f"Found {len(all_findings)} finding(s) in total."
    )
    return {"findings": all_findings, "summary": summary}

def _clean_snippet(raw: str) -> str:
    """Removes diff header artefacts from a code snippet, keeping +/- markers.

    The model occasionally leaves the leading unified-diff markers ('+', '-', ' ')
    or hunk/file headers in the snippet. Strip them so the report shows clean code.
    """
    if not raw:
        return ""
    cleaned_lines = []
    for line in raw.splitlines():
        # Drop diff file/hunk headers entirely
        if line.startswith(("+++", "---", "@@", "diff --git", "index ")):
            continue
        # Strip a single leading diff marker ('+', '-' or ' ') if present
        if line[:1] in ("+", "-", " "):
            line = line[1:]
        cleaned_lines.append(line)
    return "\n".join(cleaned_lines).strip()


# Maps common file extensions to Markdown code-fence languages.
_LANGUAGE_BY_EXTENSION = {
    "java": "java",
    "kt": "kotlin",
    "py": "python",
    "xml": "xml",
    "yml": "yaml",
    "yaml": "yaml",
    "sql": "sql",
    "json": "json",
    "sh": "bash",
    "properties": "properties",
}


def _language_for(file_name: str) -> str:
    """Derives a Markdown code-fence language hint from the file extension."""
    extension = file_name.rsplit(".", 1)[-1].lower() if "." in (file_name or "") else ""
    return _LANGUAGE_BY_EXTENSION.get(extension, "")


def format_report_node(state: ReviewState) -> ReviewState:
    """Node 2: Formats the results into a clean Markdown report."""
    print("-> Creating Markdown report...")

    findings = state.get("findings", [])
    summary = state.get("summary", "No summary available.")

    report = f"# 🤖 AI Code Review Report\n\n**Summary:** {summary}\n\n"

    if not findings:
        report += "✅ **Great! The code complies with all guidelines. No issues found.**\n"
        return {"markdown_report": report}

    report += f"Found **{len(findings)}** remarks:\n\n"

    for idx, f in enumerate(findings, 1):
        icon = "🔴" if f.severity == "HIGH" else "🟡" if f.severity == "MEDIUM" else "🔵"
        report += f"### {idx}. {icon} [{f.category}] in `{f.file_name}`\n"
        report += f"- **Line/Context:** {f.line_number}\n"
        report += f"- **Problem:** {f.description}\n"
        # Show the offending code only when the model provided a short snippet
        snippet = _clean_snippet(f.code_snippet)
        if snippet:
            report += f"- **Code:**\n\n```{_language_for(f.file_name)}\n{snippet}\n```\n"
        report += f"- **Suggestion:** {f.suggestion}\n\n"

    return {"markdown_report": report}

# ==========================================
# 4. Build & compile graph
# ==========================================
workflow = StateGraph(ReviewState)

workflow.add_node("reviewer", analyze_diff_node)
workflow.add_node("reporter", format_report_node)

# Define the flow
workflow.add_edge(START, "reviewer")
workflow.add_edge("reviewer", "reporter")
workflow.add_edge("reporter", END)

app = workflow.compile()

# ==========================================
# 5. Helper function & execution
# ==========================================
def get_git_diff() -> str:
    """Runs the git diff command. In GitHub Actions we use the base ref."""
    # Read the target from the environment variable (default is main...HEAD for local tests)
    target = os.environ.get("GIT_DIFF_TARGET", "main...HEAD")
    try:
        result = subprocess.run(
            ["git", "diff", "-U10", target],
            capture_output=True,
            text=True,
            check=True
        )
        diff = result.stdout
    except subprocess.CalledProcessError as e:
        print(f"Error while running git diff against {target}.")
        print(e.stderr)
        return ""

    # Note: the diff is split per file and each file is size-capped in
    # analyze_diff_node, so no global truncation is applied here.
    return diff

if __name__ == "__main__":
    print("Fetching git diff...")
    diff_content = get_git_diff()

    if not diff_content:
        print("No diff found. Aborting analysis.")
        # We write an empty report so the action does not fail
        with open("ai-review-report.md", "w") as f:
            f.write("✅ **No code changes found that need to be reviewed.**")
        exit(0)

    print(f"Diff loaded successfully ({len(diff_content)} characters). Starting workflow...")

    initial_state = ReviewState(
        git_diff=diff_content,
        findings=[],
        summary="",
        markdown_report=""
    )

    try:
        final_state = app.invoke(initial_state)
        report = final_state["markdown_report"]

        # Write output to a file for the action
        with open("ai-review-report.md", "w") as f:
            f.write(report)

        print("\n✅ Analysis complete. Report saved to 'ai-review-report.md'.")
    except Exception as e:
        print(f"\nAn error occurred: {e}")
