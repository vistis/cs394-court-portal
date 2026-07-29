package kh.edu.paragoniu.court_portal.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kh.edu.paragoniu.court_portal.cases.CaseDetailNotFoundException;
import kh.edu.paragoniu.court_portal.legal.CaseParticipantOption;
import kh.edu.paragoniu.court_portal.legal.CreateJudgeForm;
import kh.edu.paragoniu.court_portal.legal.CreateLawyerForm;
import kh.edu.paragoniu.court_portal.legal.JudgeAssignCaseOption;
import kh.edu.paragoniu.court_portal.legal.JudgeProfile;
import kh.edu.paragoniu.court_portal.legal.LawyerAssignCaseOption;
import kh.edu.paragoniu.court_portal.legal.LawyerJudgeException;
import kh.edu.paragoniu.court_portal.legal.LawyerJudgeService;
import kh.edu.paragoniu.court_portal.legal.LawyerProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Lawyers &amp; Judges directory — one page with two tabs (lawyer / judge),
 * each a searchable, paginated, DB-backed list. Viewable by any signed-in court
 * user (CASE_VIEW).
 */
@Controller
@RequiredArgsConstructor
public class LawyerJudgeController {

    private static final int PAGE_SIZE = 8;

    private final LawyerJudgeService lawyerJudgeService;

    @GetMapping("/lawyers-judges")
    public String directory(
        @RequestParam(required = false) String tab,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        Model model
    ) {
        boolean judgeTab = "judge".equalsIgnoreCase(tab);
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);

        if (judgeTab) {
            model.addAttribute(
                "page",
                lawyerJudgeService.searchJudges(query, status, pageable)
            );
            model.addAttribute("selectedStatus", status);
        } else {
            model.addAttribute(
                "page",
                lawyerJudgeService.searchLawyers(query, pageable)
            );
        }

