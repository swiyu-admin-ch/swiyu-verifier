package ch.admin.bit.eid.oid4vp.exception;

import ch.admin.bit.eid.oid4vp.model.enums.ResponseErrorCodeEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationErrorEnum;
import ch.admin.bit.eid.oid4vp.model.enums.VerificationStatusEnum;
import ch.admin.bit.eid.oid4vp.model.persistence.ManagementEntity;
import ch.admin.bit.eid.oid4vp.model.persistence.ResponseData;
import ch.admin.bit.eid.oid4vp.service.VerificationService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import static java.util.Objects.nonNull;


@Order(Ordered.HIGHEST_PRECEDENCE)
@ControllerAdvice
@AllArgsConstructor
@Slf4j
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    private final VerificationService verificationService;

    @ExceptionHandler(VerificationException.class)
    protected ResponseEntity<Object> handleVerificationException(
            final VerificationException exception, final WebRequest request) {
        HttpStatus responseStatus = HttpStatus.BAD_REQUEST;
        if (exception.getError().getError().equals(VerificationErrorEnum.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND)) {
            responseStatus = HttpStatus.NOT_FOUND;
        }

        ManagementEntity managementEntity = exception.getManagementEntity();

        if (managementEntity != null) {
            managementEntity.setState(VerificationStatusEnum.FAILED);
            managementEntity.setWalletResponse(ResponseData.builder().errorCode(ResponseErrorCodeEnum.CREDENTIAL_INVALID).build());
            verificationService.updateManagement(exception.getManagementEntity());
        }

        log.error(exception.getMessage());

        return new ResponseEntity<>(exception.getError(), responseStatus);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<Object> handleIllegalArgumentException(
            final IllegalArgumentException exception, final WebRequest request) {
        HttpStatus responseStatus = HttpStatus.BAD_REQUEST;
        String responseMessage = nonNull(exception.getMessage()) ? exception.getMessage() : "Bad request";


        log.error(exception.getMessage());
        return new ResponseEntity<>(responseMessage, responseStatus);
    }
}
