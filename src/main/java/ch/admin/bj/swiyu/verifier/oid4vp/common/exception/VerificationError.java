/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.common.exception;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "VerificationError")
public enum VerificationError {
    /**
     * RFC 6749 subset of error codes this verifier agent supports from base on the <a href="https://www.rfc-editor.org/rfc/rfc6749.html#section-4.2.2.1">
     * RFC specification</a>.
     */
    INVALID_REQUEST,
    SERVER_ERROR,
    INVALID_CREDENTIAL;
}
