package com.bankplatform.search.config;

import com.bankplatform.common.security.JwtSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtSecurityConfigurer jwt) throws Exception {
        return jwt.applyDefaults(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(JwtSecurityConfigurer.PUBLIC_DOCS_AND_HEALTH).permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
