package com.agenticform.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agenticform.model.entity.IntegrationConnection;

public interface IntegrationConnectionRepository extends JpaRepository<IntegrationConnection, Long> {

    Optional<IntegrationConnection> findByUserIdAndProvider(Long userId, String provider);

    void deleteByUserIdAndProvider(Long userId, String provider);
}
