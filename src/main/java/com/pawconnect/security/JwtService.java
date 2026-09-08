package com.pawconnect.security;

import com.pawconnect.config.JwtProperties;
import com.pawconnect.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private final JwtProperties properties;
    private final Key signingKey;
    private final Clock clock;

    @Autowired
    public JwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must contain at least 32 bytes");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(User user) {
        return generateToken(user, "access", properties.accessTokenTtl());
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, "refresh", properties.refreshTokenTtl());
    }

    public String getEmail(String token) {
        return parse(token).getPayload().getSubject();
    }

    public boolean isAccessToken(String token) {
        return "access".equals(parse(token).getPayload().get(TOKEN_TYPE_CLAIM, String.class));
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(parse(token).getPayload().get(TOKEN_TYPE_CLAIM, String.class));
    }

    private String generateToken(User user, String type, java.time.Duration ttl) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(TOKEN_TYPE_CLAIM, type)
                .claim("role", user.getRole().getName().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(signingKey)
                .compact();
    }

    private Jws<Claims> parse(String token) throws JwtException {
        return Jwts.parser().verifyWith((javax.crypto.SecretKey) signingKey).build().parseSignedClaims(token);
    }
}
