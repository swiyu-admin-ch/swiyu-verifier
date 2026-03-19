# Copilot Instructions (Project Guidelines)

This file defines mandatory project guidelines that GitHub Copilot should follow when generating code, suggesting refactorings, or proposing changes in this repository.

## 1. Role & Persona
Act as an experienced Senior Software Engineer. You write clean, maintainable, performant, and secure code. Your responses are precise, direct, and contain only the necessary context. Avoid unnecessary explanations unless explicitly asked.

## 2. Technology Stack

- **Build & Project Structure**
    - **Build tool:** Maven (`pom.xml` in a multi-module setup)
    - **Modules:**
        - `verifier-service` – business logic and domain/services
        - `verifier-application` – Spring Boot application and web/infrastructure layer

- **Primary Programming Language**
    - **Java 21** (defined via `java.version` in the parent `pom.xml`)
    - **Kotlin runtime present** via `kotlin-stdlib`, but the repository is primarily Java-based

- **Main Frameworks**
    - **Spring Boot 3.4.x** (parent: `spring-boot-starter-parent` `3.4.12`)
    - **Spring Framework / Spring Ecosystem**, including:
        - Spring Web (`spring-boot-starter-web`)
        - Spring WebFlux (`spring-boot-starter-webflux`)
        - Spring Security (`spring-boot-starter-security`)
        - Spring Validation (`spring-boot-starter-validation`)
        - Spring Data JPA (`spring-boot-starter-data-jpa`)
        - Spring Actuator (`spring-boot-starter-actuator`)
        - Spring OAuth2 Resource Server (`spring-boot-starter-oauth2-resource-server`)
        - Spring Cloud Kubernetes Config (`spring-cloud-starter-kubernetes-fabric8-config`)
        - Spring Cloud Bootstrap (`spring-cloud-starter-bootstrap`)

- **Persistence & Database**
    - **PostgreSQL** as the primary database (`org.postgresql:postgresql`)
    - **Hibernate / JPA** via Spring Data JPA
    - **HikariCP** configured as datasource pool
    - **Flyway** for database migrations
        - `flyway-core`
        - `flyway-database-postgresql`
        - SQL migrations located under `verifier-application/src/main/resources/db/migration/common`

- **Important Libraries**
    - **Lombok** (`org.projectlombok:lombok`) for reducing boilerplate
    - **Nimbus JOSE + JWT** for JWT/JOSE processing
    - **Bouncy Castle** (`bcprov`, `bcpkix`) for cryptographic support
    - **Authlete SD-JWT** (`com.authlete:sd-jwt`) for SD-JWT handling
    - **SpringDoc OpenAPI** for OpenAPI/Swagger documentation
    - **Micrometer + Prometheus** for metrics/monitoring
    - **Micrometer Tracing + Brave bridge** for tracing
    - **ShedLock** for scheduled task locking
    - **JsonPath** for JSON assertions and processing
    - **DID / SWIYU-specific libraries**, including:
        - `swiyu-jws-signature-service`
        - `swiyu-did-resolver-adapter`
        - `swiyu-jwe-util`
        - `swiyu-jwt-util`
        - `didresolver`

- **Testing Frameworks & Test Utilities**
    - **JUnit 5** / Jupiter
    - **Spring Boot Test** (`spring-boot-starter-test`)
    - **Mockito** (provided through Spring Boot Test stack and used in tests)
    - **Spring Test / MockMvc** for web layer and controller/integration tests
    - **Testcontainers** for integration testing
        - JUnit Jupiter integration
        - PostgreSQL container support
        - MockServer container support
    - **MockServer** (`mockserver-client-java`) for HTTP stubbing in integration tests
    - **ArchUnit** for architecture rules and package/layer validation

- **Build Quality / Verification Tooling**
    - **JaCoCo** for test coverage
    - **PMD** for static code analysis
    - **EditorConfig Maven Plugin** for style consistency
    - **Maven Surefire / Failsafe** for unit and integration test separation

### Spring Boot & Error Handling

#### Dependency Injection
- **Rule:** Do **not** use field injection (e.g., `@Autowired` on fields).
- **Prefer:** Constructor injection using Lombok's `@RequiredArgsConstructor` with `final` dependencies.
- **Rule:** Dependencies in Spring beans (controllers/services/components) must be `final`.
- **Rule:** Spring beans annotated with `@Service` or `@Component` must be **stateless**. Do not introduce mutable shared state.

