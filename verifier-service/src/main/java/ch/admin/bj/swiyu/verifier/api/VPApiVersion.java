/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.api;

/**
 * Enumeration of supported SWIYU API versions for the SWIYU-API-Version header.
 */
public enum VPApiVersion {
    /**
     * DIF Presentation Exchange for old Presentation Exchange requests standard
     */
    PE("PE", "DIF Presentation Exchange for old Presentation Exchange requests standard"),
    
    /**
     * OpenID4VP Rejection for rejections
     */
    REJECTION("REJECTION", "OpenID4VP Rejection for rejections"),
    
    /**
     * DCQL for DCQL request
     */
    DCQL("DCQL", "DCQL for DCQL request"),
    
    /**
     * Encrypted DCQL request
     */
    DCQLE("DCQLE", "Encrypted DCQL request");

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
            return null;
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
