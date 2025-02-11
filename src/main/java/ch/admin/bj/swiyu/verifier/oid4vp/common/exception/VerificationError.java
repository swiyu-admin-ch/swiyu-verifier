/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * RFC 6749 Error codes as metioned in <a href="https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#section-7.5">
 * OpenID for Verifiable Presentations</a>.
 */
@Schema(name = "VerificationError")
public enum VerificationError {
    AUTHORIZATION_REQUEST_OBJECT_NOT_FOUND,
    AUTHORIZATION_REQUEST_MISSING_ERROR_PARAM,
    VERIFICATION_PROCESS_CLOSED,
    INVALID_PRESENTATION_DEFINITION,
    INVALID_REQUEST
}