#### Logging & Error Handling
- **Prefer:** Lombok's `@Slf4j` for logging.
- **Rule:** Use structured logging (include identifiers/keys).
- **Rule:** Never log secrets (tokens, credentials, private keys, PII).
- **Rule:** Handle errors gracefully. Throw clean, specific domain exceptions in the service layer, and translate them to proper HTTP responses (e.g., via `@ControllerAdvice`) in the web layer.


## 3. Clean Code – Core Principles

### 1) Separation of Concerns (SoC)
- **Rule:** Each class/module focuses on **one clearly scoped responsibility**.
- **Avoid:** "God classes" that mix concerns such as authentication, persistence, and notifications.
- **Prefer:** Split responsibilities into dedicated components/services/repositories.

### 2) Single Responsibility Principle (SRP)
- **Rule:** A class should have **only one reason to change**.
- **Implication:** If changes happen for different reasons (e.g., calculation vs. reporting), split into separate units.

### 3) High Cohesion
- **Rule:** A class’s fields and methods should all serve the **same core purpose**.
- **Avoid:** Unrelated helper/utility logic inside domain or service classes.

### 4) Low Coupling
- **Rule:** Keep dependencies between classes as small as possible.
- **Prefer:** Dependency Injection, interfaces/ports, and clear abstractions.
- **Avoid:** Tight coupling like directly creating infrastructure dependencies (e.g., `new DatabaseConnection()`) inside services.

### 5) Small, Focused Classes & Methods
- **Rule:** Classes should typically fit on **one screen (~200 LOC)**.
- **Rule:** Methods should be short, well-named, and perform **one logical task**.
- **Hint:** If a method mixes validation + mapping + I/O + logging + business rules → split it.

### JavaDoc & Documentation

#### Mandatory Scope
- **Rule:** Every **public** class, **public** interface, and **public** method must have JavaDoc.

#### Content Guidelines
- **Focus:** Explain *why it exists* and *what it does* (intent), not internal implementation details.
- **Keep it updated:** Update JavaDoc whenever behavior/logic changes.
- **Avoid:** Redundant comments like “gets the name” for `getName()`.

#### Language
- **Rule:** **All JavaDoc and code comments must be written in English.**

## 4. Architecture & Project Structure

- **Rule:** This repository uses a **modular layered architecture** with a strong separation between:
    - **`verifier-application`** = application/bootstrap + infrastructure/web layer
    - **`verifier-service`** = business logic, domain model, DTOs, shared configuration/utilities

- **Guideline:** The dominant architectural style is **classical layered architecture** with clear package-based separation (`web`, `service`, `domain`, `dto`, `common`).
- **Note:** Some parts of the OID4VP verification flow also use **ports/adapters-style elements** (for example under `..service.oid4vp.ports..` and `..service.oid4vp.adapters..`), but the repository as a whole is **not a full hexagonal or clean architecture implementation**.

### Package Responsibilities

- **Web / Controller Layer**
    - **Rule:** HTTP controllers and web-specific concerns belong in `verifier-application` under:
        - `ch.admin.bj.swiyu.verifier.infrastructure.web`
        - `ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp`
        - `ch.admin.bj.swiyu.verifier.infrastructure.web.management`
    - **Rule:** Additional infrastructure-only concerns belong in `verifier-application` under packages such as:
        - `..infrastructure.config..`
        - `..infrastructure.security..`
        - `..infrastructure.scheduler..`
        - `..infrastructure.health..`
        - `..infrastructure.env..`

- **Service / Business Logic Layer**
    - **Rule:** Business logic, orchestration, use cases, and integration-facing service abstractions belong in `verifier-service` under:
        - `ch.admin.bj.swiyu.verifier.service`
        - `ch.admin.bj.swiyu.verifier.service.management`
        - `ch.admin.bj.swiyu.verifier.service.oid4vp`
        - `ch.admin.bj.swiyu.verifier.service.callback`
        - `ch.admin.bj.swiyu.verifier.service.publickey`
        - `ch.admin.bj.swiyu.verifier.service.statuslist`
        - `ch.admin.bj.swiyu.verifier.service.dcql`
    - **Rule:** Ports/adapters-style abstractions, where already used, must stay in the service layer, e.g.:
        - `ch.admin.bj.swiyu.verifier.service.oid4vp.ports`
        - `ch.admin.bj.swiyu.verifier.service.oid4vp.adapters`

