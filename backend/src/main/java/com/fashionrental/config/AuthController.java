package com.fashionrental.config;

import com.fashionrental.common.exception.ValidationException;
import com.fashionrental.common.response.ApiResponse;
import com.fashionrental.config.model.CreateUserRequest;
import com.fashionrental.config.model.LoginRequest;
import com.fashionrental.config.model.LoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Authentication — obtain a JWT token")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtConfig jwtConfig;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtConfig jwtConfig,
                          AppUserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtConfig = jwtConfig;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Operation(summary = "Login", description = "Authenticate with username and password. Returns a JWT token valid for 24 hours.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        AppUser user = userRepository.findByUsernameAndIsActiveTrue(request.username())
                .orElseThrow();
        String token = jwtConfig.generateToken(request.username(), user.getRole().name());
        return ResponseEntity.ok(ApiResponse.ok(new LoginResponse(token, user.getRole().name())));
    }

    @Operation(summary = "Create a new user (owner only)")
    @PostMapping("/users")
    public ResponseEntity<ApiResponse<Void>> createUser(@Valid @RequestBody CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ValidationException("Username already exists: " + request.username());
        }
        AppUser user = new AppUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        userRepository.save(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null));
    }
}
