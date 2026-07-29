package kh.edu.paragoniu.court_portal.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * System Settings hub — links to the master-data (taxonomy) management screens:
 * case classifications, case statuses, hearing types, disposition outcomes.
 */
@Controller
public class SettingsController {

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("activeNav", "settings");
        return "settings";
    }
}