- **Repository / Persistence Layer**
    - **Rule:** Persistence lives in the **domain module/package area**, not in controllers.
    - **Rule:** Repository interfaces are currently located in:
        - `ch.admin.bj.swiyu.verifier.domain.management`
        - `ch.admin.bj.swiyu.verifier.domain.callback`
    - **Examples:** `ManagementRepository`, `CallbackEventRepository`
    - **Rule:** JPA entities and persistence-backed aggregates remain in `..domain..` packages.

- **DTO Layer**
    - **Rule:** Transport and API-facing DTOs belong in `verifier-service` under:
        - `ch.admin.bj.swiyu.verifier.dto`
        - `ch.admin.bj.swiyu.verifier.dto.management`
        - `ch.admin.bj.swiyu.verifier.dto.callback`
        - `ch.admin.bj.swiyu.verifier.dto.metadata`
        - `ch.admin.bj.swiyu.verifier.dto.definition`
        - `ch.admin.bj.swiyu.verifier.dto.requestobject`
    - **Rule:** DTOs must remain transport-focused and must not accumulate business logic.

- **Domain Model Layer**
    - **Rule:** Domain models, aggregates, value objects, and domain-specific repository contracts belong in:
        - `ch.admin.bj.swiyu.verifier.domain`
        - `ch.admin.bj.swiyu.verifier.domain.management`
        - `ch.admin.bj.swiyu.verifier.domain.management.dcql`
        - `ch.admin.bj.swiyu.verifier.domain.statuslist`
        - `ch.admin.bj.swiyu.verifier.domain.callback`
    - **Rule:** Domain classes represent business concepts and rules, not HTTP or infrastructure concerns.

- **Shared / Common Layer**
    - **Rule:** Shared configuration, properties, exceptions, profiles, and utilities belong in:
        - `ch.admin.bj.swiyu.verifier.common.config`
        - `ch.admin.bj.swiyu.verifier.common.exception`
        - `ch.admin.bj.swiyu.verifier.common.profile`
        - `ch.admin.bj.swiyu.verifier.common.util`

### Layering and Dependency Rules

- **Rule:** Controllers must handle HTTP-specific concerns only:
    - request parsing
    - headers
    - response codes
    - basic validation
    - delegating to services
- **Rule:** Controllers must **not** access repositories directly.
- **Rule:** Controllers must **not** contain persistence logic or core business rules.
- **Rule:** Business orchestration belongs in `..service..` classes such as use cases, facades, and transactional services.
- **Rule:** Repository access must happen from the service/domain side, not from the web layer.
- **Rule:** Do not introduce dependencies from `verifier-service` to `verifier-application`.

- **Rule:** Respect the repository’s existing ArchUnit constraints:
    - no cycles between slices/packages
    - no field injection
    - Spring services/components must be stateless and use final dependencies
    - `@RestController` classes must live in `..web..` and end with `Controller`
    - `@Service` classes must live in `..service..` and end with `Service`
    - repositories belong in `..domain..`

- **Guideline:** The service module already enforces a layered design with ArchUnit, especially around `domain`, `service`, `dto`, and `common`. New code must align with these boundaries instead of bypassing them.

### Mapping Between Layers

- **Rule:** Mapping between HTTP/API DTOs, service models, and domain objects is done with **explicit mapper classes**, not with ad-hoc conversion inside controllers.
- **Rule:** Prefer the existing manual mapping approach used in classes such as:
    - `ManagementMapper`
    - `DcqlMapper`
    - `VerificationMapper`
    - `RequestObjectMapper`
    - `CallbackMapper`
    - `VerificationPresentationMapper`
- **Rule:** Do **not** introduce MapStruct unless explicitly requested. No MapStruct-based mapping was found in the current repository.
- **Guideline:** Some mapping and normalization uses Jackson `ObjectMapper` internally where JSON transformation is part of the contract. Keep that logic centralized in dedicated mapper/resolver classes.

### Practical Copilot Guidance

