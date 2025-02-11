/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.publickey;

public class LoadingPublicKeyOfIssuerFailedException extends Exception {
    public LoadingPublicKeyOfIssuerFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
