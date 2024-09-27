/*
 * SPDX-FileCopyrightText: 2024 Swiss Confederation
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bit.eid.oid4vp.model.statuslist;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.DataFormatException;
import java.util.zip.InflaterOutputStream;

/**
 * See <a href="https://www.ietf.org/archive/id/draft-ietf-oauth-status-list-02.html#name-status-list-token-in-jwt-fo">spec</a>
 * Status List published on registry
 */
@Slf4j
@Getter
public class TokenStatusListToken {

    /**
     * zlib needs some maximum buffer size.
     * Randomly chosen 100 MB
     */
    private static final int BUFFER_SIZE = 100 * 1024 * 1024;
    /**
     * Indicator how many consecutive bits of the token status list are contained within one status list entry.
     * Can be 1, 2, 4 or 8
     * bit 0x1 is always revocation
     * bit 0x2 is always suspension (if available)
     */
    private final int bits;
    /**
     * zlib zipped & url encoded bytes containing the status information
     */
    private final byte[] statusList;


    /**
     * Load existing TokenStatusList Token
     *
     * @param bits       how many bits each status list entry has
     * @param statusList the data of the existing status list entry
     */
    public TokenStatusListToken(int bits, byte[] statusList) {
        this.bits = bits;
        this.statusList = statusList;
    }

    public static TokenStatusListToken loadTokenStatusListToken(int bits, String lst) throws IOException {
        return new TokenStatusListToken(bits, decodeStatusList(lst));
    }


    /**
     * Retrieves the status on the given index. Can contain multiple status being set. Eg 3 = revoked & suspended
     *
     * @param idx index of the status list entry
     * @return the status bits as an integer
     */
    public int getStatus(int idx) {
        byte entryByte = getStatusEntryByte(idx);
        // The starting position of the status in the Byte
        var bitIndex = (idx * bits) % 8;
        // Mask to remove all bits larger than the status
        var mask = (1 << bitIndex << bits) - 1;
        // Drop all bits larger than our status
        var maskedByte = entryByte & mask;
        // Shift the status to the start of the byte so 1 = revoked, 2 = suspended, etc, also removes all bits smaller than our status
        return maskedByte >> bitIndex;
    }


    private byte getStatusEntryByte(int idx) {
        return statusList[idx * bits / 8];
    }

    /**
     * @param lst
     * @return the bytes of the status list
     * @throws DataFormatException if the lst is not a zlib compressed status list
     */
    private static byte[] decodeStatusList(String lst) throws IOException {
        // base64 decoding the data
        byte[] zippedData = Base64.getUrlDecoder().decode(lst);


        var zlibOutput = new ByteArrayOutputStream();
        var inflaterStream = new InflaterOutputStream(zlibOutput);
        inflaterStream.write(zippedData);
        inflaterStream.finish();
        byte[] clippedZlibOutput = Arrays.copyOf(zlibOutput.toByteArray(), zlibOutput.size());
        inflaterStream.close();
        return clippedZlibOutput;

    }

}
