---
name: make-release
description: Guides the release process for swiyu-verifier. Generates a Keep-a-Changelog changelog from git commits, bumps the Maven versions, commits/pushes, and outputs the GitHub release and Confluence/Jira checklists. Use when the user says "Start a Release" or asks to create a new release or changelog.
disable-model-invocation: true
allowed-tools: Bash(git log:*), Bash(git status:*), Bash(git branch:*), Bash(git diff:*), Read, Grep, Glob
---

# Make Release

Du agierst als erfahrener Release Manager. Folge strikt dem Workflow unten, überspring keine Schritte und erzeuge das
Changelog erst, wenn die Commit-Logs vorliegen. Kommuniziere mit dem User auf Deutsch.

## Step 1: Neue Version erfragen

Frage: "Welche Version möchtest du für dieses Release erstellen? (z.B. 1.2.0)". Warte auf die Antwort.

## Step 2: Datum des letzten Releases erfragen

Frage: "Welches Datum hatte das letzte Release? (Format: YYYY-MM-DD)". Warte auf die Antwort.

## Step 3: Commits ermitteln

Führe selbst aus (read-only):

```bash
git log --since="[DATUM]" --oneline
```

Zeige dem User die Commits kurz. Falls die Ausgabe offensichtlich unvollständig wirkt, biete an, dass der User sie
selbst einfügt.

## Step 4: Changelog generieren

Strikte Regeln:

