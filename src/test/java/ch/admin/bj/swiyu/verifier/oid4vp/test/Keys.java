/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.test;

import com.nimbusds.jose.jwk.*;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.ECPrivateKey;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class Keys {

    @Test
    void mimi1() throws IOException {
        var file = "1.pem";
        var test = getJwk(file);
    }

    @Test
    void mimi2() throws IOException {
        var file = "2.pem";
        var test = getJwk(file);
    }

    @Test
    void mimi3() throws IOException {
        var file = "3.pem";
        var test = getJwk(file);
    }

    @Test
    void mimi4() throws IOException {
        var file = "4.pem";
        var test = getJwk(file);
    }

    private JWK getJwk(String filename) throws IOException {
        PEMParser pemParser = new PEMParser(new InputStreamReader(new FileInputStream("src/test/java/ch/admin/bj/swiyu/verifier/oid4vp/test/%s".formatted(filename))));
        PEMKeyPair pemKeyPair = (PEMKeyPair)pemParser.readObject();

        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        KeyPair keyPair = converter.getKeyPair(pemKeyPair);
        pemParser.close();

        JWK jwk = new ECKey.Builder(Curve.P_256, (ECPublicKey) keyPair.getPublic())
                .privateKey((ECPrivateKey) keyPair.getPrivate())
                .build();

        System.out.println(jwk);

        return jwk;
    }
}
