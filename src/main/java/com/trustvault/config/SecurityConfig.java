package com.trustvault.config;

import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import com.trustvault.user.AppUserRepository;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.trustvault.security.LiveUserAccountFilter;

import com.trustvault.security.PermissionEndpointAccessDeniedHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            PermissionEndpointAccessDeniedHandler
                    permissionEndpointAccessDeniedHandler,
            AppUserRepository userRepository)
            throws Exception {

        http.csrf(csrf -> csrf
                .csrfTokenRepository(
                        CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(
                        new CsrfTokenRequestAttributeHandler())
        );

        http.addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class);
        
        http.addFilterBefore(
                new LiveUserAccountFilter(userRepository),
                UsernamePasswordAuthenticationFilter.class);

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/", "/index.html", "/login", "/login.html", "/logout",
                        "/signup.html", "/api/auth/signup",
                        "/dashboard", "/dashboard.html", "/admin", "/admin.html",
                        "/css/**", "/js/**",
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

        http.formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard.html", true)
                .permitAll()
        );

        http.logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessUrl("/login.html?logout")
                .permitAll()
        );

        http.exceptionHandling(exceptions ->
                exceptions.accessDeniedHandler(
                        permissionEndpointAccessDeniedHandler)
        );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}