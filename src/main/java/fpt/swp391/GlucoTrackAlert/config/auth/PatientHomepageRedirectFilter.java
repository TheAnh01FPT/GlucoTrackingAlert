package fpt.swp391.GlucoTrackAlert.config.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PatientHomepageRedirectFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization if needed
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestURI = httpRequest.getRequestURI();

        // If the frontend login page redirects to /patient/homepage, intercept and redirect to /patient/home
        if ("/patient/homepage".equals(requestURI)) {
            String userId = httpRequest.getParameter("userId");
            if (userId != null && !userId.isEmpty()) {
                httpResponse.sendRedirect("/patient/home?userId=" + userId);
            } else {
                httpResponse.sendRedirect("/patient/home");
            }
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // Cleanup if needed
    }
}
