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
    category: str = Field(description="Category: Clean Code, Naming, Thread Safety, Performance, Docs/Logging, Framework")
    severity: str = Field(description="Severity: LOW, MEDIUM, HIGH")
    description: str = Field(description="Exact description of what violates the guidelines")
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
        http_client=httpx.Client(verify=False) # <--- Disables SSL verification for corporate proxies
    )

    # 3. Enforce structured output
    #structured_llm = llm.with_structured_output(ReviewResult)
    structured_llm = llm.with_structured_output(ReviewResult, method="json_mode")

    system_prompt = """You are an expert Senior Java/Spring Boot Developer performing a strict code review on a git diff.
    Analyze the following git diff strictly against these rules:

    1. Clean Code: High cohesion, low coupling. Warn if classes seem to exceed ~200 LOC or methods do more than one thing.
    2. Naming Conventions: Correct suffixes (*Controller, *Service, *Repository). Flag ANY use of '*Interface' suffixes.
    3. Thread Safety: Look for state mutations in singletons (like Spring Beans), race conditions, or improper synchronization.
    4. Performance & Memory Leaks: Look for inefficient loops, missing caching strategies, or resource leaks.
    5. Documentation & Logging: Mandatory English JavaDoc on all public classes/methods explaining why/what. Must use @Slf4j. FLAG if secrets (passwords, tokens, PII) are logged.
    6. Framework Usage: Correct usage of underlying frameworks (Spring Boot, e.g., proper annotations, dependency injection).

    Ignore deleted lines (starting with -) unless they removed something critical. Focus on added or modified lines (starting with +).

    YOU MUST RETURN A VALID JSON OBJECT WITH THIS EXACT STRUCTURE AND NO OTHER FIELDS:
    {
      "summary": "A brief overall summary of the review.",
      "findings": [
        {
          "file_name": "path/to/file.java",
          "line_number": "line numbers or context",
          "category": "One of: Clean Code, Naming, Thread Safety, Performance, Docs/Logging, Framework",
          "severity": "LOW, MEDIUM, or HIGH",
          "description": "What is wrong",
          "suggestion": "How to fix it"
        }
      ]
    }
    If the code looks perfect, return an empty list for findings.
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
