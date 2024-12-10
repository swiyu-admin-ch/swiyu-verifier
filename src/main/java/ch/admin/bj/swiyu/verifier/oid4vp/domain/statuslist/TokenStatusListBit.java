package ch.admin.bj.swiyu.verifier.oid4vp.domain.statuslist;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
enum TokenStatusListBit {
    VALID(0),
    REVOKED(1),
    SUSPENDED(2);

    private final int bitNumber;

    public static TokenStatusListBit createStatus(int statusBitNumber) {
        for (TokenStatusListBit status : TokenStatusListBit.class.getEnumConstants()) {
            if (status.getBitNumber() == statusBitNumber) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid status number " + statusBitNumber);
    }
}
