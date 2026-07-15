package kh.edu.paragoniu.court_portal.web;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.Valid;
import kh.edu.paragoniu.court_portal.cases.CaseCreationException;
import kh.edu.paragoniu.court_portal.cases.CaseDetailNotFoundException;
import kh.edu.paragoniu.court_portal.cases.CaseRow;
import kh.edu.paragoniu.court_portal.cases.CaseService;
import kh.edu.paragoniu.court_portal.cases.CreateCaseForm;
import kh.edu.paragoniu.court_portal.cases.CreateDocketEntryForm;
import kh.edu.paragoniu.court_portal.cases.DocketEntryException;
import kh.edu.paragoniu.court_portal.cases.StatusUpdateException;
import kh.edu.paragoniu.court_portal.cases.UpdateCaseStatusForm;
import kh.edu.paragoniu.court_portal.security.GreffierUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class CaseController {

    private static final int PAGE_SIZE = 8;
    private static final int DOCKET_PAGE_SIZE = 8;

    private final CaseService caseService;

    @GetMapping("/cases/new")
    public String newCase(Model model) {
        if (!model.containsAttribute("createCaseForm")) {
            model.addAttribute("createCaseForm", new CreateCaseForm());
        }
        addCaseFormOptions(model);
        model.addAttribute("activeNav", "cases");
        return "case-form";
    }

    @GetMapping("/cases")
    public String cases(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Integer classificationId,
        @RequestParam(required = false) Integer statusId,
        @RequestParam(required = false) @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate fromDate,
        @RequestParam(required = false) @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate toDate,
        @RequestParam(defaultValue = "0") int page,
        Model model
    ) {
        Pageable pageable = PageRequest.of(
            Math.max(page, 0),
            PAGE_SIZE,
            Sort.by(Sort.Direction.DESC, "filedAt")
        );
        Page<CaseRow> result;
        try {
            result = caseService.search(
                query,
                classificationId,
                statusId,
                fromDate,
                toDate,
                pageable
            );
        } catch (IllegalArgumentException ex) {
            result = Page.empty(pageable);
            model.addAttribute("filterError", ex.getMessage());
        }

        model.addAttribute("page", result);
        model.addAttribute("statuses", caseService.findStatusOptions());
        model.addAttribute("classifications", caseService.findClassificationOptions());
        model.addAttribute("query", query);
        model.addAttribute("selectedClassificationId", classificationId);
        model.addAttribute("selectedStatusId", statusId);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("activeNav", "cases");
        return "cases";
    }

    @GetMapping("/cases/{caseId}")
    public String caseDetail(
        @PathVariable String caseId,
        Model model,
        HttpServletResponse response
    ) {
        try {
            model.addAttribute(
                "caseDetail",
                caseService.findDetail(UUID.fromString(caseId))
            );
            model.addAttribute("successMessage", model.asMap().get("successMessage"));
            model.addAttribute("activeNav", "cases");
            return "case-detail";
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    @GetMapping("/cases/{caseId}/status")
    public String updateStatusForm(
        @PathVariable String caseId,
        Model model,
        HttpServletResponse response
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            if (!model.containsAttribute("updateCaseStatusForm")) {
                UpdateCaseStatusForm form = new UpdateCaseStatusForm();
                form.setStatusId(caseService.findStatusSnapshot(parsedCaseId).statusId());
                model.addAttribute("updateCaseStatusForm", form);
            }
            addStatusFormModel(model, parsedCaseId);
            return "case-status-form";
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    @GetMapping("/cases/{caseId}/docket")
    public String docketSheet(
        @PathVariable String caseId,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String activityType,
        @RequestParam(defaultValue = "0") int page,
        Model model,
        HttpServletResponse response
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            addDocketSheetModel(
                model,
                parsedCaseId,
                query,
                activityType,
                Math.max(page, 0)
            );
            return "case-docket";
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    @GetMapping("/cases/{caseId}/docket/new")
    public String newDocketEntry(
        @PathVariable String caseId,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String activityType,
        @RequestParam(defaultValue = "0") int page,
        Model model,
        HttpServletResponse response,
        @AuthenticationPrincipal GreffierUserDetails user
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            if (!model.containsAttribute("createDocketEntryForm")) {
                model.addAttribute(
                    "createDocketEntryForm",
                    new CreateDocketEntryForm()
                );
            }
            addDocketSheetModel(
                model,
                parsedCaseId,
                query,
                activityType,
                Math.max(page, 0)
            );
            addDocketFormModel(model, user);
            return "case-docket-form";
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    @PostMapping("/cases/{caseId}/docket")
    public String createDocketEntry(
        @PathVariable String caseId,
        @Valid @ModelAttribute("createDocketEntryForm") CreateDocketEntryForm form,
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
                caseService.createDocketEntry(
                    parsedCaseId,
                    form,
                    user == null ? null : user.getUserId()
                );
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Docket entry added successfully."
                );
                return "redirect:/cases/" + parsedCaseId + "/docket";
            } catch (CaseDetailNotFoundException ex) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                model.addAttribute("activeNav", "cases");
                return "case-not-found";
            } catch (DocketEntryException ex) {
                if (ex.getFieldName() == null) {
                    bindingResult.reject("docket.create.failed", ex.getMessage());
                } else {
                    bindingResult.rejectValue(
                        ex.getFieldName(),
                        "docket.create.failed",
                        ex.getMessage()
                    );
                }
            }
        }

        addDocketSheetModel(model, parsedCaseId, null, null, 0);
        addDocketFormModel(model, user);
        return "case-docket-form";
    }

    @PostMapping("/cases/{caseId}/status")
    public String updateStatus(
        @PathVariable String caseId,
        @Valid @ModelAttribute("updateCaseStatusForm") UpdateCaseStatusForm form,
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
                caseService.updateStatus(
                    parsedCaseId,
                    form.getStatusId(),
                    user == null ? null : user.getUserId()
                );
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Case status updated successfully."
                );
                return "redirect:/cases/" + parsedCaseId;
            } catch (CaseDetailNotFoundException ex) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                model.addAttribute("activeNav", "cases");
                return "case-not-found";
            } catch (StatusUpdateException ex) {
                if (ex.getFieldName() == null) {
                    bindingResult.reject("case.status.failed", ex.getMessage());
                } else {
                    bindingResult.rejectValue(
                        ex.getFieldName(),
                        "case.status.failed",
                        ex.getMessage()
                    );
                }
            }
        }

        addStatusFormModel(model, parsedCaseId);
        return "case-status-form";
    }

    @PostMapping("/cases")
    public String createCase(
        @Valid @ModelAttribute("createCaseForm") CreateCaseForm form,
        BindingResult bindingResult,
        Model model,
        RedirectAttributes redirectAttributes,
        @AuthenticationPrincipal GreffierUserDetails user
    ) {
        if (!bindingResult.hasErrors()) {
            try {
                caseService.createCase(
                    form,
                    user == null ? null : user.getUserId()
                );
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Case registered successfully."
                );
                return "redirect:/cases";
            } catch (CaseCreationException ex) {
                if (ex.getFieldName() == null) {
                    bindingResult.reject("case.create.failed", ex.getMessage());
                } else {
                    bindingResult.rejectValue(
                        ex.getFieldName(),
                        "case.create.failed",
                        ex.getMessage()
                    );
                }
            }
        }

        addCaseFormOptions(model);
        model.addAttribute("activeNav", "cases");
        return "case-form";
    }

    private void addCaseFormOptions(Model model) {
        model.addAttribute("classifications", caseService.findClassificationOptions());
        model.addAttribute("judges", caseService.findActiveJudgeOptions());
    }

    private void addStatusFormModel(Model model, UUID caseId) {
        model.addAttribute("caseDetail", caseService.findDetail(caseId));
        model.addAttribute("caseStatus", caseService.findStatusSnapshot(caseId));
        model.addAttribute("statuses", caseService.findStatusOptions());
        model.addAttribute("activeNav", "cases");
    }

    private void addDocketSheetModel(
        Model model,
        UUID caseId,
        String query,
        String activityType,
        int page
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), DOCKET_PAGE_SIZE);
        model.addAttribute("caseDetail", caseService.findDetail(caseId));
        model.addAttribute(
            "docketPage",
            caseService.findDocketEntries(caseId, query, activityType, pageable)
        );
        model.addAttribute(
            "activityTypes",
            caseService.findDocketActivityTypeOptions(caseId)
        );
        model.addAttribute("query", query);
        model.addAttribute("selectedActivityType", activityType);
        model.addAttribute("successMessage", model.asMap().get("successMessage"));
        model.addAttribute("activeNav", "cases");
    }

    private void addDocketFormModel(
        Model model,
        GreffierUserDetails user
    ) {
        model.addAttribute(
            "performedByDisplay",
            user == null ? "Current user" : user.getDisplayName()
        );
        model.addAttribute(
            "entryTimestampDisplay",
            caseService.formatCurrentTimestamp()
        );
    }
}
