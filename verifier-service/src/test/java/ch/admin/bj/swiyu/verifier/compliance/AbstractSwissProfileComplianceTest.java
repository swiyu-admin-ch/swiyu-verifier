package ch.admin.bj.swiyu.verifier.compliance;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.BeforeAll;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractSwissProfileComplianceTest {

    protected static OpenAPI openAPI;

    @BeforeAll
    static void setUp() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);

        Path swaggerFile = Paths.get("openapi.yaml");
        if (!Files.exists(swaggerFile)) {
            swaggerFile = Paths.get("../openapi.yaml");
        }

        String finalPath = swaggerFile.toAbsolutePath().normalize().toString();
        openAPI = new OpenAPIV3Parser().read(finalPath, null, options);

        assertThat(openAPI)
                .as("The OpenAPI specification could not be loaded from path: " + finalPath)
                .isNotNull();
    }

    /**
     * Tries GET, POST, PUT, PATCH in order and returns the schema for the first operation
     * that defines the given status code with a JSON response body.
     */
    protected static Schema<?> getResponseSchema(PathItem pathItem, String statusCode) {
        if (pathItem == null) return null;
        return Stream.of(pathItem.getGet(), pathItem.getPost(), pathItem.getPut(), pathItem.getPatch())
                .filter(Objects::nonNull)
                .map(op -> getResponseSchema(op, statusCode))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static Schema<?> getResponseSchema(Operation operation, String statusCode) {
        if (operation == null || operation.getResponses() == null) return null;
        ApiResponse response = operation.getResponses().get(statusCode);
        if (response == null || response.getContent() == null) return null;
        var mediaType = response.getContent().get("application/json");
        if (mediaType == null) {
            mediaType = response.getContent().values().stream().findFirst().orElse(null);
        }
        if (mediaType == null) return null;
        return mediaType.getSchema();
    }

}
