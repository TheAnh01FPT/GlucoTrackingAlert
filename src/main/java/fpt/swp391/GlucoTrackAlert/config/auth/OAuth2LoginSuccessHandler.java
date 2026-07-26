package fpt.swp391.GlucoTrackAlert.config.auth;

import fpt.swp391.GlucoTrackAlert.model.role.Role;
import fpt.swp391.GlucoTrackAlert.model.user.User;
import fpt.swp391.GlucoTrackAlert.repository.role.RoleRepository;
import fpt.swp391.GlucoTrackAlert.repository.user.UserRepository;
import fpt.swp391.GlucoTrackAlert.util.jwt.JwtUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtUtil jwtUtil;

    @Autowired
    public OAuth2LoginSuccessHandler(UserRepository userRepository, RoleRepository roleRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        if (email == null) {
            response.sendRedirect("/login?error=Email+not+found+from+Google");
            return;
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setFullName(name != null ? name : "Người dùng Google");
            user.setPasswordHash(""); // No password for OAuth users
            user.setStatus("active");
            user.setEmailVerified(true);

            Role patientRole = roleRepository.findByName("PATIENT").orElse(null);
            if (patientRole != null) {
                user.setRole(patientRole);
            }
            userRepository.save(user);
        } else if (user.getStatus() != null && user.getStatus().equals("banned")) {
            response.sendRedirect("/login?error=Account+is+banned");
            return;
        }

        String role = user.getRole() != null ? user.getRole().getName() : "PATIENT";
        String token = jwtUtil.generateToken(email, role);

        // Store JWT in cookie so browser keeps it
        Cookie cookie = new Cookie("jwt", token);
        cookie.setPath("/");
        cookie.setMaxAge(86400); // 1 day
        cookie.setHttpOnly(false); // allow js to access if needed (or we can just use the redirect page)
        response.addCookie(cookie);

        // Redirect to a specific endpoint that will set localStorage using JS and then redirect to homepage
        String redirectUrl = "/oauth2/success?token=" + token + "&email=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&role=" + role;
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
