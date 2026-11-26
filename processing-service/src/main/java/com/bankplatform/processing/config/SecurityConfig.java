package com.bankplatform.processing.config;

import com.bankplatform.common.security.JwtSecurityConfigurer;
import com.bankplatform.common.security.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtSecurityConfigurer jwt) throws Exception {
        return jwt.applyDefaults(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(JwtSecurityConfigurer.PUBLIC_DOCS_AND_HEALTH).permitAll()
                        .requestMatchers("/api/v1/processing/**").hasRole(Role.ADMIN.name())
                        .anyRequest().authenticated())
                .build();
    }
}
