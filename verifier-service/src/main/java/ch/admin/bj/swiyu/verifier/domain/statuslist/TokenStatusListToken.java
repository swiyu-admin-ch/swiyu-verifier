package ch.admin.bj.swiyu.verifier.domain.statuslist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.InflaterInputStream;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * See <a href="https://www.ietf.org/archive/id/draft-ietf-oauth-status-list-02.html#name-status-list-token-in-jwt-fo">spec</a>
 * Status List published on registry
 */
@Slf4j
@Getter
public class TokenStatusListToken {

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

    public static TokenStatusListToken loadTokenStatusListToken(int bits, String lst, int maxBufferSize) throws IOException {
        return new TokenStatusListToken(bits, decodeStatusList(lst, maxBufferSize));
    }

    /**
     * Decodes and decompresses a Base64-encoded and compressed status list.
     *
     * <p>This method performs the following steps:
     * <ul>
     *     <li>Decodes the input string using Base64 decoding.</li>
     *     <li>Decompresses the deflated data using a {@link InflaterInputStream}.</li>
     *     <li>Ensures that the decompressed data does not exceed a predefined safe limit to prevent potential compression bomb attacks.</li>
     * </ul>
     *
     * @param lst           The Base64-encoded and deflate-compressed input string.
     * @param maxBufferSize The allowed size limit at which decoding stops with an IOException
     * @return A byte array containing the decompressed data.
     * @throws IOException If an error occurs during decoding, decompression, or if the decompressed data exceeds the allowed limit.
     */
    public static byte[] decodeStatusList(String lst, int maxBufferSize) throws IOException {
        byte[] zippedData = Base64.getUrlDecoder().decode(lst);

        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(zippedData);
             InflaterInputStream inflaterStream = new InflaterInputStream(byteArrayInputStream);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int bytesRead;
            int totalSize = 0; // Track total decompressed data size

            // Check if the decompressed data size exceeds the allowed limit
            while ((bytesRead = inflaterStream.read(buffer)) != -1) {
                totalSize += bytesRead;
                if (totalSize > maxBufferSize) {
                    throw new IOException("Decompressed data exceeds safe limit! Possible compression bomb attack.");
                }
                output.write(buffer, 0, bytesRead);
            }
            // Return the fully decompressed byte array
            return output.toByteArray();
        }
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
        var position = idx * bits / 8;

        if (position >= statusList.length) {
            throw new IndexOutOfBoundsException("Index out of bounds");
        }

        return statusList[position];
    }

}
