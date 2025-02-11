/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

-- Table required due to usage of mvn dependency shedlock-spring
CREATE TABLE shedlock(
                         name VARCHAR(64) NOT NULL,
                         lock_until TIMESTAMP NOT NULL,
                         locked_at TIMESTAMP NOT NULL,
                         locked_by VARCHAR(255) NOT NULL,
                         PRIMARY KEY (name)
);
ALTER TABLE management ADD COLUMN expires_at BIGINT;
CREATE INDEX idx_management_expires_at ON management (expires_at);
