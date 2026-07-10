package com.fashionrental.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class OwnerSeeder implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.auth.username}")
    private String ownerUsername;

    @Value("${app.auth.password}")
    private String ownerPassword;

    public OwnerSeeder(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByUsername(ownerUsername)) {
            AppUser owner = new AppUser();
            owner.setUsername(ownerUsername);
            owner.setPasswordHash(passwordEncoder.encode(ownerPassword));
            owner.setRole(AppUser.Role.OWNER);
            userRepository.save(owner);
        }
    }
}
