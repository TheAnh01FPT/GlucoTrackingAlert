package fpt.swp391.GlucoTrackAlert.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. Tắt CSRF theo cú pháp mới của Spring Boot 3.x
                .csrf(csrf -> csrf.disable())

                // 2. Mở khóa toàn bộ các đường dẫn (bao gồm cả giao diện UI và API) để dev không bị chặn
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/**").permitAll()
                )

                // 3. Cấu hình Session Stateless
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 4. Cho phép hiển thị H2 Console trong thẻ iframe
                .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}