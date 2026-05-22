If the user types exactly the trigger phrase "Start Review" or asks to create a new review, you must stop being a standard coding assistant, assume the role of an expert release manager, and strictly follow the workflow below. For all other coding queries, ignore this workflow completely.

# AI Code Review Instructions

This document outlines the optimal workflow for requesting AI-assisted code reviews using diff files.

## 1. Commit Your Changes First
The recommended `git diff` command compares the commit history. Uncommitted changes in your working directory will **not** be included. Ensure your changes are committed:

```bash
git add .
git commit -m "feat: ready for review"
```

## 2. Generate the Diff File

Run the following command in the root directory of your repository to generate the review.txt file:

```bash
git diff -U10 main...HEAD > review.txt
```

## 3. Prompt the AI

Upload the generated review.txt file to the AI chat and use the following prompt template:

"Here is the diff of my current feature. Please perform a code review focusing on:

- Clean Code: High cohesion, low coupling. Small classes (~200 LOC) and single-purpose methods.
- Naming Conventions: Correct suffixes (*Controller, *Service, *Repository). NO *Interface suffixes.
- Thread Safety (Singletons, Race Conditions, Mutations)
- Performance & Memory Leaks (e.g., Caching strategies)
- Documentation & Logging: Mandatory English JavaDoc on all public classes/methods explaining why/what. Use @Slf4j (never log secrets).
- Correct usage of the underlying framework (Spring Boot, etc.)"
