/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.common.config;

import com.nimbusds.jose.JWSSigner;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class SignerProvider {
    private final JWSSigner signer;

    public boolean canProvideSigner() {
        return signer != null;
    }

}
