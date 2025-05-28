/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.service.statuslist;

public class StatusListMaxSizeExceededException extends RuntimeException {
    public StatusListMaxSizeExceededException(String message) {
        super(message);
    }
}