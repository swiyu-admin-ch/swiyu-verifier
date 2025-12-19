/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.dto.VerificationErrorResponseCodeDto;
import ch.admin.bj.swiyu.verifier.dto.VerificationErrorResponseDto;
import ch.admin.bj.swiyu.verifier.dto.VerificationErrorTypeDto;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationError;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class VerificationMapper {

    public static VerificationErrorResponseDto toVerificationErrorResponseDto(VerificationException e) {
        return new VerificationErrorResponseDto(
                toVerificationErrorTypeDto(e.getErrorType()),
                toVerificationErrorResponseCode(e.getErrorResponseCode()),
                toVerificationErrorResponseCode(e.getErrorResponseCode()),
                e.getErrorDescription());
    }

    private static VerificationErrorResponseCodeDto toVerificationErrorResponseCode(
            VerificationErrorResponseCode source) {
        if (source == null) {
            return null;
        }
        return switch (source) {
            case CREDENTIAL_INVALID -> null;
            case JWT_EXPIRED -> VerificationErrorResponseCodeDto.JWT_EXPIRED;
            case JWT_PREMATURE -> VerificationErrorResponseCodeDto.JWT_PREMATURE;
            case INVALID_FORMAT -> VerificationErrorResponseCodeDto.INVALID_FORMAT;
            case CREDENTIAL_EXPIRED -> VerificationErrorResponseCodeDto.CREDENTIAL_EXPIRED;
            case MISSING_NONCE -> VerificationErrorResponseCodeDto.MISSING_NONCE;
            case UNSUPPORTED_FORMAT -> VerificationErrorResponseCodeDto.UNSUPPORTED_FORMAT;
            case CREDENTIAL_REVOKED -> VerificationErrorResponseCodeDto.CREDENTIAL_REVOKED;
            case CREDENTIAL_SUSPENDED -> VerificationErrorResponseCodeDto.CREDENTIAL_SUSPENDED;
            case HOLDER_BINDING_MISMATCH -> VerificationErrorResponseCodeDto.HOLDER_BINDING_MISMATCH;
            case CREDENTIAL_MISSING_DATA -> VerificationErrorResponseCodeDto.CREDENTIAL_MISSING_DATA;
            case UNRESOLVABLE_STATUS_LIST -> VerificationErrorResponseCodeDto.UNRESOLVABLE_STATUS_LIST;
            case PUBLIC_KEY_OF_ISSUER_UNRESOLVABLE ->
                VerificationErrorResponseCodeDto.PUBLIC_KEY_OF_ISSUER_UNRESOLVABLE;
            case CLIENT_REJECTED -> VerificationErrorResponseCodeDto.CLIENT_REJECTED;
            case ISSUER_NOT_ACCEPTED -> VerificationErrorResponseCodeDto.ISSUER_NOT_ACCEPTED;
            case AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND -> null;
            case AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM ->
                VerificationErrorResponseCodeDto.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM;
            case VERIFICATION_PROCESS_CLOSED -> null;
            case INVALID_PRESENTATION_DEFINITION -> VerificationErrorResponseCodeDto.INVALID_PRESENTATION_DEFINITION;
            case MALFORMED_CREDENTIAL -> VerificationErrorResponseCodeDto.MALFORMED_CREDENTIAL;
            case PRESENTATION_SUBMISSION_CONSTRAINT_VIOLATED ->
                VerificationErrorResponseCodeDto.PRESENTATION_SUBMISSION_CONSTRAINT_VIOLATED;
            case INVALID_PRESENTATION_SUBMISSION -> VerificationErrorResponseCodeDto.INVALID_PRESENTATION_SUBMISSION;
            case INVALID_TOKEN_STATUS_LIST -> VerificationErrorResponseCodeDto.INVALID_TOKEN_STATUS_LIST;
            default -> throw new IllegalStateException("Unexpected value: " + source);
        };
    }

    private static VerificationErrorTypeDto toVerificationErrorTypeDto(VerificationError source) {
        if (source == null) {
            return VerificationErrorTypeDto.SERVER_ERROR;
        }
        return switch (source) {
            case INVALID_REQUEST -> VerificationErrorTypeDto.INVALID_REQUEST;
            case SERVER_ERROR -> VerificationErrorTypeDto.SERVER_ERROR;
            case INVALID_CREDENTIAL -> VerificationErrorTypeDto.INVALID_CREDENTIAL;
            case INVALID_CLIENT -> VerificationErrorTypeDto.INVALID_CLIENT;
            case ACCESS_DENIED -> VerificationErrorTypeDto.ACCESS_DENIED;
            case INVALID_TRANSACTION_DATA -> VerificationErrorTypeDto.INVALID_TRANSACTION_DATA;
        };
    }
}