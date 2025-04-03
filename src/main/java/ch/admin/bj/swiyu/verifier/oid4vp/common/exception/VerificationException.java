/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.common.exception;

import static ch.admin.bj.swiyu.verifier.oid4vp.common.exception.VerificationError.INVALID_CREDENTIAL;

import lombok.Getter;

@Getter
public class VerificationException extends RuntimeException {
    private final VerificationError errorType;
    private final VerificationErrorResponseCode errorResponseCode;
    private final String errorDescription;

    private VerificationException(Throwable cause, VerificationError errorType, VerificationErrorResponseCode errorResponseCode, String errorDescription) {
        super(cause);
        this.errorType = errorType;
        this.errorResponseCode = errorResponseCode;
        this.errorDescription = errorDescription;
    }

    public static VerificationException submissionError(VerificationErrorResponseCode error, String errorDescription) {
        return new VerificationException(
                null /* submissionError is only caused by business cases and not exceptions */,
                VerificationError.INVALID_REQUEST,
                error,
                errorDescription
        );
    }


    public static VerificationException submissionError(VerificationError error, String errorDescription) {
        return new VerificationException(
                null /* submissionError is only caused by business cases and not exceptions */,
                error,
                null,
                errorDescription
        );
    }

    public static VerificationException credentialError(VerificationErrorResponseCode errorCode,
                                                        String errorDescription) {
        return new VerificationException(
                null,
                INVALID_CREDENTIAL,
                errorCode,
                errorDescription
        );
    }

    public static VerificationException credentialError(Throwable cause,
                                                        VerificationErrorResponseCode errorCode,
                                                        String errorDescription) {
        return new VerificationException(
                cause,
                INVALID_CREDENTIAL,
                errorCode,
                errorDescription
        );
    }

    public static VerificationException credentialError(Throwable cause,
                                                        String errorDescription) {
        return new VerificationException(
                cause,
                INVALID_CREDENTIAL,
                null,
                errorDescription
        );
    }
}
