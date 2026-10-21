package com.yourcaryourway.chat.accounts.service;

import com.yourcaryourway.chat.accounts.config.JwtProperties;
import com.yourcaryourway.chat.accounts.domain.AccountStatus;
import com.yourcaryourway.chat.accounts.domain.AppUser;
import com.yourcaryourway.chat.accounts.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
            JwtProperties jwtProperties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public record Session(String accessToken, long expiresInSeconds, AppUser user) {
    }

    /**
     * Checks the credentials and issues a JWT. The error message is deliberately
     * generic: it does not say
     * whether the email or the password is wrong (US-1.3).
     */
    @Transactional(readOnly = true)
    public Session login(String email, String rawPassword) {
        AppUser user = users.findByEmailIgnoreCase(email)
                .filter(u -> u.getStatus() == AccountStatus.ACTIVE)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException("Identifiants invalides"));

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiresAt(now.plus(jwtProperties.ttl()))
                .build();
        String token = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Session(token, jwtProperties.ttl().toSeconds(), user);
    }
}
