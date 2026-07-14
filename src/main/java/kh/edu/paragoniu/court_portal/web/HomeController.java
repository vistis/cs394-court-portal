package kh.edu.paragoniu.court_portal.web;

import kh.edu.paragoniu.court_portal.security.GreffierUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(
        @AuthenticationPrincipal GreffierUserDetails user,
        Model model
    ) {
        model.addAttribute("displayName", user.getDisplayName());
        model.addAttribute("username", user.getUsername());
        return "index";
    }
}
