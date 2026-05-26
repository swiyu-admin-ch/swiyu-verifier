If the user types exactly the trigger phrase "Update Openapi" or asks to update/regenerate the `openapi.yaml`, you must stop being a standard coding assistant, assume the role of an expert release manager, and strictly follow the workflow below. For all other coding queries, ignore this workflow completely.


# OpenAPI Generation Task

## Goal
Regenerate the `openapi.yaml` file in the project root by starting the Spring Boot application in IntelliJ and fetching the live API docs.

## Prerequisites Check
Before starting, verify:
1. Docker/Podman is running
2. IntelliJ IDEA is open with this project

## Steps

### 1. Start the Spring Boot application in IntelliJ
- Open the `Application.java` class in `verifier-application`
- Run it with the `local` Spring profile active
  - In IntelliJ: Edit Run Configuration → Active profiles: `local`
- Wait until the log shows `Started Application` on port `8080`

### 2. Generate the OpenAPI spec
```bash
cd /home/gapa/development/swiyu-verifier
curl -s http://localhost:8080/v3/api-docs.yaml -o openapi.yaml
```

This fetches the live API docs directly from the running app and writes them to `openapi.yaml` in the project root.

> **Note:** Do NOT use `mvn verify -P generate-doc` when the app is already running in IntelliJ – the Maven plugin will try to start a second instance on port 8080 and fail.

### 3. Stop the Spring Boot application in IntelliJ
- Press the Stop button in IntelliJ after the generation completes.

### 4. Verify the result
```bash
head -20 /home/gapa/development/swiyu-verifier/openapi.yaml
```
The file should start with `openapi: x.x.x` and contain updated paths.


## Expected Output
- File: `/home/gapa/development/swiyu-verifier/openapi.yaml`
- Format: YAML (OpenAPI 3.x)
- Contains all endpoints from `VerificationController`, `ManagementController`, and other `@RestController` classes


### 5. Commit and push the updated openapi.yaml
```bash
cd /home/gapa/development/swiyu-verifier
git add openapi.yaml
git commit -m "docs: regenerate openapi.yaml"
git push
```



