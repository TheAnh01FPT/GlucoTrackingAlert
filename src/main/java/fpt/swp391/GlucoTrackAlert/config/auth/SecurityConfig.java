package fpt.swp391.GlucoTrackAlert.config.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // 1. Các đường dẫn công khai (Public)
                        .requestMatchers(
                                "/", "/api/auth/**", "/login", "/register", "/forgot-password", "/error",
                                "/css/**", "/js/**", "/images/**", "/api/contact-requests"
                        ).permitAll()

                        // Ảnh avatar bác sĩ: công khai để bệnh nhân có thể xem
                        .requestMatchers("/uploads/doctors/*/avatar*").permitAll()

                        // 2. Phân quyền cho Nhật ký ăn uống & Nhắc nhở
                        .requestMatchers("/meal-logs").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")
                        .requestMatchers("/api/meal-logs/**").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")
                        .requestMatchers("/api/reminders/**").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")

                        // 3. Phân quyền cho AI (Chỉ ADMIN và DOCTOR)
                        .requestMatchers("/api/ai/**").hasAnyRole("DOCTOR", "ADMIN")
                        .requestMatchers("/ai/**").hasAnyRole("ADMIN", "DOCTOR")

                        // 4. Phân quyền cho Thuốc & Đơn thuốc (Quy tắc cụ thể đặt trước wildcard)
                        .requestMatchers("/patient/medications").hasAnyRole("PATIENT", "ADMIN")
                        .requestMatchers("/api/medications/prescriptions/*/cancel").hasAnyRole("DOCTOR", "ADMIN")
                        .requestMatchers("/api/medications/prescriptions").hasAnyRole("DOCTOR", "ADMIN")
                        .requestMatchers("/api/medications/prescriptions/**").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")
                        .requestMatchers("/api/medications/**").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")

                        // 5. Phân quyền cho Hồ sơ sức khỏe & Nguy cơ thận
                        .requestMatchers("/health-logs/doctor-view", "/health-logs/doctor-chart").hasAnyRole("ADMIN", "DOCTOR")
                        .requestMatchers("/health-logs/kidney-risk/weekly").hasAnyRole("PATIENT", "ADMIN", "DOCTOR")
                        .requestMatchers("/health-logs/kidney-risk/weekly/**").hasAnyRole("ADMIN", "DOCTOR")
                        .requestMatchers("/health-logs/kidney-risk/doctor/**", "/health-logs/kidney-risk/doctor/dashboard").hasAnyRole("DOCTOR", "ADMIN")
                        .requestMatchers("/health-logs/doctor-view/thresholds/**").hasAnyRole("ADMIN", "DOCTOR")
                        .requestMatchers("/api/health-logs/**").hasAnyRole("DOCTOR", "ADMIN", "PATIENT")

                        // 6. Phân quyền cho Quản trị viên (ADMIN)
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/assignments/**", "/api/assignments").hasRole("ADMIN")

                        // 7. Phân quyền cho Bác sĩ & Bệnh nhân
                        .requestMatchers("/api/patient/assignments/**").hasRole("PATIENT")
                        .requestMatchers("/api/doctors/**", "/api/doctors").hasAnyRole("ADMIN", "DOCTOR")
                        .requestMatchers("/api/patient/**", "/api/patient").hasAnyRole("ADMIN", "PATIENT", "DOCTOR")
                        .requestMatchers("/patient/**").hasRole("PATIENT")
                        .requestMatchers("/health-reminders").hasAnyRole("PATIENT", "ADMIN")
                        .requestMatchers("/doctor/**", "/api/doctor/**").hasAnyRole("DOCTOR", "ADMIN")

                        // 8. Khuyến nghị và Thông báo
                        .requestMatchers(HttpMethod.GET, "/api/recommendations/patient/**").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")
                        .requestMatchers("/api/recommendations/**").hasRole("DOCTOR")
                        .requestMatchers("/api/notifications/**").hasAnyRole("PATIENT", "DOCTOR", "ADMIN")

                        // 9. Bảo vệ tài liệu/CCCD nhạy cảm trong uploads (Chỉ ADMIN, DOCTOR, PATIENT được xem)
                        .requestMatchers("/uploads/banners/**").permitAll()
                        .requestMatchers("/uploads/**").hasAnyRole("ADMIN", "DOCTOR", "PATIENT")

                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .formLogin(form -> form.disable())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "remember-me", "jwt")
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