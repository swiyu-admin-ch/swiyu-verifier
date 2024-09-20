package ch.admin.bit.eid.oid4vp.model.statuslist;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TokenStatusListBit {
    VALID(0),
    REVOKED(1),
    SUSPENDED(2);

    private final int bitNumber;

    public static TokenStatusListBit createStatus(int statusBitNumber) {
        if (statusBitNumber == VALID.bitNumber) {
            return VALID;
        }
        if (statusBitNumber % 2 == REVOKED.bitNumber) {
            return REVOKED;
        }
        return SUSPENDED;
    }
}
