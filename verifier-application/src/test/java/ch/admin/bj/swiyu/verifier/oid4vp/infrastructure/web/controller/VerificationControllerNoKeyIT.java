/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.infrastructure.web.controller;

import ch.admin.bj.swiyu.verifier.api.management.ResponseModeTypeDto;
import ch.admin.bj.swiyu.verifier.api.requestobject.RequestObjectDto;
import ch.admin.bj.swiyu.verifier.common.config.ApplicationProperties;
import ch.admin.bj.swiyu.verifier.infrastructure.web.oid4vp.VerificationController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test-no-key")
class VerificationControllerNoKeyIT extends BaseVerificationControllerTest {

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private VerificationController verificationController;

    @Test
    void shouldGetSignedRequestObject_thenFailDuetoNoKey() {

        // WHEN & THEN
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            verificationController.getRequestObject(REQUEST_ID_SECURED);
        });

        // Verify the exception message
        assertThat(exception.getMessage()).isEqualTo("Failed to sign request object");
    }

    @Test
    void shouldGetRequestObject() {

        // WHEN
        ResponseEntity<Object> requestObject = verificationController.getRequestObject(REQUEST_ID_SDJWT_MGMT_NO_SIGNATURE);

        // THEN
        assertNotNull(requestObject);
        assertThat(requestObject.getStatusCode().value()).isEqualTo(200);
        assertThat(requestObject.getBody()).isNotNull();
        RequestObjectDto requestObjectDto = (RequestObjectDto) requestObject.getBody();
        assertThat(requestObjectDto.getClientId()).contains(applicationProperties.getClientId());
        assertThat(requestObjectDto.getClientIdScheme()).contains(applicationProperties.getClientIdScheme());
        assertThat(requestObjectDto.getResponseType()).contains("vp_token");
        assertThat(requestObjectDto.getResponseMode()).isEqualTo(ResponseModeTypeDto.DIRECT_POST);
        assertNotNull(requestObjectDto.getNonce());
    }
}