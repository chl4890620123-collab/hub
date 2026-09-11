package com.hub.service;

import com.hub.config.JwtProperties;
import com.hub.model.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Issues short-lived access JWTs tied to a server-side refresh-token family via sid. */
@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final JwtProperties props;

    public JwtService(JwtEncoder encoder, JwtProperties props) { this.encoder = encoder; this.props = props; }

    public AccessToken issue(User user, long authVersion, String sessionId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(props.accessTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer()).subject(user.email()).audience(List.of(props.audience()))
                .issuedAt(now).notBefore(now.minusSeconds(5)).expiresAt(expiresAt).id(UUID.randomUUID().toString())
                .claim("uid", user.id()).claim("role", user.globalRole()).claim("ver", authVersion)
                .claim("sid", sessionId).claim("type", "access").build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, expiresAt);
    }

    public record AccessToken(String value, Instant expiresAt) {}
}