- Gruppiere in "Added", "Fixed" und "Changed".
- Nimm relevante Jira-Ticketnummern in Klammern auf -> (#XXX).
- Kurze, klare Bullet Points.
- Markdown im "Keep a Changelog"-Stil.
- Nur user-facing Änderungen; Refactoring, Doku und Merge-Commits weglassen, ausser sie beeinflussen die Funktionalität.

## Step 5: pom.xml aktualisieren

Direkt nach dem Changelog: Zeige die XML-Snippets für die `<version>`-Tags von `swiyu-verifier-parent` und
`swiyu-verifier-service` mit der neuen Version. Schlage zusätzlich als Alternative vor:

```bash
mvn versions:set -DnewVersion=[VERSION]
```

Führe Änderungen an den pom.xml-Dateien erst nach Bestätigung des Users aus.

## Step 6: SECURITY.md und EXPERIMENTAL.md aktualisieren

Dieser Schritt kommt vor dem Commit, damit die Änderungen im Release-Commit enthalten sind:

- **SECURITY.md:** Passe die Tabelle "Supported Versions" (z.B. "Latest tagged release 5.0.x") und die Branch-Liste unter
  "Bug Bounty" (z.B. `release/5.0.x`) auf den Release-Branch der neuen Version an (Major.Minor aus Step 1, z.B. 5.1.x).
  Frage den User, ob die bisherige Version weiterhin supported bleibt, bevor du sie entfernst.
- **EXPERIMENTAL.md:** Prüfe anhand der Commits aus Step 3, ob Features neu experimentell wurden, produktiv freigegeben
  wurden oder wegfielen, und aktualisiere die Liste entsprechend. Zeige dem User die vorgeschlagenen Änderungen.

Zeige beide Diffs und warte auf Bestätigung des Users. Der Commit-Befehl in Step 8 muss diese Dateien mit einschliessen
(`git commit -am` erfasst bereits alle getrackten Änderungen).

## Step 7: Migration Guide prüfen

Prüfe anhand des Changelogs (Step 4) und der Commits (Step 3), ob ein Migration Guide von der vorherigen Minor-Version
(z.B. 5.0.x) auf die neue (z.B. 5.1.x) nötig ist. Das ist bei jedem Release zwingend zu prüfen und dem User explizit zu
begründen, auch wenn das Ergebnis "nicht nötig" lautet.

Ein Guide ist nötig bei Änderungen, die bestehende Setups beeinflussen:

- Breaking Changes in API, Issuer-Metadata oder Datenmodell (z.B. neue Pflichtfelder, entfernte Werte).
- Neue, geänderte oder entfernte Umgebungsvariablen und geänderte Default-Werte.
- Geändertes Verhalten, auf das Clients, Wallets oder Business Issuer reagieren müssen (z.B. neue HTTP-Status-Codes).
- Datenbank-Migrationen oder Konfigurationsänderungen mit Handlungsbedarf für Operatoren.

Nicht nötig bei rein additiven, optionalen Änderungen ohne Handlungsbedarf, Bugfixes, Dokumentation und
Dependency-Updates (Patch-Releases ebenfalls in der Regel nicht).

Verifiziere Behauptungen im Code (z.B. Validierungsannotationen, Default-Werte in `application.yml`), bevor du sie in den
Guide schreibst, und prüfe, ob die Änderungen im Changelog noch aktuell sind.

Vorgehen:

1. Schaue dir die vorhandenen Guides in `migration-guides/` an (z.B. `guide-4.x.x-to-5.0.x.md`) und übernimm Aufbau und
   Stil: Compatibility summary (Issuance clients / Business verifiers / Operators), danach Breaking Changes mit
   Before/After-Beispielen.
2. Dateiname: `migration-guides/guide-[ALTE_VERSION].x-to-[NEUE_VERSION].x.md` (z.B. `guide-5.0.x-to-5.1.x.md`).
3. Sage dem User das Ergebnis der Prüfung (nötig / nicht nötig, mit Begründung) und zeige bei "nötig" den Entwurf. Lege die
   Datei erst nach Bestätigung des Users an.
4. Die neue Datei ist untracked: `git commit -am` erfasst sie nicht. Sie muss in Step 8 zusätzlich mit `git add`
   aufgenommen werden.

## Step 8: Commit und Push

Zeige die Befehle (Version einsetzen) und führe sie NUR nach ausdrücklicher Bestätigung des Users aus:

```bash
git add migration-guides/guide-[ALTE_VERSION].x-to-[NEUE_VERSION].x.md   # nur falls in Step 7 ein Guide erstellt wurde
git commit -am "chore(release): bump version to [VERSION]"
git push
```

Füge keine anderen untracked Dateien hinzu (z.B. `.claude/`, `review.txt`).

## Step 9: GitHub Release

Leite den Release-Branch aus Major.Minor ab (z.B. 2.4.3 -> `release/2.4.x`) und gib exakt diese Anleitung aus:

**Schritt 9: GitHub Release erstellen**
Bitte führe nun die folgenden Schritte auf GitHub aus:

1. Öffne die Release-Seite: [https://github.com/swiyu-admin-ch/swiyu-verifier/releases](https://github.com/swiyu-admin-ch/swiyu-verifier/releases)
2. Klicke auf den Button **"Draft a new release"**.
3. **Choose a tag:** Tippe `[VERSION]` ein und wähle "Create new tag".
4. **Target:** Wähle den entsprechenden Release-Branch aus (z.B. `[BERECHNETER_BRANCH]`).
5. **Release title:** Gib `swiyu-verifier [VERSION]` ein.
6. **Describe this release:** Kopiere das oben generierte Changelog und füge es hier ein.
7. Klicke auf **"Publish release"**.

## Step 10: Confluence-Dokumentation, Jira-Filter & Highlights

Extrahiere alle eindeutigen Jira-Ticket-IDs (z.B. EIDOMNI-123) aus den Commits von Step 3, entferne Duplikate und
erzeuge einen JQL-String: `key IN (TICKET-1, TICKET-2, TICKET-3)`

Gib exakt diese Anleitung aus:

**Schritt 10: Confluence Dokumentation**
Bitte trage das neue Release nun im Confluence ein:

1. Gehe auf die Seite: **7.2. Generic Components Releases - e-ID Teamspace - Confluence**.
2. Erstelle in der Tabelle einen neuen Eintrag für die Version `[VERSION]`.
3. Nutze den folgenden JQL-Filter, um die zugehörigen Jira-Tickets zu hinterlegen:
   `[GENERIERTER_JQL_FILTER]`

### Swiyu Issuer Releases Seite

Das Release muss zusätzlich auf dieser Seite eingetragen werden:
[7.2.1 Swiyu Issuer Releases](https://confluence.bit.admin.ch/spaces/EIDTEAM/pages/1555065503/7.2.1+Swiyu+Issuer+Releases)

Generiere dafür eine kurze Highlights-Liste zum Copy-Paste in Confluence:

- Nur Bullet Points mit Stichworten, jeweils ein Satz auf Englisch, möglichst kurz.
- Nur die wichtigsten user-facing Punkte aus dem Changelog (Step 4), keine Ticketnummern.
- Ausgabe in einem Markdown-Codeblock, damit der User sie einfach kopieren kann.

Beispiel für den Stil:

```markdown
- Migrated build to Java 25 (LTS) and upgraded to Spring Boot 4.1.1.
- Docker base images updated to Eclipse Temurin 25 JRE
- Added credential_refresh_disabled property to verifier metadata to let business verifiers disable the renewal flow per credential type.
```

Gib danach die Anleitung aus:

1. Öffne die Seite oben.
2. Erstelle einen neuen Eintrag für die Version `[VERSION]` mit dem Release-Datum.
3. Füge die Highlights-Liste ein.
