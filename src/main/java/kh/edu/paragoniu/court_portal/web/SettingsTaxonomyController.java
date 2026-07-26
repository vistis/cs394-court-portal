package kh.edu.paragoniu.court_portal.web;

import jakarta.servlet.http.HttpServletResponse;
import kh.edu.paragoniu.court_portal.settings.TaxonomyException;
import kh.edu.paragoniu.court_portal.settings.TaxonomyKind;
import kh.edu.paragoniu.court_portal.settings.TaxonomyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Master-data (taxonomy) management screens under System Settings. One generic
 * controller serves all four kinds, keyed by the {@code slug} path segment.
 * Viewing needs CASE_VIEW; add/delete need CASE_CREATE (see SecurityConfig).
 */
@Controller
@RequiredArgsConstructor
public class SettingsTaxonomyController {

    private final TaxonomyService taxonomyService;

    @GetMapping("/settings/{slug}")
    public String taxonomy(
        @PathVariable String slug,
        Model model,
        HttpServletResponse response
    ) {
        TaxonomyKind kind = TaxonomyKind.fromSlug(slug);
        if (kind == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "settings");
            return "case-not-found";
        }
        model.addAttribute("kind", kind);
        model.addAttribute("rows", taxonomyService.list(kind));
        model.addAttribute("activeNav", "settings");
        return "taxonomy";
    }

    @PostMapping("/settings/{slug}")
    public String add(
        @PathVariable String slug,
        @RequestParam(required = false) String name,
        RedirectAttributes redirectAttributes,
        HttpServletResponse response,
        Model model
    ) {
        TaxonomyKind kind = TaxonomyKind.fromSlug(slug);
        if (kind == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "settings");
            return "case-not-found";
        }
        try {
            taxonomyService.add(kind, name);
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Added successfully."
            );
        } catch (TaxonomyException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/settings/" + slug;
    }

    @PostMapping("/settings/{slug}/{id}/delete")
    public String delete(
        @PathVariable String slug,
        @PathVariable int id,
        RedirectAttributes redirectAttributes,
        HttpServletResponse response,
        Model model
    ) {
        TaxonomyKind kind = TaxonomyKind.fromSlug(slug);
        if (kind == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "settings");
            return "case-not-found";
        }
        try {
            taxonomyService.delete(kind, id);
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Deleted successfully."
            );
        } catch (TaxonomyException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/settings/" + slug;
    }
}
