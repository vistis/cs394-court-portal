package kh.edu.paragoniu.court_portal.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import kh.edu.paragoniu.court_portal.cases.CaseDetailNotFoundException;
import kh.edu.paragoniu.court_portal.cases.HearingScheduleException;
import kh.edu.paragoniu.court_portal.cases.HearingService;
import kh.edu.paragoniu.court_portal.cases.ScheduleHearingForm;
import kh.edu.paragoniu.court_portal.hearings.CaseLookupResult;
import kh.edu.paragoniu.court_portal.hearings.CaseLookupService;
import kh.edu.paragoniu.court_portal.hearings.GlobalHearingForm;
import kh.edu.paragoniu.court_portal.hearings.HearingDetailView;
import kh.edu.paragoniu.court_portal.hearings.HearingScheduleRow;
import kh.edu.paragoniu.court_portal.hearings.HearingScheduleService;
import kh.edu.paragoniu.court_portal.security.GreffierUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class HearingScheduleController {

    private static final int PAGE_SIZE = 8;

    private final HearingScheduleService hearingScheduleService;
    private final HearingService hearingService;
    private final CaseLookupService caseLookupService;

    @GetMapping("/hearings")
    public String hearings(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Integer hearingTypeId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate fromDate,
        @RequestParam(required = false) @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate toDate,
        @RequestParam(defaultValue = "0") int page,
        Model model
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE);

        Page<HearingScheduleRow> result;
        try {
            result = hearingScheduleService.search(
                query,
                hearingTypeId,
                status,
                fromDate,
                toDate,
                pageable
            );
        } catch (IllegalArgumentException ex) {
            result = Page.empty(pageable);
            model.addAttribute("filterError", ex.getMessage());
        }

        model.addAttribute("page", result);
        model.addAttribute("hearingTypes", hearingScheduleService.hearingTypeOptions());
        model.addAttribute("statuses", hearingScheduleService.statusOptions());
        model.addAttribute("query", query);
        model.addAttribute("selectedHearingTypeId", hearingTypeId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("activeNav", "hearings");
        return "hearings";
    }

    @GetMapping("/hearings/{hearingId}")
    public String hearingDetail(
        @org.springframework.web.bind.annotation.PathVariable String hearingId,
        Model model,
        HttpServletResponse response
    ) {
        try {
            HearingDetailView hearing = hearingScheduleService.findDetail(
                UUID.fromString(hearingId)
            );
            model.addAttribute("hearing", hearing);
            model.addAttribute("activeNav", "hearings");
            return "hearing-detail";
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "hearings");
            return "case-not-found";
        }
    }

    @GetMapping("/hearings/new")
    public String newHearing(Model model) {
        if (!model.containsAttribute("globalHearingForm")) {
            model.addAttribute("globalHearingForm", new GlobalHearingForm());
        }
        addScheduleOptions(model);
        return "hearing-schedule-form";
    }

    @PostMapping("/hearings")
    public String scheduleHearing(
        @Valid @ModelAttribute("globalHearingForm") GlobalHearingForm form,
        BindingResult binding,
        @AuthenticationPrincipal GreffierUserDetails user,
        RedirectAttributes redirectAttributes,
        Model model
    ) {
        UUID caseId = null;
        if (form.getCaseId() != null && !form.getCaseId().isBlank()) {
            try {
                caseId = UUID.fromString(form.getCaseId());
            } catch (IllegalArgumentException ex) {
                binding.rejectValue(
                    "caseId",
                    "case.invalid",
                    "Select a valid case from the suggestions."
                );
            }
        }

        if (!binding.hasErrors() && caseId != null) {
            ScheduleHearingForm scheduleForm = new ScheduleHearingForm();
            scheduleForm.setHearingTypeId(form.getHearingTypeId());
            scheduleForm.setCourtroomId(form.getCourtroomId());
            scheduleForm.setStartAt(form.getStartAt());
            scheduleForm.setEndAt(form.getEndAt());
            try {
                hearingService.scheduleHearing(
                    caseId,
                    scheduleForm,
                    user == null ? null : user.getUserId()
                );
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Hearing scheduled successfully."
                );
                return "redirect:/hearings";
            } catch (CaseDetailNotFoundException ex) {
                binding.rejectValue(
                    "caseId",
                    "case.notfound",
                    "Selected case was not found."
                );
            } catch (HearingScheduleException ex) {
                if (ex.getFieldName() == null) {
                    binding.reject("hearing.schedule.failed", ex.getMessage());
                } else {
                    binding.rejectValue(
                        ex.getFieldName(),
                        "hearing.schedule.failed",
                        ex.getMessage()
                    );
                }
            }
        }

        addScheduleOptions(model);
        return "hearing-schedule-form";
    }

    @GetMapping("/hearings/case-lookup")
    @ResponseBody
    public List<CaseLookupResult> caseLookup(
        @RequestParam(required = false) String q
    ) {
        return caseLookupService.lookup(q, 8);
    }

    private void addScheduleOptions(Model model) {
        model.addAttribute("hearingTypes", hearingService.findHearingTypeOptions());
        model.addAttribute("courtrooms", hearingService.findCourtroomOptions());
        model.addAttribute("activeNav", "hearings");
    }
}
