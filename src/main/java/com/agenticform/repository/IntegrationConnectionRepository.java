package com.agenticform.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.agenticform.model.entity.IntegrationConnection;

public interface IntegrationConnectionRepository extends JpaRepository<IntegrationConnection, Long> {

    Optional<IntegrationConnection> findByUserIdAndProvider(Long userId, String provider);

    @Query("""
            SELECT ic.user.id FROM IntegrationConnection ic
            WHERE ic.provider = :provider AND ic.user.id IN :userIds
            """)
    List<Long> findConnectedUserIds(
            @Param("userIds") Collection<Long> userIds,
            @Param("provider") String provider);

    void deleteByUserIdAndProvider(Long userId, String provider);

    void deleteByUserId(Long userId);
}