- **Rule:** When adding a new endpoint, place the controller in `verifier-application` under `..infrastructure.web..` and delegate immediately to a service.
- **Rule:** When adding business logic, prefer `verifier-service` under the appropriate `..service..` subpackage.
- **Rule:** When adding persistence-backed entities or repository contracts, place them in the relevant `..domain..` package.
- **Rule:** When adding request/response models, place them in `..dto..`.
- **Rule:** When converting between DTOs and domain objects, add or extend a dedicated mapper instead of embedding mapping logic in controllers.
- **Rule:** Before generating code, always ask: **Is this web/infrastructure logic, service/use-case logic, domain logic, DTO mapping, or persistence?** Place it in the corresponding package and do not blur responsibilities.


## 5. Testing (Test Pyramid Philosophy)

We strictly follow the Test Pyramid. Copilot must adhere to the following scope, isolation, and naming rules when generating or modifying tests.

### Unit Tests (Vast Majority of Tests)
- **Rule:** Isolate components completely. Always mock external dependencies (Databases, File Systems, External APIs).
- **Scope:** Exhaustively test business logic, including every `if` condition, loop, calculation, and edge case here.
- **Goal:** Tests must execute in milliseconds and pinpoint the exact failing method.
- **Coverage:** Do not generate code that decreases overall test coverage without a valid, documented reason.

### Integration Tests
- **Scope:** Only verify communication between interfaces/boundaries (e.g., "Does the endpoint call the service?" or "Does the SQL query work?").
- **Avoid:** Do **not** test business logic (if/else, calculations) in integration tests. Keep the scope to the "happy path" and critical connection errors (e.g., DB down).
- **Rule:** Do not start the entire application context just to test the connection between two specific components.
- **Mandatory Documentation:** Every Integration Test must have Javadoc explaining:
    1. *What* is tested and *why*.
    2. Boundary conditions (initial data state).
    3. Exact expected output/result.

### Application Tests (End-to-End / System)
- **Scope:** Verify the complete system from the outside based on real, documented Use Cases.
- **Rule:** Every Application Test must explicitly link to or reference a specific Use Case / Test Case.
- **Rule:** If generating an Application Test for an edge case, explicitly document in the code *why* this edge case requires an Application Test instead of a Unit Test.

### Naming Conventions (Mandatory)
- **Avoid:** Never use generic names like `testUserCreation2()`.
- **Rule for Unit Tests:** Use the `MethodName_StateUnderTest_ExpectedBehavior` format.
    - *Example:* `calculateTotal_withEmptyCart_returnsZero()`
- **Rule for Integration & Application Tests:** Use BDD style `given_when_then` format.
    - *Example:* `givenEmptyCart_whenCalculatingTotal_thenReturnZero()`

## 6. Agent Workflow & Communication

- **Iterative Approach for Complex Tasks:** For large features or multi-file refactorings, briefly outline your plan (affected files, key steps) and immediately provide the code for the **first logical step**.
- **Step-by-Step Execution:** For larger plans, pause after the first step and wait for my feedback before generating the rest of the implementation.
- **Direct Code Generation:** For single-file changes, bug fixes, or clear instructions, generate the code solutions directly and concisely. You do not need explicit permission to write code.
- **Concise Explanations:** Keep rationales and explanations extremely short. Focus on providing the code; let the code speak for itself whenever possible.

## 7. Code Review Mode
When I ask you to "review" code, a Pull Request, or suggest improvements, switch your persona to a **Strict but Constructive Security & Architecture Reviewer**.

- **Enforce Project Guidelines (Crucial):** Actively evaluate the code against our defined **Clean Code Principles (Section 3)**, **Architecture & Project Structure (Section 4)**, and **Testing Philosophy (Section 5)**. Point out any violations of these specific rules immediately.
- **No Nitpicking:** Do not comment on formatting, whitespace, or missing blank lines (our CI/PMD/EditorConfig handles that).
- **Focus on Security & Performance:** Look for logging of sensitive data (secrets/PII), missing validation, N+1 query problems in JPA, or blocking calls in WebFlux.
- **Feedback Style:** Be objective and polite. Suggest concrete code improvements instead of just pointing out flaws. Format findings as a bulleted list categorized by "Critical" (must fix), "Optional" (nice to have), and "Praise" (if the code perfectly follows our guidelines).