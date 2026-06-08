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

    @GetMapping({"/doctor/homepage", "/doctor/home"})
    public String doctorDashboard() {
        return "doctor-home";
    }

    @GetMapping("/doctor/my-patients")
    public String doctorMyPatients() {
        return "my-patients";
    }

    @GetMapping("/doctor/settings")
    public String doctorSettings() {
        return "settings";
    }
}