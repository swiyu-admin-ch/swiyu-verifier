/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */
package ch.admin.bj.swiyu.verifier.service.oid4vp;

import ch.admin.bj.swiyu.verifier.api.requestobject.RequestObjectDto;

/**
 * Result type for {@link RequestObjectService#assembleRequestObject(java.util.UUID)}.
 * <p>
 * It captures whether the request object was returned as a plain DTO (unsigned)
 * or as a signed JWT string.
 */
public sealed interface RequestObjectResult
        permits RequestObjectResult.Unsigned, RequestObjectResult.Signed {

    /**
     * @return {@code true} if the result is a signed JWT, {@code false} if it is an unsigned DTO.
     */
    boolean isSigned();

    /**
     * Unsigned variant: exposes the plain {@link RequestObjectDto}.
     */
    record Unsigned(RequestObjectDto requestObject) implements RequestObjectResult {
        @Override
        public boolean isSigned() {
            return false;
        }
    }

    /**
     * Signed variant: exposes the compact serialized JWT.
     */
    record Signed(String jwt) implements RequestObjectResult {
        @Override
        public boolean isSigned() {
            return true;
        }
    }
}