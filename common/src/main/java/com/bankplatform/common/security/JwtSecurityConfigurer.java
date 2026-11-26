package com.bankplatform.common.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Baseline stateless-JWT setup shared by every service. A service calls
 * {@link #applyDefaults(HttpSecurity)} and then declares only its own URL rules.
 */
public class JwtSecurityConfigurer {

    public static final String[] PUBLIC_DOCS_AND_HEALTH = {
            "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
            "/actuator/health/**", "/actuator/info", "/error"
    };

    private final JwtAuthenticationFilter jwtFilter;
    private final ProblemDetailSecurityHandlers handlers;

    public JwtSecurityConfigurer(JwtAuthenticationFilter jwtFilter, ProblemDetailSecurityHandlers handlers) {
        this.jwtFilter = jwtFilter;
        this.handlers = handlers;
    }

    public HttpSecurity applyDefaults(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(handlers).accessDeniedHandler(handlers))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
    }
}
