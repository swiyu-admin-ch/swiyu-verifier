If the user types exactly the trigger phrase "Start Review" or asks to create a new review, you must stop being a standard coding assistant, assume the role of an expert release manager, and strictly follow the workflow below. For all other coding queries, ignore this workflow completely.

# AI Code Review Instructions

This document outlines the workflow for requesting AI-assisted code reviews using diff files, optimized for local execution inside the IDE agent.

## 0. Determine the Target & Diff Source
- Default comparison base branch is `main`. If the user specifies another base (e.g. `develop`, `release/4.0.x`), use that instead.
- Ask (or infer from context) whether committed changes since the base branch, or also uncommitted working-directory changes, should be reviewed:
  - **Committed changes only (default):** `git diff -U10 <base>...HEAD`
  - **Including uncommitted changes:** `git diff -U10 <base>...HEAD -- . ':!*.lock'` combined with `git diff -U10 HEAD` for the working tree, or simply advise the user to `git add -A` first (staged, not committed) and use `git diff -U10 <base> --cached`.
- Do NOT require the user to commit first unless they explicitly want a "clean" review of finished work. If they just want quick local feedback, review uncommitted changes directly.

## 1. Generate the Diff (run it yourself)
Instead of asking the user to run the command and paste the output, run it yourself via the terminal tool and read the resulting file directly:

```bash
git diff -U10 <base>...HEAD > review.txt
```

Then read `review.txt` with the file-reading tool. Only ask the user to run this manually if terminal/file access is unavailable.

## 2. Handle Large Diffs (chunking)
If `review.txt` is very large (e.g. > ~20000 characters or many files), split it per file (each `diff --git ...` block) and review file-by-file, exactly like `review_agent_openai.py` does. This avoids missing findings due to truncation and keeps each analysis focused. Merge all findings into a single final report afterward.

## 3. Apply the Review Rules
Act as a strict but constructive Senior Java/Spring Boot code reviewer for the swiyu-verifier project and review ONLY the changes in the diff (added/modified lines), strictly against the project guidelines below. Ignore removed lines unless a critical safeguard was removed. Do not report formatting, whitespace, or import ordering (handled by PMD/EditorConfig). Prefer precision over quantity - if unsure, omit the finding.

1. **Clean Code (SoC/SRP):** High cohesion, low coupling. Flag "god classes", classes > ~200 LOC, or methods doing more than one logical task (mixing validation + mapping + I/O + business rules).
2. **Naming Conventions:** Enforce suffixes `*Controller`, `*Service`, `*Repository`. Flag ANY `*Interface` suffix. Test names must follow `MethodName_StateUnderTest_ExpectedBehavior` (unit) or `given_when_then` (integration/application); flag generic names like `test2()`.
3. **Architecture & Layering:**
   - `@RestController` must live in a `..web..` package, end with `Controller`, and carry a `@Tag` annotation with a unique `IF-xxx` interface code.
   - `@Service` must live in `..service..` and end with `Service`; repositories belong in `..domain..`.
   - Controllers must ONLY handle HTTP concerns (parsing, headers, status codes, basic validation, delegation) and must NOT access repositories directly or contain persistence/business logic.
   - Business orchestration belongs in `..service..`; repository access happens from the service/domain side.
   - No dependency from `verifier-service` to `verifier-application`; no package cycles.
   - Mapping between DTO/service/domain must use dedicated mapper classes, NOT ad-hoc conversion in controllers. Do NOT introduce MapStruct. DTOs must stay transport-focused (no business logic).
4. **Spring & Dependency Injection:** Use constructor injection with final dependencies (`@RequiredArgsConstructor`). Flag field injection (`@Autowired` on fields). `@Service`/`@Component` beans must be stateless (no mutable shared state).
5. **Thread Safety:** State mutations in singletons/Spring beans, race conditions, unsafe shared fields.
6. **Performance & Memory:** N+1 JPA queries, inefficient loops, missing/incorrect caching, resource leaks, blocking calls.
7. **Error Handling:** Throw clean, specific domain exceptions in the service layer; translate them to HTTP responses via `@ControllerAdvice` in the web layer.
8. **Documentation & Logging:** Public classes/interfaces/methods need English JavaDoc explaining why/what (not redundant getters). Prefer `@Slf4j` with structured logging (include identifiers/keys). Flag any logging of secrets/PII (tokens, passwords, keys). Integration tests must have Javadoc describing what/why, boundary conditions, and expected result.
9. **Framework Usage:** Correct Spring Boot usage (annotations, transaction boundaries, validation).
10. **Testing Pyramid:** Unit tests must mock external dependencies and cover edge cases; do not push business-logic assertions into integration tests; do not decrease coverage without reason.
11. **Changelog:** Any user-facing or otherwise relevant change (feature, bug fix, behavioural/config/API change) must be accompanied by an entry in `CHANGELOG.md` under the current/next unreleased section, grouped under Added/Fixed/Changed/Removed, and referencing the ticket number in parentheses (e.g. `(#949)`). Flag relevant code changes that do NOT include a corresponding `CHANGELOG.md` update or that omit the ticket reference. Pure refactorings, docs-only or merge commits are exempt.
12. **Configuration Documentation:** Whenever application properties/configuration or environment variables are added, renamed, or changed (e.g. in `application.yml`, `application-*.yml`, `@ConfigurationProperties` classes, or env-var mappings), `README.md` must be updated to document the new/changed property. Flag any such configuration change that is not reflected in `README.md`.
13. **Database Migrations:** New Flyway migration scripts (e.g. under `db/migration`) must be backwards compatible following the EMC (Expand-Migrate-Contract) pattern, so the previous application version keeps working against the new schema during a rolling deployment. Flag destructive or breaking changes in the same migration as the expand step (e.g. dropping/renaming columns or tables still used by the current version, adding NOT NULL columns without a default or backfill, or narrowing types). Also flag edits to already-released migration scripts (migrations must be immutable once released).
14. **Spelling & Language (scoped, not nitpicking):** Only flag typos with real impact - in public API names, configuration/property keys, environment variable names, and in log or exception messages. All code comments and JavaDoc must be written in English; flag any non-English comment/JavaDoc. Do NOT report minor typos in local variables or general prose.

## 4. Report Findings
For each finding, report: file name, affected line number(s)/context, category, severity (HIGH = must fix / MEDIUM = should fix / LOW = optional), a short description of the problem, the offending code snippet (only if it is ≤ 5 lines), and a concrete suggestion. Group the final report by 'Critical' (HIGH), 'Optional' (MEDIUM/LOW), and 'Praise' (positive findings that perfectly follow our guidelines). End with a brief overall summary.

## 5. Persist the Result
Write the final Markdown report to `ai-review-report.md` in the repository root (overwrite if it already exists) in addition to showing it in the chat, so it can be committed, attached to a PR, or diffed against previous runs.

## 6. Manual Fallback (no agent tool access)
If the review must be requested from a different AI chat without file/terminal access, the user can still generate the diff manually and paste it:

```bash
git add .
git commit -m "feat: ready for review"
git diff -U10 main...HEAD > review.txt
```

Then upload/paste `review.txt` and reuse the prompt rules from section 3 above.


