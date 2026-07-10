package com.fashionrental.config;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByUsernameAndIsActiveTrue(String username);
    boolean existsByUsername(String username);
}
