/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.management;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum VerificationStatus {
    PENDING,
    SUCCESS,
    FAILED
}
