package com.example.careerMonitor.WebConfig;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration to enable CORS for the frontend application.
 * This is crucial for allowing the React frontend (running on a different port)
 * to communicate with the Spring Boot backend.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Allowing all origins, methods, and headers for development.
        // In production, restrict 'allowedOrigins' to the frontend domain.
        registry.addMapping("/**") // Apply to all API endpoints
                .allowedOrigins("*") // Allows any domain access (development only)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Allow all CRUD methods
                .allowedHeaders("*"); // Allows all headers
    }
}