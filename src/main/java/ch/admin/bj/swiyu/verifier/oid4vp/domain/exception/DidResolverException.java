/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.exception;

public class DidResolverException extends RuntimeException {
    public DidResolverException(Throwable cause) {
        super(cause);
    }

    public DidResolverException(String message) {
        super(message);
    }
}
