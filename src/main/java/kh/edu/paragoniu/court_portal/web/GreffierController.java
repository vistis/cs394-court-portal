package kh.edu.paragoniu.court_portal.web;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import kh.edu.paragoniu.court_portal.cases.CaseDetailNotFoundException;
import kh.edu.paragoniu.court_portal.greffier.AssignCaseOption;
import kh.edu.paragoniu.court_portal.greffier.AssignedCaseRow;
import kh.edu.paragoniu.court_portal.greffier.GreffierRow;
import kh.edu.paragoniu.court_portal.greffier.GreffierService;
import kh.edu.paragoniu.court_portal.security.GreffierUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Greffier Management screen — restricted to the Chief Greffier via the
 * CASE_ASSIGN authority (see SecurityConfig). Lists greffiers straight from the
 * database.
 */
@Controller
@RequiredArgsConstructor
public class GreffierController {

    private static final int PAGE_SIZE = 8;

    private final GreffierService greffierService;

    @GetMapping("/greffiers")
    public String greffiers(
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "0") int page,
        Model model
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
        Page<GreffierRow> result = greffierService.search(query, pageable);

        model.addAttribute("page", result);
        model.addAttribute("query", query);
        model.addAttribute("activeNav", "greffier");
        return "greffier-management";
    }

    @GetMapping("/greffiers/{greffierId}")
    public String greffierDetail(
        @PathVariable String greffierId,
        @RequestParam(required = false) String query,
        @RequestParam(defaultValue = "0") int page,
        Model model,
        HttpServletResponse response
    ) {
        UUID id;
        String greffierName;
        try {
            id = UUID.fromString(greffierId);
            greffierName = greffierService.findGreffierName(id);
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "greffier");
            return "case-not-found";
        }

        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
        Page<AssignedCaseRow> result = greffierService.findAssignedCases(
            id,
            query,
            pageable
        );

        model.addAttribute("page", result);
        model.addAttribute("query", query);
        model.addAttribute("greffierId", id.toString());
        model.addAttribute("greffierName", greffierName);
        model.addAttribute("activeNav", "greffier");
        return "greffier-detail";
    }

    /** Typeahead for the Assign Case modal (JSON). */
    @GetMapping("/greffiers/{greffierId}/case-search")
    @ResponseBody
    public List<AssignCaseOption> caseSearch(
        @PathVariable String greffierId,
        @RequestParam(required = false) String q
    ) {
        try {
            return greffierService.searchAssignableCases(
                UUID.fromString(greffierId),
                q,
                8
            );
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    /** Assign an existing case to a greffier, then return to their detail page. */
    @PostMapping("/greffiers/{greffierId}/assign")
    public String assignCase(
        @PathVariable String greffierId,
        @RequestParam String caseId,
        @AuthenticationPrincipal GreffierUserDetails user,
        RedirectAttributes redirectAttributes,
        HttpServletResponse response,
        Model model
    ) {
        UUID id;
        UUID parsedCaseId;
        try {
            id = UUID.fromString(greffierId);
            parsedCaseId = UUID.fromString(caseId);
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "greffier");
            return "case-not-found";
        }

        try {
            String caseNumber = greffierService.assignCase(
                id,
                parsedCaseId,
                user == null ? null : user.getUserId()
            );
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Case " + caseNumber + " assigned successfully."
            );
        } catch (CaseDetailNotFoundException ex) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Could not assign case — the case or greffier no longer exists."
            );
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/greffiers/" + id;
    }
}
