/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.management;

import ch.admin.bj.swiyu.verifier.oid4vp.common.exception.VerificationErrorResponseCode;
import lombok.Builder;

/**
 * The wallet response data, only persisted during oid4vp, will be used by verifier-agent-management.
 */
@Builder
public record ResponseData(
        VerificationErrorResponseCode errorCode,
        String errorDescription,
        String credentialSubjectData
) {
}
