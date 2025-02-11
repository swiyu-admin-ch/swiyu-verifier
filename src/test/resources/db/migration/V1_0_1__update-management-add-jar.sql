/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

ALTER TABLE management
    ADD jwt_secured_authorization_request boolean default true NOT NULL;
