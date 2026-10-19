package com.trustvault.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http.authorizeHttpRequests(auth -> auth
            .requestMatchers(
                "/", "/index.html", "/css/**", "/js/**",
                "/images/**", "/favicon.ico", "/error"
            ).permitAll()
            .requestMatchers("/api/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/owner/**")
                .hasAnyRole("ADMIN", "OWNER")
            .requestMatchers("/api/viewer/**")
                .hasAnyRole("ADMIN", "OWNER", "VIEWER")
            .requestMatchers("/api/**").authenticated()
            .anyRequest().authenticated()
        );

        // Use Spring Security's session-based login page.
        // Default CSRF protection remains enabled.
        http.formLogin(form -> form
            .defaultSuccessUrl("/index.html", true)
            .permitAll()
        );

        http.logout(logout -> logout
            .logoutSuccessUrl("/index.html")
            .permitAll()
        );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}