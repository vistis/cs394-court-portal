package kh.edu.paragoniu.court_portal.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import kh.edu.paragoniu.court_portal.cases.CaseDetailNotFoundException;
import kh.edu.paragoniu.court_portal.cases.CaseService;
import kh.edu.paragoniu.court_portal.cases.HearingScheduleException;
import kh.edu.paragoniu.court_portal.cases.HearingService;
import kh.edu.paragoniu.court_portal.cases.RescheduleHearingForm;
import kh.edu.paragoniu.court_portal.cases.ScheduleHearingForm;
import kh.edu.paragoniu.court_portal.security.GreffierUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class HearingController {

    private static final String FILTER_UPCOMING_PAST = "upcoming-past";
    private static final String FILTER_UPCOMING = "upcoming";
    private static final String FILTER_PAST = "past";

    private final CaseService caseService;
    private final HearingService hearingService;

    @GetMapping("/cases/{caseId}/hearings")
    public String hearings(
        @PathVariable String caseId,
        @RequestParam(defaultValue = FILTER_UPCOMING_PAST) String filter,
        Model model,
        HttpServletResponse response
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            addHearingListModel(model, parsedCaseId, normalizeFilter(filter));
            return "case-hearings";
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    @GetMapping("/cases/{caseId}/hearings/new")
    public String newHearing(
        @PathVariable String caseId,
        Model model,
        HttpServletResponse response
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            if (!model.containsAttribute("scheduleHearingForm")) {
                model.addAttribute(
                    "scheduleHearingForm",
                    new ScheduleHearingForm()
                );
            }
            addHearingFormModel(model, parsedCaseId);
            return "hearing-form";
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    @PostMapping("/cases/{caseId}/hearings")
    public String scheduleHearing(
        @PathVariable String caseId,
        @Valid @ModelAttribute("scheduleHearingForm") ScheduleHearingForm form,
        BindingResult bindingResult,
        Model model,
        RedirectAttributes redirectAttributes,
        HttpServletResponse response,
        @AuthenticationPrincipal GreffierUserDetails user
    ) {
        UUID parsedCaseId;
        try {
            parsedCaseId = UUID.fromString(caseId);
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }

        if (!bindingResult.hasErrors()) {
            try {
                hearingService.scheduleHearing(
                    parsedCaseId,
                    form,
                    user == null ? null : user.getUserId()
                );
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Hearing scheduled successfully."
                );
                return "redirect:/cases/" + parsedCaseId + "/hearings";
            } catch (CaseDetailNotFoundException ex) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                model.addAttribute("activeNav", "cases");
                return "case-not-found";
            } catch (HearingScheduleException ex) {
                if (ex.getFieldName() == null) {
                    bindingResult.reject("hearing.schedule.failed", ex.getMessage());
                } else {
                    bindingResult.rejectValue(
                        ex.getFieldName(),
                        "hearing.schedule.failed",
                        ex.getMessage()
                    );
                }
            }
        }

        addHearingFormModel(model, parsedCaseId);
        return "hearing-form";
    }

    @GetMapping("/cases/{caseId}/hearings/{hearingId}/reschedule")
    public String rescheduleHearingForm(
        @PathVariable String caseId,
        @PathVariable String hearingId,
        Model model,
        HttpServletResponse response
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            UUID parsedHearingId = UUID.fromString(hearingId);
            if (!model.containsAttribute("rescheduleHearingForm")) {
                RescheduleHearingForm form = new RescheduleHearingForm();
                form.setCourtroomId(
                    hearingService
                        .findRescheduleView(parsedCaseId, parsedHearingId)
                        .courtroomId()
                );
                model.addAttribute("rescheduleHearingForm", form);
            }
            addRescheduleModel(model, parsedCaseId, parsedHearingId);
            return "hearing-reschedule-form";
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    @PostMapping("/cases/{caseId}/hearings/{hearingId}/reschedule")
    public String rescheduleHearing(
        @PathVariable String caseId,
        @PathVariable String hearingId,
        @Valid @ModelAttribute("rescheduleHearingForm") RescheduleHearingForm form,
        BindingResult bindingResult,
        Model model,
        RedirectAttributes redirectAttributes,
        HttpServletResponse response,
        @AuthenticationPrincipal GreffierUserDetails user
    ) {
        UUID parsedCaseId;
        UUID parsedHearingId;
        try {
            parsedCaseId = UUID.fromString(caseId);
            parsedHearingId = UUID.fromString(hearingId);
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }

        if (!bindingResult.hasErrors()) {
            try {
                hearingService.rescheduleHearing(
                    parsedCaseId,
                    parsedHearingId,
                    form,
                    user == null ? null : user.getUserId()
                );
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Hearing rescheduled successfully."
                );
                return "redirect:/cases/" + parsedCaseId + "/hearings";
            } catch (CaseDetailNotFoundException ex) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                model.addAttribute("activeNav", "cases");
                return "case-not-found";
            } catch (HearingScheduleException ex) {
                if (ex.getFieldName() == null) {
                    bindingResult.reject("hearing.reschedule.failed", ex.getMessage());
                } else {
                    bindingResult.rejectValue(
                        ex.getFieldName(),
                        "hearing.reschedule.failed",
                        ex.getMessage()
                    );
                }
            }
        }

        addRescheduleModel(model, parsedCaseId, parsedHearingId);
        return "hearing-reschedule-form";
    }

    private void addHearingListModel(
        Model model,
        UUID caseId,
        String selectedFilter
    ) {
        model.addAttribute("caseDetail", caseService.findDetail(caseId));
        model.addAttribute("upcomingHearings", hearingService.findUpcomingHearings(caseId));
        model.addAttribute("pastHearings", hearingService.findPastHearings(caseId));
        model.addAttribute("selectedHearingFilter", selectedFilter);
        model.addAttribute("showUpcoming", !FILTER_PAST.equals(selectedFilter));
        model.addAttribute("showPast", !FILTER_UPCOMING.equals(selectedFilter));
        model.addAttribute("successMessage", model.asMap().get("successMessage"));
        model.addAttribute("activeNav", "cases");
    }

    private void addHearingFormModel(Model model, UUID caseId) {
        model.addAttribute("caseDetail", caseService.findDetail(caseId));
        model.addAttribute("hearingTypes", hearingService.findHearingTypeOptions());
        model.addAttribute("courtrooms", hearingService.findCourtroomOptions());
        model.addAttribute("activeNav", "cases");
    }

    private void addRescheduleModel(Model model, UUID caseId, UUID hearingId) {
        addHearingListModel(model, caseId, FILTER_UPCOMING_PAST);
        model.addAttribute(
            "hearingReschedule",
            hearingService.findRescheduleView(caseId, hearingId)
        );
        model.addAttribute("courtrooms", hearingService.findCourtroomOptions());
    }

    private String normalizeFilter(String filter) {
        if (FILTER_UPCOMING.equals(filter) || FILTER_PAST.equals(filter)) {
            return filter;
        }
        return FILTER_UPCOMING_PAST;
    }
}
