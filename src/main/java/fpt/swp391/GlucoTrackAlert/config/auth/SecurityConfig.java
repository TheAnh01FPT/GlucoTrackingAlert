package fpt.swp391.GlucoTrackAlert.config.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**", "/login", "/register", "/error", "/css/**", "/js/**", "/images/**").permitAll()
                    .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/patient/**", "/api/patient/**").hasRole("PATIENT")
                    .requestMatchers("/doctor/**", "/api/doctor/**").hasRole("DOCTOR")
                    .anyRequest().authenticated()
            )

            .formLogin(form -> form.disable())

            // Cho phép hiển thị H2 Console trong thẻ iframe
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))

            // Thêm filter kiểm tra JWT trước filter xác thực mật khẩu
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}