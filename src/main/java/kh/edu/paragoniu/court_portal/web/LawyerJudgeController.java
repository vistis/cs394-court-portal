package kh.edu.paragoniu.court_portal.web;

import jakarta.validation.Valid;
import kh.edu.paragoniu.court_portal.legal.CreateJudgeForm;
import kh.edu.paragoniu.court_portal.legal.CreateLawyerForm;
import kh.edu.paragoniu.court_portal.legal.LawyerJudgeException;
import kh.edu.paragoniu.court_portal.legal.LawyerJudgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
}
