/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.dto;

/**
 * Enumeration of supported SWIYU API versions for the SWIYU-API-Version header.
 */
public enum VPApiVersion {
    /**
     * DIF Presentation Exchange for old Presentation Exchange requests standard
     */
    ID2("1", "Implementers Draft 2 (OID4VP Draft 18) with DIF Presentation Exchange"),
    
    /**
     * OpenID4VP Rejection for rejections
     */
    V1("2", "Version 1.0 of OID4VP with DCQL for requesting credentials");

    private final String value;
    private final String description;

    VPApiVersion(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parse string value to enum
     * @param value the string value
     * @return the corresponding enum value
     * @throws IllegalArgumentException if the value is not supported
     */
    public static VPApiVersion fromValue(String value) {
        if (value == null) {
            return ID2; // Default behaviour
        }
        
        for (VPApiVersion version : values()) {
            if (version.value.equals(value)) {
                return version;
            }
        }
        
        throw new IllegalArgumentException("Not allowed SWIYU-API-Version: " + value);
    }

    @Override
    public String toString() {
        return value;
    }
}
