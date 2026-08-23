package com.agenticform.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.agenticform.model.entity.Role;
import com.agenticform.model.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    long countByRole(Role role);
}
