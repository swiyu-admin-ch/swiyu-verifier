/*
 * SPDX-FileCopyrightText: 2025 Swiss Confederation
 *
 * SPDX-License-Identifier: MIT
 */

package ch.admin.bj.swiyu.verifier.domain.management;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ManagementRepository extends JpaRepository<Management, UUID> {

    @Modifying
    @Query("DELETE FROM Management m WHERE m.expiresAt < :expiresAt")
    void deleteByExpiresAtIsBefore(@Param("expiresAt") Long expiresAt);
}
