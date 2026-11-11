---
name: make-release
description: Guides the release process for swiyu-verifier. Generates a Keep-a-Changelog changelog from git commits, bumps the Maven versions, commits/pushes, and outputs the GitHub release and Confluence/Jira checklists. Use when the user says "Start a Release" or asks to create a new release or changelog.
disable-model-invocation: true
allowed-tools: Bash(git log:*), Bash(git status:*), Bash(git branch:*), Bash(git diff:*), Read, Grep, Glob
---

# Make Release

You act as an experienced release manager. Follow the workflow below strictly, do not skip any steps, and only
generate the changelog once the commit logs are available. Communicate with the user in English.

## Step 1: Ask for the new version

Ask: "Which version do you want to create for this release? (e.g. 1.2.0)". Wait for the answer.

## Step 2: Ask for the date of the last release

Ask: "What was the date of the last release? (Format: YYYY-MM-DD)". Wait for the answer.

## Step 3: Determine the commits

Run yourself (read-only):

```bash
git log --since="[DATE]" --oneline
```

Briefly show the commits to the user. If the output obviously looks incomplete, offer that the user pastes them
themselves.

## Step 4: Generate the changelog

Strict rules:

- Group into "Added", "Fixed" and "Changed".
- Include relevant Jira ticket numbers in parentheses -> (#XXX).
- Short, clear bullet points.
- Markdown in "Keep a Changelog" style.
- Only user-facing changes; omit refactoring, documentation and merge commits unless they affect functionality.

## Step 5: Update pom.xml

Directly after the changelog: show the XML snippets for the `<version>` tags of `swiyu-verifier-parent` and
`swiyu-verifier-service` with the new version. Additionally suggest as an alternative:

```bash
mvn versions:set -DnewVersion=[VERSION]
```

Only modify the pom.xml files after the user has confirmed.

## Step 6: Update SECURITY.md and EXPERIMENTAL.md

This step comes before the commit so that the changes are included in the release commit:

- **SECURITY.md:** Adjust the "Supported Versions" table (e.g. "Latest tagged release 5.0.x") and the branch list under
  "Bug Bounty" (e.g. `release/5.0.x`) to the release branch of the new version (Major.Minor from Step 1, e.g. 5.1.x).
  Ask the user whether the previous version remains supported before removing it.
- **EXPERIMENTAL.md:** Based on the commits from Step 3, check whether features became newly experimental, were released
  for production, or were removed, and update the list accordingly. Show the proposed changes to the user.

Show both diffs and wait for the user's confirmation. The commit command in Step 8 must include these files
(`git commit -am` already picks up all tracked changes).

## Step 7: Check the migration guide

Based on the changelog (Step 4) and the commits (Step 3), check whether a migration guide from the previous minor
version (e.g. 5.0.x) to the new one (e.g. 5.1.x) is needed. This check is mandatory for every release and must be
explicitly justified to the user, even if the result is "not needed".

A guide is needed for changes that affect existing setups:

- Breaking changes in the API, issuer metadata or data model (e.g. new mandatory fields, removed values).
- New, changed or removed environment variables and changed default values.
- Changed behavior that clients, wallets or business issuers must react to (e.g. new HTTP status codes).
- Database migrations or configuration changes that require action by operators.

Not needed for purely additive, optional changes without required action, bug fixes, documentation and
dependency updates (patch releases usually not either).

Verify claims in the code (e.g. validation annotations, default values in `application.yml`) before writing them into
the guide, and check whether the changes in the changelog are still up to date.

Procedure:

1. Look at the existing guides in `migration-guides/` (e.g. `guide-4.x.x-to-5.0.x.md`) and adopt their structure and
   style: Compatibility summary (Issuance clients / Business verifiers / Operators), followed by breaking changes with
   before/after examples.
2. File name: `migration-guides/guide-[OLD_VERSION].x-to-[NEW_VERSION].x.md` (e.g. `guide-5.0.x-to-5.1.x.md`).
3. Tell the user the result of the check (needed / not needed, with justification) and show the draft if "needed".
   Only create the file after the user has confirmed.
4. The new file is untracked: `git commit -am` does not pick it up. It must additionally be added with `git add` in
   Step 8.

## Step 8: Commit and push

Show the commands (substituting the version) and run them ONLY after the user's explicit confirmation:

```bash
git add migration-guides/guide-[OLD_VERSION].x-to-[NEW_VERSION].x.md   # only if a guide was created in Step 7
git commit -am "chore(release): bump version to [VERSION]"
git push
```

Do not add any other untracked files (e.g. `.claude/`, `review.txt`).

## Step 9: GitHub release

Derive the release branch from Major.Minor (e.g. 2.4.3 -> `release/2.4.x`) and output exactly this guide:

**Step 9: Create the GitHub release**
Please perform the following steps on GitHub:

1. Open the releases page: [https://github.com/swiyu-admin-ch/swiyu-verifier/releases](https://github.com/swiyu-admin-ch/swiyu-verifier/releases)
2. Click the **"Draft a new release"** button.
3. **Choose a tag:** Type `[VERSION]` and select "Create new tag".
4. **Target:** Select the corresponding release branch (e.g. `[COMPUTED_BRANCH]`).
5. **Release title:** Enter `swiyu-verifier [VERSION]`.
6. **Describe this release:** Copy the changelog generated above and paste it here.
7. Click **"Publish release"**.

## Step 10: Confluence documentation, Jira filter & highlights

Extract all unique Jira ticket IDs (e.g. EIDOMNI-123) from the commits of Step 3, remove duplicates and generate a JQL
string: `key IN (TICKET-1, TICKET-2, TICKET-3)`

Output exactly this guide:

**Step 10: Confluence documentation**
Please enter the new release in Confluence now:

1. Go to the page: **7.2. Generic Components Releases - e-ID Teamspace - Confluence**.
2. Create a new entry in the table for version `[VERSION]`.
3. Use the following JQL filter to attach the related Jira tickets:
   `[GENERATED_JQL_FILTER]`

### Swiyu Issuer Releases page

The release must additionally be entered on this page:
[7.2.1 Swiyu Issuer Releases](https://confluence.bit.admin.ch/spaces/EIDTEAM/pages/1555065503/7.2.1+Swiyu+Issuer+Releases)

Generate a short highlights list for copy-paste into Confluence:

- Only bullet points with keywords, one sentence each in English, as short as possible.
- Only the most important user-facing points from the changelog (Step 4), no ticket numbers.
- Output in a Markdown code block so the user can easily copy it.

Example of the style:

```markdown
- Migrated build to Java 25 (LTS) and upgraded to Spring Boot 4.1.1.
- Docker base images updated to Eclipse Temurin 25 JRE
- Added credential_refresh_disabled property to verifier metadata to let business verifiers disable the renewal flow per credential type.
```

Afterwards output this guide:

1. Open the page above.
2. Create a new entry for version `[VERSION]` with the release date.
3. Paste the highlights list.
