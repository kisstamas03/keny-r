package com.melearning.elearning.config;

import com.melearning.elearning.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .userDetailsService(userDetailsService)
                .authorizeHttpRequests(authz -> authz
                        // Publikus útvonalak
                        .requestMatchers("/", "/login", "/register",
                                "/css/**", "/js/**", "/images/**",
                                "/h2-console/**").permitAll()

                        // Admin-only
                        .requestMatchers("/dashboard/admin/**",
                                "/admin/**").hasRole("ADMIN")

                        // Oktató + Admin
                        .requestMatchers("/courses/create",
                                "/dashboard/instructor/**").hasAnyRole("INSTRUCTOR", "ADMIN")

                        // Diák + Admin
                        .requestMatchers("/dashboard/student/**").hasAnyRole("STUDENT", "ADMIN")

                        // Dashboard főútvonal – bejelentkezett felhasználóknak
                        .requestMatchers("/dashboard").authenticated()

                        // Minden más bejelentkezést igényel
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", true)   // ← szerepkör szerinti átirányítás
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/h2-console/**")
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())
                );

        return http.build();
    }
}