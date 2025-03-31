/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.exception;

public class StatusListMaxSizeExceededException extends RuntimeException {
    public StatusListMaxSizeExceededException(String message) {
        super(message);
    }
}