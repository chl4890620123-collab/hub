package com.hub.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.Cookie;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
public class SecurityConfig {
    // The React SPA shell (see PageController) is public at every one of these paths - auth is
    // enforced only at the /api/** layer, exactly like "/" and "/login" already were before the SPA
    // existed. Kept as one list so the authorizeHttpRequests permitAll and the bearerTokenResolver
    // exemption below can't drift apart.
    private static final String[] SPA_PAGE_PATHS = {
            "/login", "/signup", "/signup/member", "/signup/admin",
            "/search", "/ask", "/context", "/todos", "/review",
            "/documents", "/meetings", "/sheets", "/connectors", "/account",
            "/admin", "/admin/members", "/admin/reassign", "/admin/users",
            "/admin/search", "/admin/security", "/admin/history",
    };

    @Bean
    PasswordEncoder passwordEncoder() {
        // Cost 12 is a reasonable local/server default while remaining usable on modest hardware.
        return new BCryptPasswordEncoder(12);
    }

    private SecretKey jwtSecretKey(JwtProperties props) {
        byte[] secret = props.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) throw new IllegalStateException("HUB_JWT_SECRET must be at least 32 bytes");
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(JwtProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtSecretKey(props)));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties props) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey(props)).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(props.issuer());
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(props.audience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        OAuth2TokenValidator<Jwt> tokenType = jwt -> "access".equals(jwt.getClaimAsString("type"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid token type", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaults, audience, tokenType));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString("role");
            if (role == null || role.isBlank()) return List.of();
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        });
        return converter;
    }

    @Bean
    BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
        return request -> {
            String headerToken = headerResolver.resolve(request);
            if (headerToken != null) return headerToken;
            String path = request.getRequestURI();
            // Public pages and refresh/logout must still work when the access JWT is expired.
            if ("/".equals(path) || java.util.Arrays.asList(SPA_PAGE_PATHS).contains(path)
                    || "/api/auth/login".equals(path)
                    || "/api/auth/signup/member".equals(path) || "/api/auth/signup/admin".equals(path)
                    || "/api/auth/signup/projects".equals(path) || "/api/auth/signup/organization".equals(path)
                    || "/api/auth/setup-status".equals(path) || "/api/auth/check-login-id".equals(path)
                    || "/api/auth/refresh".equals(path) || "/api/auth/logout".equals(path)
                    || "/api/connectors/google/callback".equals(path)
                    || "/api/connectors/oauth/callback".equals(path)
                    || path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/assets/")
                    || "/actuator/health".equals(path)) {
                return null;
            }
            Cookie[] cookies = request.getCookies();
            if (cookies == null) return null;
            for (Cookie cookie : cookies) {
                if (JwtProperties.ACCESS_COOKIE.equals(cookie.getName())) return cookie.getValue();
            }
            return null;
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, BearerTokenResolver bearerTokenResolver,
                                            JwtAuthenticationConverter converter) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");

        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Logout is exempt: signing out must never fail because the CSRF cookie went missing,
                // and the worst a forged logout can do is end a session the user can restart.
                // The plain (non-XOR) request handler is required for a JS client: the default
                // XorCsrfTokenRequestAttributeHandler masks the value it hands to server-rendered forms
                // (Thymeleaf's th:action reads it through the same handler, so it always got the right
                // value) but a SPA reads the XSRF-TOKEN cookie directly and echoes that raw value back as
                // a header - against the XOR handler that raw value fails to "un-mask" into anything
                // valid, so every POST from the React app 401s here before it ever reaches a controller.
                .csrf(config -> config.csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/auth/logout"))
                // The token is only written to the cookie when something reads it during the request. Without
                // this the cookie can be missing on a page the browser served from cache, and the next POST
                // (logout, most visibly) fails CSRF and — being anonymous — comes back 401 with no body.
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/css/**", "/js/**", "/assets/**", "/favicon.ico", "/actuator/health").permitAll()
                        .requestMatchers(SPA_PAGE_PATHS).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/setup-status", "/api/auth/check-login-id", "/api/auth/signup/projects", "/api/auth/signup/organization").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/signup/member", "/api/auth/signup/admin", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/connectors/google/callback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/connectors/oauth/callback").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(bearerTokenResolver)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .cors(Customizer.withDefaults());
        return http.build();
    }

    /** Forces the CSRF token to materialise so CookieCsrfTokenRepository always writes XSRF-TOKEN. */
    static final class CsrfCookieFilter extends org.springframework.web.filter.OncePerRequestFilter {
        @Override
        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                        jakarta.servlet.http.HttpServletResponse response,
                                        jakarta.servlet.FilterChain chain)
                throws jakarta.servlet.ServletException, java.io.IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) token.getToken();
            chain.doFilter(request, response);
        }
    }
}
