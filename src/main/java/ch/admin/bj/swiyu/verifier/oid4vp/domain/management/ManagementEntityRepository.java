/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.oid4vp.domain.management;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ManagementEntityRepository extends JpaRepository<ManagementEntity, UUID> {
}
