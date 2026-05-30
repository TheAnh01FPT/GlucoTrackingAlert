package fpt.swp391.GlucoTrackAlert.controller.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/login")
    public String loginPage() {
        return "login/login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register/register";
    }

    @GetMapping("/patient/homepage")
    public String patientDashboard() {
        return "homepage/homepage";
    }

    @GetMapping("/doctor/homepage")
    public String doctorDashboard() {
        return "doctor/dashboard";
    }
}