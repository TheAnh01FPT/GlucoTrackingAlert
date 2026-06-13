package fpt.swp391.GlucoTrackAlert.config.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
<<<<<<< HEAD
                        .requestMatchers("/api/auth/**", "/login", "/register", "/error", "/css/**", "/js/**", "/images/**", "/uploads/**").permitAll()
=======
                        .requestMatchers("/api/auth/**", "/login", "/register", "/forgot-password", "/error", "/css/**", "/js/**", "/images/**").permitAll()
>>>>>>> 645926964eebdc2a52ac801e7f676031b556048b
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        // FIX LỖI 3: /api/users/** có thể tra email→userId của bất kỳ user nào → chỉ ADMIN được dùng
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/assignments/**", "/api/assignments").hasAnyRole("ADMIN")
                        .requestMatchers("/api/doctors/**", "/api/doctors").hasAnyRole("ADMIN", "DOCTOR")
                        .requestMatchers("/api/patient/**", "/api/patient").hasAnyRole("ADMIN", "PATIENT", "DOCTOR")
                        .requestMatchers("/patient/**").hasRole("PATIENT")
                        .requestMatchers("/doctor/**", "/api/doctor/**").hasRole("DOCTOR")
                        .anyRequest().authenticated()
                )

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .headers(headers -> headers.frameOptions(frame -> frame.disable()))

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}