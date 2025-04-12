/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

ALTER TABLE management ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE management ADD COLUMN created_by VARCHAR(255) DEFAULT 'system';
ALTER TABLE management ADD COLUMN last_modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE management ADD COLUMN last_modified_by VARCHAR(255) DEFAULT 'system';