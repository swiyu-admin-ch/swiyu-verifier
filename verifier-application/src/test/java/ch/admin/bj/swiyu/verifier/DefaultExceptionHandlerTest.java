package ch.admin.bj.swiyu.verifier;

import ch.admin.bj.swiyu.verifier.api.ApiErrorDto;
import ch.admin.bj.swiyu.verifier.infrastructure.web.DefaultExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class DefaultExceptionHandlerTest {

    private DefaultExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DefaultExceptionHandler();
    }

    @Test
    void handleMethodArgumentNotValid_shouldReturnBadRequestResponse() {
        var errorMessage = "Bad request.. this is not possible";
        IllegalArgumentException ex = new IllegalArgumentException(errorMessage);

        ResponseEntity<Object> response = handler.handleIllegalArgumentException(ex);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        var apiErrorDto = (ApiErrorDto) response.getBody();

        assertNotNull(response.getBody());
        assertEquals(errorMessage, apiErrorDto.getErrorDescription());
    }

    @Test
    void handleIllegalArgumentExceptionNoMessage_shouldReturnBadRequestResponse() {
        IllegalArgumentException ex = new IllegalArgumentException();

        ResponseEntity<Object> response = handler.handleIllegalArgumentException(ex);

        // Then
        var apiErrorDto = (ApiErrorDto) response.getBody();
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Bad request", apiErrorDto.getErrorDescription());
    }

    @Test
    void handleNoSuchElementException_shouldReturnNotFoundResponse(CapturedOutput output) {
        var errorMessage = "There is nothing with this ID";
        NoSuchElementException ex = new NoSuchElementException("There is nothing with this ID");

        ResponseEntity<Object> response = handler.handleNoSuchElementException(ex);

        // Then
        var apiErrorDto = (ApiErrorDto) response.getBody();
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(errorMessage, apiErrorDto.getErrorDescription());
        assertThat(output.getAll()).doesNotContain("ERROR");
    }

    @Test
    void checkDefaultException_shouldReturnInternalServerError(CapturedOutput output) {
        Exception ex = new Exception("This is a test for exception handling");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/test"));

        ResponseEntity<Object> response = handler.handleException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        ApiErrorDto body = (ApiErrorDto) response.getBody();
        assertNotNull(response.getBody());
        assertEquals("Internal Server Error. Please check again later", body.getErrorDescription());
        assertThat(output.getAll()).contains("ERROR").contains("This is a test for exception handling");
    }

    @Test
    void checkHttpRequestMethodNotSupportedException_shouldReturnInternalServerError(CapturedOutput output) {
        Exception ex = new HttpRequestMethodNotSupportedException("This is a test for exception handling");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/test"));

        ResponseEntity<Object> response = handler.handleException(ex, request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertThat(output.getAll()).doesNotContain("ERROR");
    }
}