package com.example.quizlet.service.impl;

import com.example.quizlet.dto.auth.AuthResponse;
import com.example.quizlet.dto.auth.LoginRequest;
import com.example.quizlet.dto.auth.RegisterRequest;
import com.example.quizlet.dto.auth.TokenRefreshRequest;
import com.example.quizlet.entity.Role;
import com.example.quizlet.entity.User;
import com.example.quizlet.exception.DuplicateResourceException;
import com.example.quizlet.repository.UserRepository;
import com.example.quizlet.security.JwtService;
import com.example.quizlet.service.RefreshTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthServiceImpl} focused on the refresh-token feature:
 * token-pair issuance on register/login, refresh delegation, logout delegation,
 * and the uniform-error login contract.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final Long USER_ID = 42L;
    private static final String ACCESS = "access-jwt";
    private static final String REFRESH = "opaque-refresh-raw";

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private AuthServiceImpl service;

    private final User user = User.builder()
            .id(USER_ID)
            .username("dung")
            .email("dung@test.io")
            .password("bcrypt-hash")
            .role(Role.ROLE_USER)
            .build();

    // ---------- register / login issue a token PAIR ----------

    @Test
    @DisplayName("register: issues access + refresh tokens and returns user info")
    void registerIssuesTokenPair() {
        when(userRepository.existsByUsernameIgnoreCase("dung")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("dung@test.io")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn(ACCESS);
        when(refreshTokenService.issue(user)).thenReturn(REFRESH);

        AuthResponse response = service.register(
                new RegisterRequest("dung", "dung@test.io", "secret123"));

        assertThat(response.accessToken()).isEqualTo(ACCESS);
        assertThat(response.refreshToken()).isEqualTo(REFRESH);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.username()).isEqualTo("dung");
        verify(refreshTokenService).issue(user);
    }

    @Test
    @DisplayName("login: issues access + refresh tokens for the authenticated principal")
    void loginIssuesTokenPair() {
        when(userRepository.findByUsername("dung")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(authentication.getPrincipal()).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn(ACCESS);
        when(refreshTokenService.issue(user)).thenReturn(REFRESH);

        AuthResponse response = service.login(new LoginRequest("dung", "secret123"));

        assertThat(response.accessToken()).isEqualTo(ACCESS);
        assertThat(response.refreshToken()).isEqualTo(REFRESH);
        verify(refreshTokenService).issue(user);
    }

    // ---------- refresh ----------

    @Test
    @DisplayName("refresh: delegates to validateAndRotate and issues a fresh pair")
    void refreshDelegatesAndIssuesNewPair() {
        when(refreshTokenService.validateAndRotate("presented-raw")).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("new-access");
        when(refreshTokenService.issue(user)).thenReturn("new-refresh");

        AuthResponse response = service.refresh(new TokenRefreshRequest("presented-raw"));

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenService).validateAndRotate("presented-raw");
        verify(refreshTokenService).issue(user);
    }

    @Test
    @DisplayName("refresh: invalid token propagates BadCredentialsException (→ 401)")
    void refreshPropagatesBadCredentials() {
        when(refreshTokenService.validateAndRotate("bad"))
                .thenThrow(new BadCredentialsException("Invalid refresh token"));

        assertThatThrownBy(() -> service.refresh(new TokenRefreshRequest("bad")))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwtService, never()).generateToken(any());
        verify(refreshTokenService, never()).issue(any());
    }

    // ---------- logout ----------

    @Test
    @DisplayName("logout: delegates revocation of the presented refresh token")
    void logoutDelegatesRevocation() {
        service.logout(new TokenRefreshRequest("presented-raw"));

        verify(refreshTokenService).revoke("presented-raw");
    }

    // ---------- login error contract ----------

    @Test
    @DisplayName("login: unknown user → BadCredentialsException")
    void loginUnknownUserThrows() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("ghost", "secret123")))
                .isInstanceOf(BadCredentialsException.class);
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    @DisplayName("login: wrong password → BadCredentialsException with uniform message")
    void loginWrongPasswordThrows() {
        when(userRepository.findByUsername("dung")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("whatever the provider said"));

        assertThatThrownBy(() -> service.login(new LoginRequest("dung", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid username or password");
        verify(refreshTokenService, never()).issue(any());
    }

    @Test
    @DisplayName("register: duplicate username → DuplicateResourceException, no tokens issued")
    void registerDuplicateUsernameThrows() {
        when(userRepository.existsByUsernameIgnoreCase("dung")).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                new RegisterRequest("dung", "dung@test.io", "secret123")))
                .isInstanceOf(DuplicateResourceException.class);
        verify(refreshTokenService, never()).issue(any());
    }
}
