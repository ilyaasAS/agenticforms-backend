package com.agenticform.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agenticform.model.entity.EmailVerificationToken;
import com.agenticform.model.entity.User;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    void deleteByUser(User user);
}
