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
    """Node 1: Analysiert den Code anhand der swiyu/adesso Richtlinien."""
    print("-> Analysiere Git Diff mit Qwen (adesso AI Hub)...")

    # Konfiguration aus Umgebungsvariablen laden
    adesso_api_key = os.environ.get("ADESSO_API_KEY")
    adesso_base_url = os.environ.get("ADESSO_BASE_URL")
    qwen_model_name = os.environ.get("QWEN_MODEL_NAME", "qwen")

    if not adesso_api_key or not adesso_base_url:
        raise ValueError("FEHLER: Bitte ADESSO_API_KEY und ADESSO_BASE_URL als Umgebungsvariablen setzen!")

# 2. LangChain ChatOpenAI initialisieren
    llm = ChatOpenAI(
        model=qwen_model_name,
        temperature=0,
        api_key=adesso_api_key,
        base_url=adesso_base_url,
        max_tokens=8000, # Zur Sicherheit ein Limit setzen
        http_client=httpx.Client(verify=False) # <--- Schaltet SSL-Prüfung für Firmenproxys ab
    )

    # 3. Structured Output erzwingen
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
    """Node 2: Formatiert die Ergebnisse in einen sauberen Markdown-Report."""
    print("-> Erstelle Markdown Report...")

    findings = state.get("findings", [])
    summary = state.get("summary", "Keine Zusammenfassung verfügbar.")

    report = f"# 🤖 AI Code Review Report (Qwen)\n\n**Zusammenfassung:** {summary}\n\n"

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
    """Führt den git diff Befehl aus. In GitHub Actions nutzen wir die Base-Ref."""
    # Lese das Ziel aus der Umgebungsvariable (Standard ist main...HEAD für lokale Tests)
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
        print(f"Fehler beim Ausführen von git diff gegen {target}.")
        print(e.stderr)
        return ""

if __name__ == "__main__":
    print("Hole Git Diff...")
    diff_content = get_git_diff()

    if not diff_content:
        print("Kein Diff gefunden. Beende Analyse.")
        # Wir schreiben einen leeren Report, damit die Action nicht fehlschlägt
        with open("ai-review-report.md", "w") as f:
            f.write("✅ **Keine Code-Änderungen gefunden, die überprüft werden müssten.**")
        exit(0)

    print(f"Diff erfolgreich geladen ({len(diff_content)} Zeichen). Starte Workflow...")

    initial_state = ReviewState(
        git_diff=diff_content,
        findings=[],
        summary="",
        markdown_report=""
    )

    try:
        final_state = app.invoke(initial_state)
        report = final_state["markdown_report"]

        # Output für die Action in eine Datei schreiben
        with open("ai-review-report.md", "w") as f:
            f.write(report)

        print("\n✅ Analyse abgeschlossen. Report in 'ai-review-report.md' gespeichert.")
    except Exception as e:
        print(f"\nEin Fehler ist aufgetreten: {e}")