/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.service;

import ch.admin.bj.swiyu.verifier.oid4vp.api.VerificationErrorDto;
import ch.admin.bj.swiyu.verifier.oid4vp.api.VerificationErrorResponseCodeDto;
import ch.admin.bj.swiyu.verifier.oid4vp.api.VerificationErrorResponseDto;
import ch.admin.bj.swiyu.verifier.oid4vp.common.exception.VerificationError;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.VerificationErrorResponseCode;
import ch.admin.bj.swiyu.verifier.oid4vp.domain.exception.VerificationException;
import lombok.experimental.UtilityClass;

@UtilityClass
public class VerificationMapper {

    public static VerificationErrorResponseDto toVerficationErrorResponseDto(VerificationException e) {
        return new VerificationErrorResponseDto(
                toVerificationErrorTypeDto(e.getErrorType()),
                toVerificationErrorResponseCode(e.getErrorResponseCode()),
                e.getErrorDescription());
    }

    private static VerificationErrorResponseCodeDto toVerificationErrorResponseCode(VerificationErrorResponseCode source) {
        if (source == null) {
            return null;
        }
        return switch (source) {
            case CREDENTIAL_INVALID -> VerificationErrorResponseCodeDto.CREDENTIAL_INVALID;
            case JWT_EXPIRED -> VerificationErrorResponseCodeDto.JWT_EXPIRED;
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
        };
    }

    private static VerificationErrorDto toVerificationErrorTypeDto(VerificationError source) {
        if (source == null) {
            return null;
        }
        return switch (source) {
            case AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND -> VerificationErrorDto.AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND;
            case AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM ->
                    VerificationErrorDto.AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM;
            case VERIFICATION_PROCESS_CLOSED -> VerificationErrorDto.VERIFICATION_PROCESS_CLOSED;
            case INVALID_PRESENTATION_DEFINITION -> VerificationErrorDto.INVALID_PRESENTATION_DEFINITION;
            case INVALID_REQUEST -> VerificationErrorDto.INVALID_REQUEST;
        };
    }
}
