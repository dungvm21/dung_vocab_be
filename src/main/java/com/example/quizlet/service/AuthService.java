package com.example.quizlet.service;

import com.example.quizlet.dto.auth.AuthResponse;
import com.example.quizlet.dto.auth.LoginRequest;
import com.example.quizlet.dto.auth.RegisterRequest;
import com.example.quizlet.entity.Role;
import com.example.quizlet.entity.User;
import com.example.quizlet.exception.DuplicateResourceException;
import com.example.quizlet.repository.UserRepository;
import com.example.quizlet.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration and login (JWT issuance).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    /**
     * Creates a new user with ROLE_USER and issues a JWT immediately.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new DuplicateResourceException("Username is already taken: " + request.username());
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("Email is already registered: " + request.email());
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                // Never persist the raw password.
                .password(passwordEncoder.encode(request.password()))
                .role(Role.ROLE_USER)
                .build();

        user = userRepository.save(user);

        String token = jwtService.generateToken(user);
        return AuthResponse.of(token, user);
    }

    /**
     * Authenticates by username or email, then issues a JWT.
     * Throws BadCredentialsException (mapped to 401) on failure.
     */
    public AuthResponse login(LoginRequest request) {
        try {
            User user = userRepository.findByUsername(request.usernameOrEmail())
                    .or(() -> userRepository.findByEmail(request.usernameOrEmail()))
                    .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), request.password()));
            User principal = (User) authentication.getPrincipal();
            return AuthResponse.of(jwtService.generateToken(principal), principal);
        } catch (AuthenticationException e) {
            // Uniform message so attackers cannot learn whether the account exists.
            throw new BadCredentialsException("Invalid username or password");
        }
    }
}