        model.addAttribute("tab", judgeTab ? "judge" : "lawyer");
        model.addAttribute("query", query);
        model.addAttribute("activeNav", "lawyers");
        return "lawyers-judges";
    }

    @GetMapping("/lawyers-judges/add-lawyer")
    public String addLawyerForm(Model model) {
        if (!model.containsAttribute("createLawyerForm")) {
            model.addAttribute("createLawyerForm", new CreateLawyerForm());
        }
        model.addAttribute("activeNav", "lawyers");
        return "lawyer-form";
    }

    @PostMapping("/lawyers-judges/add-lawyer")
    public String createLawyer(
        @Valid @ModelAttribute("createLawyerForm") CreateLawyerForm form,
        BindingResult bindingResult,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        if (!bindingResult.hasErrors()) {
            try {
                lawyerJudgeService.createLawyer(form);
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Lawyer registered successfully."
                );
                return "redirect:/lawyers-judges?tab=lawyer";
            } catch (LawyerJudgeException ex) {
                if (ex.getFieldName() == null) {
                    bindingResult.reject("lawyer.create.failed", ex.getMessage());
                } else {
                    bindingResult.rejectValue(
                        ex.getFieldName(),
                        "lawyer.create.failed",
                        ex.getMessage()
                    );
                }
            }
        }

        model.addAttribute("activeNav", "lawyers");
        return "lawyer-form";
    }

    @GetMapping("/lawyers-judges/add-judge")
    public String addJudgeForm(Model model) {
        if (!model.containsAttribute("createJudgeForm")) {
            model.addAttribute("createJudgeForm", new CreateJudgeForm());
        }
        model.addAttribute("activeNav", "lawyers");
        return "judge-form";
    }

    @PostMapping("/lawyers-judges/add-judge")
    public String createJudge(
        @Valid @ModelAttribute("createJudgeForm") CreateJudgeForm form,
        BindingResult bindingResult,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        if (!bindingResult.hasErrors()) {
            try {
                lawyerJudgeService.createJudge(form);
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Judge registered successfully."
                );
                return "redirect:/lawyers-judges?tab=judge";
            } catch (LawyerJudgeException ex) {
                if (ex.getFieldName() == null) {
                    bindingResult.reject("judge.create.failed", ex.getMessage());
                } else {
                    bindingResult.rejectValue(
                        ex.getFieldName(),
                        "judge.create.failed",
                        ex.getMessage()
                    );
                }
            }
        }

        model.addAttribute("activeNav", "lawyers");
        return "judge-form";
    }

    /** Judge Profile page — details + assigned cases. Viewable with CASE_VIEW. */
    @GetMapping("/lawyers-judges/judges/{judgeId}")
    public String judgeProfile(
        @PathVariable String judgeId,
        Model model,
        HttpServletResponse response
    ) {
        UUID id;
        try {
            id = UUID.fromString(judgeId);
            model.addAttribute("profile", lawyerJudgeService.findJudgeProfile(id));
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "lawyers");
            return "case-not-found";
        }

        model.addAttribute("involvedCases", lawyerJudgeService.findJudgeCases(id));
        model.addAttribute("activeNav", "lawyers");
        return "judge-profile";
    }

    /** Typeahead for the "Assign Case to Judge" modal (JSON). */
    @GetMapping("/lawyers-judges/judges/{judgeId}/case-search")
    @ResponseBody
    public List<JudgeAssignCaseOption> judgeCaseSearch(
        @PathVariable String judgeId,
        @RequestParam(required = false) String q
    ) {
        try {
            return lawyerJudgeService.searchAssignableCasesForJudge(
                UUID.fromString(judgeId),
                q,
                8
            );
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    /** Assign an existing case to a judge, then return to the judge directory. */
    @PostMapping("/lawyers-judges/judges/{judgeId}/assign")
    public String assignCaseToJudge(
        @PathVariable String judgeId,
        @RequestParam String caseId,
        @RequestParam(defaultValue = "true") boolean presiding,
        @RequestParam(required = false) String returnTo,
        RedirectAttributes redirectAttributes,
        HttpServletResponse response,
        Model model
    ) {
        UUID id;
        UUID parsedCaseId;
        try {
            id = UUID.fromString(judgeId);
            parsedCaseId = UUID.fromString(caseId);
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "lawyers");
            return "case-not-found";
        }

        try {
            String caseNumber = lawyerJudgeService.assignCaseToJudge(
                id,
                parsedCaseId,
                presiding
            );
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Case " + caseNumber + " assigned successfully."
            );
        } catch (CaseDetailNotFoundException ex) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Could not assign case — the case or judge no longer exists."
            );
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        // Return to the judge's profile when the assign came from there.
        if ("profile".equals(returnTo)) {
            return "redirect:/lawyers-judges/judges/" + id;
        }
        return "redirect:/lawyers-judges?tab=judge";
    }

    /** Lawyer Profile page — details + involved cases. Viewable with CASE_VIEW. */
    @GetMapping("/lawyers-judges/lawyers/{lawyerId}")
    public String lawyerProfile(
        @PathVariable String lawyerId,
        Model model,
        HttpServletResponse response
    ) {
        UUID id;
        try {
            id = UUID.fromString(lawyerId);
            model.addAttribute("profile", lawyerJudgeService.findLawyerProfile(id));
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "lawyers");
            return "case-not-found";
        }

        model.addAttribute("involvedCases", lawyerJudgeService.findInvolvedCases(id));
        model.addAttribute("activeNav", "lawyers");
        return "lawyer-profile";
    }

    /** Typeahead for the "Assign Case to Lawyer" modal — step 1: pick a case. */
    @GetMapping("/lawyers-judges/lawyers/{lawyerId}/case-search")
    @ResponseBody
    public List<LawyerAssignCaseOption> lawyerCaseSearch(
        @PathVariable String lawyerId,
        @RequestParam(required = false) String q
    ) {
        try {
            UUID.fromString(lawyerId);
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
        return lawyerJudgeService.searchAssignableCasesForLawyer(q, 8);
    }

    /**
     * "Assign Case to Lawyer" modal — step 2: the parties on the selected case,
     * for choosing who the lawyer represents.
     */
    @GetMapping("/lawyers-judges/lawyers/{lawyerId}/case/{caseId}/participants")
    @ResponseBody
    public List<CaseParticipantOption> lawyerCaseParticipants(
        @PathVariable String lawyerId,
        @PathVariable String caseId
    ) {
        try {
            return lawyerJudgeService.findAssignableParticipants(
                UUID.fromString(lawyerId),
                UUID.fromString(caseId)
            );
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    /** Assign a case to a lawyer (as counsel for a party), then return. */
    @PostMapping("/lawyers-judges/lawyers/{lawyerId}/assign")
    public String assignCaseToLawyer(
        @PathVariable String lawyerId,
        @RequestParam String caseId,
        @RequestParam String participantId,
        @RequestParam(required = false) String returnTo,
        RedirectAttributes redirectAttributes,
        HttpServletResponse response,
        Model model
    ) {
        UUID id;
        UUID parsedCaseId;
        UUID parsedParticipantId;
        try {
            id = UUID.fromString(lawyerId);
            parsedCaseId = UUID.fromString(caseId);
            parsedParticipantId = UUID.fromString(participantId);
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "lawyers");
            return "case-not-found";
        }

        try {
            String caseNumber = lawyerJudgeService.assignCaseToLawyer(
                id,
                parsedCaseId,
                parsedParticipantId
            );
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Case " + caseNumber + " assigned successfully."
            );
        } catch (CaseDetailNotFoundException ex) {
            redirectAttributes.addFlashAttribute(
                "errorMessage",
                "Could not assign case — the case, party, or lawyer no longer exists."
            );
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        // Return to the lawyer's profile when the assign came from there.
        if ("profile".equals(returnTo)) {
            return "redirect:/lawyers-judges/lawyers/" + id;
        }
        return "redirect:/lawyers-judges?tab=lawyer";
    }

    /** Edit Lawyer Profile form, prefilled with the lawyer's current details. */
    @GetMapping("/lawyers-judges/lawyers/{lawyerId}/edit")
    public String editLawyerForm(
        @PathVariable String lawyerId,
        Model model,
        HttpServletResponse response
    ) {
        LawyerProfile profile = loadLawyerProfileOr404(lawyerId, model, response);
        if (profile == null) {
            return "case-not-found";
        }
        if (!model.containsAttribute("editLawyerForm")) {
            CreateLawyerForm form = new CreateLawyerForm();
            form.setFirstName(profile.firstName());
            form.setLastName(profile.lastName());
            form.setBarNumber(profile.barNumber());
            form.setFirmName("—".equals(profile.firmOffice()) ? null : profile.firmOffice());
            model.addAttribute("editLawyerForm", form);
        }
        model.addAttribute("profile", profile);
        model.addAttribute("activeNav", "lawyers");
        return "lawyer-edit";
    }

    @PostMapping("/lawyers-judges/lawyers/{lawyerId}/edit")
    public String updateLawyer(
        @PathVariable String lawyerId,
        @Valid @ModelAttribute("editLawyerForm") CreateLawyerForm form,
        BindingResult bindingResult,
        Model model,
        RedirectAttributes redirectAttributes,
        HttpServletResponse response
    ) {
        LawyerProfile profile = loadLawyerProfileOr404(lawyerId, model, response);
        if (profile == null) {
            return "case-not-found";
        }
        if (!bindingResult.hasErrors()) {
            try {
                lawyerJudgeService.updateLawyer(UUID.fromString(lawyerId), form);
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Lawyer profile updated successfully."
                );
                return "redirect:/lawyers-judges/lawyers/" + lawyerId;
            } catch (LawyerJudgeException ex) {
                rejectFormError(bindingResult, ex, "lawyer.update.failed");
            }
        }
        model.addAttribute("profile", profile);
        model.addAttribute("activeNav", "lawyers");
        return "lawyer-edit";
    }

    /** Edit Judge Profile form, prefilled with the judge's current details. */
    @GetMapping("/lawyers-judges/judges/{judgeId}/edit")
    public String editJudgeForm(
        @PathVariable String judgeId,
        Model model,
        HttpServletResponse response
    ) {
        JudgeProfile profile = loadJudgeProfileOr404(judgeId, model, response);
        if (profile == null) {
            return "case-not-found";
        }
        if (!model.containsAttribute("editJudgeForm")) {
            CreateJudgeForm form = new CreateJudgeForm();
            form.setFirstName(profile.firstName());
            form.setLastName(profile.lastName());
            form.setBarNumber(profile.barNumber());
            model.addAttribute("editJudgeForm", form);
        }
        model.addAttribute("profile", profile);
        model.addAttribute("activeNav", "lawyers");
        return "judge-edit";
    }

    @PostMapping("/lawyers-judges/judges/{judgeId}/edit")
    public String updateJudge(
        @PathVariable String judgeId,
        @Valid @ModelAttribute("editJudgeForm") CreateJudgeForm form,
        BindingResult bindingResult,
        Model model,
        RedirectAttributes redirectAttributes,
        HttpServletResponse response
    ) {
        JudgeProfile profile = loadJudgeProfileOr404(judgeId, model, response);
        if (profile == null) {
            return "case-not-found";
        }
        if (!bindingResult.hasErrors()) {
            try {
                lawyerJudgeService.updateJudge(UUID.fromString(judgeId), form);
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Judge profile updated successfully."
                );
                return "redirect:/lawyers-judges/judges/" + judgeId;
            } catch (LawyerJudgeException ex) {
                rejectFormError(bindingResult, ex, "judge.update.failed");
            }
        }
        model.addAttribute("profile", profile);
        model.addAttribute("activeNav", "lawyers");
        return "judge-edit";
    }

    private LawyerProfile loadLawyerProfileOr404(
        String lawyerId,
        Model model,
        HttpServletResponse response
    ) {
        try {
            return lawyerJudgeService.findLawyerProfile(UUID.fromString(lawyerId));
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "lawyers");
            return null;
        }
    }

    private JudgeProfile loadJudgeProfileOr404(
        String judgeId,
        Model model,
        HttpServletResponse response
    ) {
        try {
            return lawyerJudgeService.findJudgeProfile(UUID.fromString(judgeId));
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "lawyers");
            return null;
        }
    }

    private void rejectFormError(
        BindingResult bindingResult,
        LawyerJudgeException ex,
        String code
    ) {
        if (ex.getFieldName() == null) {
            bindingResult.reject(code, ex.getMessage());
        } else {
            bindingResult.rejectValue(ex.getFieldName(), code, ex.getMessage());
        }
    }
}
