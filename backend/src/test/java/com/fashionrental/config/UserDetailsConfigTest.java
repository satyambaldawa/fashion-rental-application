package com.fashionrental.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsConfigTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private AuthenticationConfiguration authenticationConfiguration;

    private final UserDetailsConfig config = new UserDetailsConfig();

    @Test
    void should_load_user_details_for_active_user() {
        AppUser user = new AppUser();
        user.setUsername("owner1");
        user.setPasswordHash("hashed-password");
        user.setRole(AppUser.Role.OWNER);
        when(userRepository.findByUsernameAndIsActiveTrue("owner1")).thenReturn(Optional.of(user));

        UserDetailsService service = config.userDetailsService(userRepository);
        UserDetails details = service.loadUserByUsername("owner1");

        assertThat(details.getUsername()).isEqualTo("owner1");
        assertThat(details.getPassword()).isEqualTo("hashed-password");
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_OWNER");
    }

    @Test
    void should_throw_when_user_not_found_or_inactive() {
        when(userRepository.findByUsernameAndIsActiveTrue("ghost")).thenReturn(Optional.empty());

        UserDetailsService service = config.userDetailsService(userRepository);

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void should_produce_a_bcrypt_password_encoder_that_matches_its_own_hash() {
        var encoder = config.passwordEncoder();

        String hash = encoder.encode("plaintext-password");

        assertThat(hash).isNotEqualTo("plaintext-password");
        assertThat(encoder.matches("plaintext-password", hash)).isTrue();
        assertThat(encoder.matches("wrong-password", hash)).isFalse();
    }

    @Test
    void should_delegate_authentication_manager_bean_to_spring_security_configuration() throws Exception {
        AuthenticationManager expected = authRequest -> authRequest;
        when(authenticationConfiguration.getAuthenticationManager()).thenReturn(expected);

        AuthenticationManager actual = config.authenticationManager(authenticationConfiguration);

        assertThat(actual).isSameAs(expected);
    }
}
