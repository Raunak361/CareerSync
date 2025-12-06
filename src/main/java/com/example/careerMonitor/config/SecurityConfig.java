package com.example.careerMonitor.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.Customizer;

/**
 * Temporary Security Configuration to disable CSRF for API development.
 * In Phase 3, this will be replaced with Keycloak integration.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. **CRITICAL FIX:** Disable CSRF protection. This is necessary for API
                //    testing with POST/PUT/DELETE from external clients (like Postman).
                //    We will replace this with JWT-based security later.
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Configure Authorization: Use HTTP Basic Auth for all requests for now.
                //    This ensures that when you send the 'user' + password via Postman,
                //    the request is accepted for the purpose of testing the API calls.
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated() // All requests require authentication
                )
                .httpBasic(Customizer.withDefaults()); // Use HTTP Basic Authentication

        return http.build();
    }

    // Note: We are not defining any UserDetailsService or PasswordEncoder here
    // because Spring Boot is still providing the default 'user' and random password.
}