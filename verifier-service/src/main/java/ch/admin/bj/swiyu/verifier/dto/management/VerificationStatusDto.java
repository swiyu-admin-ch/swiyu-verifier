/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.dto.management;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "VerificationStatus", enumAsRef = true)
public enum VerificationStatusDto {
    PENDING,
    SUCCESS,
    FAILED
}
