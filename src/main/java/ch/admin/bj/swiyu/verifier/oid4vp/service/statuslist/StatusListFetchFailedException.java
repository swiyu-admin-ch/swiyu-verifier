/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.service.statuslist;

public class StatusListFetchFailedException extends RuntimeException {
    public StatusListFetchFailedException(String message) {
        super(message);
    }
}