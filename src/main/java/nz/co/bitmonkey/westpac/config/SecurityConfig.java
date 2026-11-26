// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bitmonkey NZ Ltd

package nz.co.bitmonkey.westpac.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/** Defines the security filter chains for the Actuator endpoints and for the API. */
@Configuration(proxyBeanMethods = false)
public final class SecurityConfig {

    private SecurityConfig() {}

    /** Permits the {@code health}, {@code info} and {@code prometheus} endpoints and refuses every other endpoint. */
    @Bean
    @Order(1)
    SecurityFilterChain managementChain(HttpSecurity http) {
        return stateless(http.securityMatcher(EndpointRequest.toAnyEndpoint()))
                .authorizeHttpRequests(
                        requests -> requests.requestMatchers(EndpointRequest.to("health", "info", "prometheus"))
                                .permitAll()
                                .anyRequest()
                                .denyAll())
                .build();
    }

    /**
     * Permits the two savings account routes and refuses every other request, with {@code 401}. The error dispatch is
     * permitted, so a fault that reaches the error page keeps its {@code 500}.
     */
    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http) {
        return stateless(http)
                .authorizeHttpRequests(requests -> requests.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/savings-accounts")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/savings-accounts/*")
                        .permitAll()
                        .anyRequest()
                        .denyAll())
                .build();
    }

    private static HttpSecurity stateless(HttpSecurity http) {
        return http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
    }
}
