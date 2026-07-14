package kh.edu.paragoniu.court_portal.web;

import kh.edu.paragoniu.court_portal.security.GreffierUserDetails;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes the signed-in user's display data (name, initials, role) to every
 * view so the shared top app bar can render without each controller repeating
 * the logic.
 */
@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute
    public void addCurrentUser(
        @AuthenticationPrincipal GreffierUserDetails user,
        Model model
    ) {
        if (user == null) {
            return;
        }
        model.addAttribute("displayName", user.getDisplayName());
        model.addAttribute("userInitials", initials(user.getDisplayName()));
        model.addAttribute("userRole", primaryRole(user));
    }

    private String initials(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "?";
        }
        String[] parts = displayName.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String second = parts.length > 1
            ? parts[parts.length - 1].substring(0, 1)
            : "";
        return (first + second).toUpperCase();
    }

    private String primaryRole(GreffierUserDetails user) {
        return user
            .getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("ROLE_"))
            .findFirst()
            .map(a -> a.substring("ROLE_".length()).replace('_', ' '))
            .orElse("USER");
    }
}
