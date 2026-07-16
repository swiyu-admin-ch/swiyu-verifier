# ComplianceTest.instructions.md

This instruction file defines how you (the AI) must generate and update automated Swagger/OpenAPI static compliance tests (linting) for the Swiss Profile ecosystem. Your primary objective is to verify that the generated API contract matches the official strict specification requirements.

## 1. The Golden Rule: Test the Contract, Not the Code
* **Input Source:** Your only input source for what to expect is the requirement document located in the `/spez` directory (e.g., `/spez/CredentialMedataSpez.md`). Never analyze Java controller code or implementation layers to determine expectations.
* **Target Object:** The only target under test is the generated `openapi.yaml` file. The test loads this contract file programmatically at runtime and evaluates it.
* **Goal:** Detect deviations between the target specification and the contract actually exposed to external actors (Contract-First approach).

## 2. Test Package & Class Naming Conventions
* **Package Name:** All generated unit test classes MUST strictly reside in the following package:
  ```java
  package ch.admin.bj.swiyu.verifier.compliance;
  ```
* **Class Naming:** Create or update exactly one test class per endpoint. Follow this naming pattern:
  `SwissProfile<EndpointName>ComplianceTest.java`
  *(e.g., for the OCA metadata endpoint, the class name must be SwissProfileOcaComplianceTest)*

## 3. Robust Multi-Module Path Setup
To guarantee that the test runs seamlessly when executed both from an IDE (where the working directory is the child module) and from the root directory during CI/CD command-line builds (`mvn test`), each class must implement the exact dynamic path resolution strategy shown below.

### Standard Setup Template
Every compliance test class must copy this structural scaffolding:

```java
package ch.admin.bj.swiyu.verifier.compliance;

@DisplayName("Static Compliance Check: Swiss Profile VCT Endpoint")
class SwissProfileXXXComplianceTest extends AbstractSwissProfileComplianceTest {

    private static final String MAPPING_PATH = "/oid4vci";
    private static final String ENDPOINT = MAPPING_PATH + "/vct/{metadataKey}";

    // --- Tier 1: Path Item Verification ---

    @Test
    @DisplayName("Path: Endpoint '/oid4vci/vct/{metadataKey}' must exist in the contract")
    void testVctEndpointExists() {
        assertThat(openAPI.getPaths())
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 4.2. JWT VC Issuer Metadata Response] The paths section must not be empty.")
                .isNotNull();
        assertThat(openAPI.getPaths().get(ENDPOINT))
                .as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 4.2. JWT VC Issuer Metadata Response] The endpoint " + ENDPOINT + " MUST exist in the OpenAPI contract.")
                .isNotNull();
    }
}
```

## 4. Test Step Scaffolding
When writing or expanding specific tests for an endpoint, you must always structuralize assertions into these four sequential tiers:

1. **Path Item Verification:** Check that the precise path key (combining any configuration prefixes) is present in `openAPI.getPaths()`.
2. **HTTP Verb Validation:** Assert that the specific required HTTP operation method (e.g., `getGet()`, `getPost()`) is not null.
3. **Response Status & Media Type Check:** Retrieve the successful response node (e.g., `200`) and verify that its `content` maps containing the target key (e.g., `application/json` or `application/schema+json`) exist precisely.
4. **JSON Schema Assertions:** Extract the root `Schema` payload property map and validate properties:
    * **Required Fields:** Check `schema.getRequired()` to confirm core values must be present.
    * **Property Types:** Assert specific types on properties (e.g., `string`, `array`, `object`).
    * **Unsupported Properties:** Verify that features marked as *NOT SUPPORTED* are absent or explicitly restricted within the property map.
5. **CRITICAL RULE: Traceable Assertions (Error Messages)**
      Whenever you write an assertion, you MUST use AssertJ's `.as("...")` method to provide a detailed, traceable error message. The message MUST strictly follow this format:
      `[Document: <DocName>, Chapter: <ChapterNumber>] <Reason for the rule>`
*Bad Example:* `assertThat(req).as("vct is required").contains("vct");`
*Good Example:* `assertThat(req).as("[Document: draft-ietf-oauth-sd-jwt-vc-15, Chapter: 5.2. Type Metadata Format] The 'vct' property MUST be declared as required.").contains("vct");`

## 5. Coding Language & Conventions
* **Language Consistency:** All method signatures, display metadata annotations, comments, and AssertJ diagnostic descriptions (`.as("...")`) MUST be drafted entirely in **English**.
* **Null Safety:** Always safely unpack objects. Prevent internal test code `NullPointerExceptions` by evaluating parent keys (like sub-properties or lists) before drilling down into nested schemas.

## 6. OpenAPI 3.1 Type Assertions
This project uses **OpenAPI 3.1.0**, which is based on JSON Schema Draft 2020-12. The swagger-parser (2.1.x) stores schema types in a `Set<String>` accessible via `getTypes()` — **not** via `getType()` (which returns `null` for OAS 3.1 schemas). All type assertions MUST use `getTypes()`.

*Wrong (OAS 3.0 style):*
```java
assertThat(schema.getType()).isEqualTo("object");
assertThat(properties.get("vct").getType()).isEqualTo("string");
```

*Correct (OAS 3.1 style):*
```java
assertThat(schema.getTypes())
    .as("[Document: ..., Chapter: ...] The response MUST be formatted as a JSON object.")
    .isNotNull()
    .contains("object");

assertThat(properties.get("vct").getTypes())
    .as("[Document: ..., Chapter: ...] The 'vct' property MUST be defined as a string.")
    .isNotNull()
    .contains("string");
```

## 7. Optional vs. Required Property Assertions
When a specification marks a property as **OPTIONAL** (e.g., "MAY be present", "if present MUST be..."), the test MUST NOT assert that the property exists in the schema. Instead, use a conditional check: only validate the type *if* the property is present, and assert it is *not* in the `required` list.

*Wrong (treats optional as mandatory):*
```java
assertThat(properties).containsKey("c_nonce");
assertThat(properties.get("c_nonce").getTypes()).contains("string");
```

*Correct (conditional check for optional property):*
```java
if (properties != null && properties.containsKey("c_nonce")) {
    assertThat(properties.get("c_nonce").getTypes())
        .as("[Document: ..., Chapter: ...] If 'c_nonce' is defined, it MUST be of type 'string'.")
        .isNotNull()
        .contains("string");
}

List<String> required = schema.getRequired();
if (required != null) {
    assertThat(required)
        .as("[Document: ..., Chapter: ...] The 'c_nonce' property is OPTIONAL and MUST NOT be declared as required.")
        .doesNotContain("c_nonce");
}
```
