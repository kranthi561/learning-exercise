package com.aiengineering.service;

import com.aiengineering.config.JwtProperties;
import com.aiengineering.domain.User;
import com.aiengineering.repository.UserRepository;
import com.aiengineering.security.JwtService;
import com.aiengineering.web.dto.auth.LoginRequest;
import com.aiengineering.web.dto.auth.RegisterRequest;
import com.aiengineering.web.dto.auth.TokenResponse;
import com.aiengineering.web.dto.user.UserResponse;
import com.aiengineering.web.dto.user.UserSummaryResponse;
import com.aiengineering.web.exception.ResourceNotFoundException;
import com.aiengineering.web.mapper.UserMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = new User();
        user.setEmail(request.email().strip().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().strip());
        return userMapper.toResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository
                .findByEmailIgnoreCase(request.email().strip().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        String token = jwtService.createToken(user);
        return new TokenResponse(token, "Bearer", jwtProperties.expirationSeconds());
    }

    @Transactional(readOnly = true)
    public UserResponse getById(long id) {
        return userRepository
                .findById(id)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> searchSummaries(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return userRepository.searchSummaries(q.strip()).stream()
                .map(userMapper::toSummary)
                .toList();
    }
}
