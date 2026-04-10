package com.aiengineering.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Marks this class as a Spring configuration source.
@Configuration

// Enables Spring Security's web security support and provides the
// SecurityFilterChain bean that replaces the default auto-configuration.
@EnableWebSecurity

// Lombok: generates a constructor for jwtAuthenticationFilter so Spring injects it.
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // @Bean exposes the SecurityFilterChain — Spring Security uses it to build
    // the ordered chain of filters applied to every incoming HTTP request.
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF protection because the API is stateless (JWT, no cookies)
            // and CSRF attacks rely on browser cookie auto-submission.
            .csrf(AbstractHttpConfigurer::disable)

            // STATELESS means Spring Security never creates or uses an HttpSession —
            // the JWT in each request is the only source of authentication state.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Return 401 Unauthorized (not a login-page redirect) for unauthenticated requests,
            // which is appropriate for a REST API consumed by clients, not browsers.
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

            .authorizeHttpRequests(auth -> auth
                // Allow register and login without a token — anyone can create an account or sign in.
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                // Allow health/metrics endpoints for load-balancers and monitoring without auth.
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/prometheus").permitAll()
                // All other endpoints require a valid JWT.
                .anyRequest().authenticated())

            // Disable form-based login and HTTP Basic — this is a token-only REST API.
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)

            // Insert our JWT filter before Spring Security's built-in username/password filter
            // so the SecurityContext is populated with the user principal before authorization checks run.
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // @Bean makes the PasswordEncoder available for injection in UserService.
    // BCrypt is used because it is slow by design (adaptive cost factor),
    // making brute-force and rainbow-table attacks computationally expensive.
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
