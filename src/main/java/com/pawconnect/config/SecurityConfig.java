package com.pawconnect.config;

import com.pawconnect.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/register", "/api/auth/verify-email", "/api/auth/resend-verification",
                                "/api/auth/login", "/api/auth/refresh-token", "/api/auth/forgot-password",
                                "/api/auth/verify-reset-otp", "/api/auth/reset-password",
                                "/api/v1/auth/register", "/api/v1/auth/verify-email", "/api/v1/auth/resend-verification",
                                "/api/v1/auth/login", "/api/v1/auth/refresh-token", "/api/v1/auth/forgot-password",
                                "/api/v1/auth/verify-reset-otp", "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/adoptions", "/api/adoptions/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/adoptions", "/adoptions/*").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/", "/shop", "/service", "/profile", "/checkout", "/orders", "/orders/**", "/admin/**", "/auth/**", "/css/**", "/js/**", "/images/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/branches", "/api/categories", "/api/products", "/api/products/*", "/api/service-types").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/puppy-listings", "/api/puppy-listings/*").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
