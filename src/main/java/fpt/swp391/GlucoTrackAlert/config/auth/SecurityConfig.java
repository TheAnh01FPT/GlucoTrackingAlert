package fpt.swp391.GlucoTrackAlert.config.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                            "/api/auth/**", "/login", "/register", "/forgot-password", "/error",
                            "/css/**", "/js/**", "/images/**"
                        ).permitAll()

                        .requestMatchers("/meal-logs").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")
                        .requestMatchers("/api/meal-logs/**").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")

                        .requestMatchers("/api/reminders/**").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")

                        .requestMatchers("/api/ai/**").hasAnyRole("DOCTOR", "ADMIN")

                        .requestMatchers("/patient/medications").hasAnyRole("PATIENT", "ADMIN")
                        // Phải đặt rule cụ thể trước rule wildcard để Spring Security match đúng
                        .requestMatchers("/api/medications/prescriptions/*/cancel").hasAnyRole("DOCTOR", "ADMIN")
                        .requestMatchers("/api/medications/prescriptions").hasAnyRole("DOCTOR", "ADMIN")
                        .requestMatchers("/api/medications/prescriptions/**").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")
                        .requestMatchers("/api/medications/**").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")

                        .requestMatchers("/health-logs/doctor-view", "/health-logs/doctor-chart").hasAnyRole("ADMIN", "DOCTOR")
                        .requestMatchers("/health-logs/kidney-risk/weekly").hasAnyRole("PATIENT", "ADMIN", "DOCTOR")
                        .requestMatchers("/health-logs/kidney-risk/weekly/**").hasAnyRole("ADMIN", "DOCTOR")
                        .requestMatchers("/health-logs/kidney-risk/doctor/**", "/health-logs/kidney-risk/doctor/dashboard").hasAnyRole("DOCTOR", "ADMIN")
                        .requestMatchers("/health-logs/doctor-view/thresholds/**").hasAnyRole("ADMIN", "DOCTOR")
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/assignments/**", "/api/assignments").hasAnyRole("ADMIN")
                        .requestMatchers("/api/doctors/**", "/api/doctors").hasAnyRole("ADMIN", "DOCTOR")
                        .requestMatchers("/api/patient/**", "/api/patient").hasAnyRole("ADMIN", "PATIENT", "DOCTOR")
                        .requestMatchers("/patient/**").hasRole("PATIENT")
                        .requestMatchers("/health-reminders").hasAnyRole("PATIENT", "ADMIN")
                        .requestMatchers("/doctor/**", "/api/doctor/**").hasAnyRole("DOCTOR", "ADMIN")
                        .anyRequest().authenticated()
                )

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                .formLogin(form -> form.disable())

                .headers(headers -> headers.frameOptions(frame -> frame.disable()))

                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}