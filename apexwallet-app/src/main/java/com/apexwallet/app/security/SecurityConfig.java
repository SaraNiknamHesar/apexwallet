package com.apexwallet.app.security;

import com.apexwallet.app.config.ApexProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(frame -> frame.deny()))
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/actuator/health", "/actuator/prometheus", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/transfers/*/approve", "/api/v1/transfers/*/reject").hasRole("SUPERVISOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/transfers").hasAnyRole("CUSTOMER", "TELLER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/loans/*/approve", "/api/v1/loans/*/reject").hasRole("SUPERVISOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/loans", "/api/v1/loans/*/repay").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/accounts").hasAnyRole("CUSTOMER", "TELLER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/accounts/**").hasAnyRole("CUSTOMER", "TELLER", "SUPERVISOR")
                        .requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole("CUSTOMER", "TELLER", "SUPERVISOR", "AUDITOR")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(ApexProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.getCorsAllowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Idempotency-Key", "X-Correlation-Id"));
        cors.setExposedHeaders(List.of("Idempotency-Replayed"));
        cors.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
