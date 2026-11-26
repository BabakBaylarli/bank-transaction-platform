package com.bankplatform.common.autoconfigure;

import com.bankplatform.common.error.GlobalExceptionHandler;
import com.bankplatform.common.kafka.CorrelationIdRecordInterceptor;
import com.bankplatform.common.kafka.KafkaEventPublisher;
import com.bankplatform.common.security.JwtAuthenticationFilter;
import com.bankplatform.common.security.JwtProperties;
import com.bankplatform.common.security.JwtSecurityConfigurer;
import com.bankplatform.common.security.JwtTokenService;
import com.bankplatform.common.security.ProblemDetailSecurityHandlers;
import com.bankplatform.common.web.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.RecordInterceptor;

import java.time.Clock;
import java.time.Duration;

/**
 * Cross-cutting infrastructure every service gets just by depending on {@code common}:
 * JWT validation, RFC 7807 errors, correlation ids (HTTP + Kafka), OpenAPI metadata and a clock.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@EnableConfigurationProperties(JwtProperties.class)
public class BankPlatformAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        var registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        // runs before Spring Security, so even 401 responses carry the correlation id
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Configuration(proxyBeanMethods = false)
    static class SecuritySupport {

        @Bean
        public JwtTokenService jwtTokenService(JwtProperties properties, Clock clock) {
            return new JwtTokenService(properties, clock);
        }

        @Bean
        public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenService tokenService) {
            return new JwtAuthenticationFilter(tokenService);
        }

        /** The JWT filter belongs to the security chain only, not to the plain servlet chain. */
        @Bean
        public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterServletRegistration(
                JwtAuthenticationFilter filter) {
            var registration = new FilterRegistrationBean<>(filter);
            registration.setEnabled(false);
            return registration;
        }

        @Bean
        public ProblemDetailSecurityHandlers problemDetailSecurityHandlers(ObjectMapper objectMapper) {
            return new ProblemDetailSecurityHandlers(objectMapper);
        }

        @Bean
        public JwtSecurityConfigurer jwtSecurityConfigurer(JwtAuthenticationFilter filter,
                                                           ProblemDetailSecurityHandlers handlers) {
            return new JwtSecurityConfigurer(filter, handlers);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class KafkaSupport {

        @Bean
        public RecordInterceptor<Object, Object> correlationIdRecordInterceptor() {
            return new CorrelationIdRecordInterceptor();
        }

        @Bean
        @ConditionalOnBean(KafkaTemplate.class)
        public KafkaEventPublisher kafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                                       @Value("${app.kafka.send-timeout:10s}") Duration timeout) {
            return new KafkaEventPublisher(kafkaTemplate, timeout);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OpenApiSupport {

        private static final String BEARER = "bearerAuth";

        @Bean
        @ConditionalOnMissingBean
        public OpenAPI openApi(@Value("${spring.application.name}") String name,
                               @Value("${app.openapi.description:}") String description) {
            return new OpenAPI()
                    .info(new Info().title(name).version("v1").description(description))
                    .addSecurityItem(new SecurityRequirement().addList(BEARER))
                    .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                            .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
        }
    }
}
