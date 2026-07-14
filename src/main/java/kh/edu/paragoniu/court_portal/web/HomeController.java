package kh.edu.paragoniu.court_portal.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    /** The Cases Directory is the panel's landing screen. */
    @GetMapping("/")
    public String home() {
        return "redirect:/cases";
    }
}
