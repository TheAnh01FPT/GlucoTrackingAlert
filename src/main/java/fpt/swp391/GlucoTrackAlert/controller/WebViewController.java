package fpt.swp391.GlucoTrackAlert.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping
public class WebViewController {

    // Map root and common UI paths to avoid 404s
    @GetMapping({"/", "/index", "/home"})
    public String showHome() {
        // simple redirect to register page for first-run convenience
        return "redirect:/register-ui";
    }

    // Accept both /register and /register-ui
    @GetMapping({"/register", "/register-ui"})
    public String showRegisterPage() {
        return "register";
    }

    // Accept both /login and /login-ui
    @GetMapping({"/login", "/login-ui"})
    public String showLoginPage() {
        return "login";
    }
}