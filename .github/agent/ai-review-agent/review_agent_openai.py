import os
import subprocess
import httpx
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
def analyze_diff_node(state: ReviewState) -> ReviewState:
    """Node 1: Analyzes the code against the swiyu/adesso guidelines."""
    print("-> Analyzing git diff with Qwen (adesso AI Hub)...")

    # Load configuration from environment variables
    adesso_api_key = os.environ.get("ADESSO_API_KEY")
    adesso_base_url = os.environ.get("ADESSO_BASE_URL")
    qwen_model_name = os.environ.get("QWEN_MODEL_NAME", "qwen")

    if not adesso_api_key or not adesso_base_url:
        raise ValueError("ERROR: Please set ADESSO_API_KEY and ADESSO_BASE_URL as environment variables!")

# 2. Initialize LangChain ChatOpenAI
    llm = ChatOpenAI(
        model=qwen_model_name,
        temperature=0,
        api_key=adesso_api_key,
        base_url=adesso_base_url,
        max_tokens=8000, # Set a limit to be safe
        # http_client=httpx.Client(verify=False) # <--- Disables SSL verification for corporate proxies
    )

    # 3. Enforce structured output
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
    12. Configuration Documentation: whenever application properties / configuration or environment variables are added, renamed, or changed (e.g. in application.yml, application-*.yml, @ConfigurationProperties classes, or env-var mappings), the README.md must be updated to document the new/changed property. Flag any such configuration change in the diff that is not reflected in README.md.
    13. Database Migrations: new Flyway migration scripts (e.g. under db/migration) must be backwards compatible following the EMC (Expand-Migrate-Contract) pattern, so the previous application version keeps working against the new schema during a rolling deployment. Flag destructive or breaking changes in the same migration as the expand step, e.g. dropping/renaming columns or tables still used by the current version, adding NOT NULL columns without a default or backfill, or narrowing types. Such changes must be split into separate expand and contract migrations across releases. Also flag edits to already-released migration scripts (migrations must be immutable once released).
    14. Spelling & Language (scoped, not nitpicking): only flag typos that have real impact - in public API names, configuration/property keys, environment variable names, and in log or exception messages. All code comments and JavaDoc must be written in English; flag any non-English comment/JavaDoc. Do NOT report minor typos in local variables or general prose.

    ## Scope & discipline
    - Consider ONLY added/modified lines (starting with '+'). Ignore '-' lines unless a critical safeguard was removed.
    - Derive line numbers from the diff hunk headers ('@@ -a,b +c,d @@').
    - Judge ONLY what is visible in the diff; do NOT assume unseen code.
    - Do NOT report formatting, whitespace, or import ordering (handled by PMD/EditorConfig).
    - Prefer precision over quantity: no speculative or duplicate findings. If unsure, omit it.
    - Map severity to the review categories: HIGH = must fix (Critical), MEDIUM = should fix, LOW = optional/nice to have.
    - For each finding, if the problem is confined to a single line or a few (<= 5) lines, copy those exact source lines (without the leading '+' diff marker) verbatim into "code_snippet". If the finding spans many lines, a whole method or class, leave "code_snippet" as an empty string.

    ## Output
    - Respond in English only.
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
    result = chain.invoke({"diff": state["git_diff"]})

    return {"findings": result.findings, "summary": result.summary}

def format_report_node(state: ReviewState) -> ReviewState:
    """Node 2: Formats the results into a clean Markdown report."""
    print("-> Creating Markdown report...")

    findings = state.get("findings", [])
    summary = state.get("summary", "No summary available.")

    report = f"# 🤖 AI Code Review Report (Qwen)\n\n**Summary:** {summary}\n\n"

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
        snippet = (f.code_snippet or "").strip()
        if snippet:
            report += f"- **Code:**\n\n```java\n{snippet}\n```\n"
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
        return result.stdout
    except subprocess.CalledProcessError as e:
        print(f"Error while running git diff against {target}.")
        print(e.stderr)
        return ""

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
