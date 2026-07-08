import os
import subprocess
from typing import List, TypedDict
from pydantic import BaseModel, Field
from langchain_anthropic import ChatAnthropic
from langchain_core.prompts import ChatPromptTemplate
from langgraph.graph import StateGraph, START, END

# ==========================================
# 1. Datenmodelle (Strukturierter Output)
# ==========================================
class ReviewFinding(BaseModel):
    file_name: str = Field(description="Name der Datei, in der das Problem gefunden wurde")
    line_number: str = Field(description="Betroffene Zeilennummern oder Methode")
    category: str = Field(description="Kategorie: Clean Code, Naming, Thread Safety, Performance, Docs/Logging, Framework")
    severity: str = Field(description="Kritikalität: LOW, MEDIUM, HIGH")
    description: str = Field(description="Genaue Beschreibung, was gegen die Richtlinien verstößt")
    suggestion: str = Field(description="Konkreter Verbesserungsvorschlag oder Code-Snippet")

class ReviewResult(BaseModel):
    findings: List[ReviewFinding] = Field(description="Liste aller gefundenen Code-Smells oder Fehler")
    summary: str = Field(description="Eine kurze, allgemeine Zusammenfassung des Reviews")

# ==========================================
# 2. LangGraph State
# ==========================================
class ReviewState(TypedDict):
    git_diff: str
    findings: List[ReviewFinding]
    summary: str
    markdown_report: str

# ==========================================
# 3. Nodes (Die Schritte im Workflow)
# ==========================================
def analyze_diff_node(state: ReviewState) -> ReviewState:
    """Node 1: Analysiert den Code anhand deiner spezifischen Regeln."""
    print("-> Analysiere Git Diff...")

    # LLM Initialisieren (Claude 3.5 Sonnet ist extrem gut im Code Review)
    llm = ChatAnthropic(model="claude-3-5-sonnet-20240620", temperature=0)
    structured_llm = llm.with_structured_output(ReviewResult)

    system_prompt = """You are an expert Senior Java/Spring Boot Developer performing a strict code review on a git diff.
    Analyze the following git diff strictly against these rules:

    1. Clean Code: High cohesion, low coupling. Warn if classes seem to exceed ~200 LOC or methods do more than one thing.
    2. Naming Conventions: Correct suffixes (*Controller, *Service, *Repository). Flag ANY use of '*Interface' suffixes.
    3. Thread Safety: Look for state mutations in singletons (like Spring Beans), race conditions, or improper synchronization.
    4. Performance & Memory Leaks: Look for inefficient loops, missing caching strategies, or resource leaks.
    5. Documentation & Logging: Mandatory English JavaDoc on all public classes/methods explaining why/what. Must use @Slf4j. FLAG if secrets (passwords, tokens, PII) are logged.
    6. Framework Usage: Correct usage of underlying frameworks (Spring Boot, e.g., proper annotations, dependency injection).

    Ignore deleted lines (starting with -) unless they removed something critical. Focus on added or modified lines (starting with +).
    If the code looks perfect, return an empty findings list.
    """

    prompt = ChatPromptTemplate.from_messages([
        ("system", system_prompt),
        ("human", "Here is the git diff:\n\n{diff}")
    ])

    chain = prompt | structured_llm
    result = chain.invoke({"diff": state["git_diff"]})

    return {"findings": result.findings, "summary": result.summary}

def format_report_node(state: ReviewState) -> ReviewState:
    """Node 2: Formatiert die Ergebnisse in einen sauberen Markdown-Report."""
    print("-> Erstelle Markdown Report...")

    findings = state.get("findings", [])
    summary = state.get("summary", "Keine Zusammenfassung verfügbar.")

    report = f"# 🤖 AI Code Review Report\n\n**Zusammenfassung:** {summary}\n\n"

    if not findings:
        report += "✅ **Super! Der Code entspricht allen Richtlinien. Keine Fehler gefunden.**\n"
        return {"markdown_report": report}

    report += f"Es wurden **{len(findings)}** Anmerkungen gefunden:\n\n"

    for idx, f in enumerate(findings, 1):
        icon = "🔴" if f.severity == "HIGH" else "🟡" if f.severity == "MEDIUM" else "🔵"
        report += f"### {idx}. {icon} [{f.category}] in `{f.file_name}`\n"
        report += f"- **Zeile/Kontext:** {f.line_number}\n"
        report += f"- **Problem:** {f.description}\n"
        report += f"- **Vorschlag:** {f.suggestion}\n\n"

    return {"markdown_report": report}

# ==========================================
# 4. Graph aufbauen & kompilieren
# ==========================================
workflow = StateGraph(ReviewState)

workflow.add_node("reviewer", analyze_diff_node)
workflow.add_node("reporter", format_report_node)

# Definiere den Ablauf
workflow.add_edge(START, "reviewer")
workflow.add_edge("reviewer", "reporter")
workflow.add_edge("reporter", END)

app = workflow.compile()

# ==========================================
# 5. Hilfsfunktion & Ausführung
# ==========================================
def get_git_diff() -> str:
    """Führt den git diff Befehl im aktuellen Verzeichnis aus."""
    try:
        # Führt git diff -U10 main...HEAD aus
        result = subprocess.run(
            ["git", "diff", "-U10", "main...HEAD"],
            capture_output=True,
            text=True,
            check=True
        )
        return result.stdout
    except subprocess.CalledProcessError as e:
        print("Fehler beim Ausführen von git diff. Befindest du dich in einem Git-Repository?")
        print(e.stderr)
        return ""

if __name__ == "__main__":
    # Stelle sicher, dass der API Key gesetzt ist
    if not os.environ.get("ANTHROPIC_API_KEY"):
        print("Bitte setze die Umgebungsvariable ANTHROPIC_API_KEY")
        exit(1)

    print("Hole Git Diff...")
    diff_content = get_git_diff()

    if not diff_content:
        print("Kein Diff gefunden oder Fehler aufgetreten.")
        exit(0)

    print(f"Diff erfolgreich geladen ({len(diff_content)} Zeichen). Starte LangGraph Workflow...")

    # Start-State definieren
    initial_state = ReviewState(
        git_diff=diff_content,
        findings=[],
        summary="",
        markdown_report=""
    )

    # Graph ausführen
    final_state = app.invoke(initial_state)

    print("\n" + "="*50 + "\n")
    print(final_state["markdown_report"])