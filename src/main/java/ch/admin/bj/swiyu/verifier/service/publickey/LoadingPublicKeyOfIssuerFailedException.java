/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.publickey;

public class LoadingPublicKeyOfIssuerFailedException extends Exception {
    public LoadingPublicKeyOfIssuerFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}