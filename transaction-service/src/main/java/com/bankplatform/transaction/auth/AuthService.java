package com.bankplatform.transaction.auth;

import com.bankplatform.common.error.ConflictException;
import com.bankplatform.common.security.AuthenticatedUser;
import com.bankplatform.common.security.JwtTokenService;
import com.bankplatform.common.security.Role;
import com.bankplatform.transaction.auth.dto.LoginRequest;
import com.bankplatform.transaction.auth.dto.RegisterRequest;
import com.bankplatform.transaction.auth.dto.TokenResponse;
import com.bankplatform.transaction.auth.dto.UserResponse;
import com.bankplatform.transaction.user.User;
import com.bankplatform.transaction.user.UserMapper;
import com.bankplatform.transaction.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final UserMapper userMapper;
    private final Clock clock;

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new ConflictException("username-taken", "Username taken",
                    "Username '" + request.username() + "' is already registered");
        }
        User user = userRepository.save(new User(request.username(), passwordEncoder.encode(request.password()),
                request.fullName(), Role.CUSTOMER, clock.instant()));
        log.info("Registered customer {}", user.getId());
        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByUsernameIgnoreCase(request.username())
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        var issued = tokenService.issue(new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole()));
        return new TokenResponse(issued.token(), "Bearer", issued.expiresAt());
    }
}
