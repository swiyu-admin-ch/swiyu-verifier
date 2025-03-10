/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class TokenStatusListTokenTest {

    @Test
    void testDecodeStatusList_CompressionBomb_IOExceptionExpected() {
        // Generate a compression bomb
        byte[] compressionBomb = createCompressionBomb(11534336); // 11MB
        // Encode in Base64
        var base64CompressionBomb = Base64.getUrlEncoder().withoutPadding().encodeToString(compressionBomb);
        // Expect an IOException while decompressing
        var exception = assertThrows(IOException.class, () -> {
            TokenStatusListToken.decodeStatusList(base64CompressionBomb);
        });
        assertEquals("Decompressed data exceeds safe limit! Possible compression bomb attack.", exception.getMessage());
    }

    @Test
    void testDecodeStatusList_CompressionBomb_NoExceptionExpected() {
        // Generate a compression bomb
        byte[] compressionBomb = createCompressionBomb(9437184); // 9MB
        // Encode in Base64
        var base64CompressionBomb = Base64.getUrlEncoder().withoutPadding().encodeToString(compressionBomb);
        // Expect no IOException while decompressing, because the safe limit is bigger than the compressed data
        assertDoesNotThrow(() -> {
            TokenStatusListToken.decodeStatusList(base64CompressionBomb);
        });
    }

    /**
     * Creates a highly compressed payload (Compression Bomb) that will exceed the safe limit when decompressed.
     */
    private byte[] createCompressionBomb(int sizeInBytes) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             // Use deflater with max compression level (9)
             DeflaterOutputStream deflaterStream = new DeflaterOutputStream(byteStream, new Deflater(9))) {

            byte[] largeData = new byte[sizeInBytes];
            Arrays.fill(largeData, (byte) 'A');

            deflaterStream.write(largeData);
            deflaterStream.finish();

            return byteStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create compression bomb", e);
        }
    }
}
