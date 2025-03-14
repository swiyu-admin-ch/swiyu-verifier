/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.common.exception;

public enum VerificationError {
    /**
     * RFC 6749 subset of error codes this verifier agent supports from base on the <a href="https://www.rfc-editor.org/rfc/rfc6749.html#section-4.2.2.1">
     * RFC specification</a>.
     * The other error types as indicated in <a href="https://openid.net/specs/openid-4-verifiable-presentations-1_0-ID2.html#section-6.4">OpenID4VP</a>
     * are not listed because they are only relevant for the holder during the presentation submission / response
     */
    // RFC Codes
    INVALID_REQUEST,
    SERVER_ERROR,

    // Codes according to custom profile
    INVALID_CREDENTIAL;
}
