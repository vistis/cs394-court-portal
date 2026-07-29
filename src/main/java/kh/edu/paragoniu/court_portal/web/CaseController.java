package kh.edu.paragoniu.court_portal.web;

import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.Valid;
import java.util.List;
import kh.edu.paragoniu.court_portal.cases.AssignPersonOption;
import kh.edu.paragoniu.court_portal.cases.CaseCreationException;
import kh.edu.paragoniu.court_portal.cases.CaseDetailNotFoundException;
import kh.edu.paragoniu.court_portal.cases.CaseRow;
import kh.edu.paragoniu.court_portal.cases.CaseService;
import kh.edu.paragoniu.court_portal.greffier.GreffierService;
import kh.edu.paragoniu.court_portal.legal.LawyerJudgeService;
import kh.edu.paragoniu.court_portal.cases.CreateDispositionForm;
import kh.edu.paragoniu.court_portal.cases.CreateCaseForm;
import kh.edu.paragoniu.court_portal.cases.CreateDocketEntryForm;
import kh.edu.paragoniu.court_portal.cases.DocketEntryException;
import kh.edu.paragoniu.court_portal.cases.DispositionException;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class CaseController {

    private static final int PAGE_SIZE = 8;
    private static final int DOCKET_PAGE_SIZE = 8;

    private final CaseService caseService;
    private final LawyerJudgeService lawyerJudgeService;
    private final GreffierService greffierService;

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

    /** Judge suggestions for the quick-assign popup on the Case Detail page. */
    @GetMapping("/cases/{caseId}/judge-search")
    @ResponseBody
    public List<AssignPersonOption> judgeSearch(
        @PathVariable String caseId,
        @RequestParam(required = false, name = "q") String query
    ) {
        try {
            return lawyerJudgeService.searchAssignableJudgesForCase(
                UUID.fromString(caseId),
                query,
                20
            );
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    /** Quick-assign a judge to this case from the Case Detail page. */
    @PostMapping("/cases/{caseId}/assign-judge")
    public String assignJudge(
        @PathVariable String caseId,
        @RequestParam String judgeId,
        @RequestParam(defaultValue = "true") boolean presiding,
        Model model,
        RedirectAttributes redirectAttributes,
        HttpServletResponse response
    ) {
        UUID parsedCaseId;
        UUID parsedJudgeId;
        try {
            parsedCaseId = UUID.fromString(caseId);
            parsedJudgeId = UUID.fromString(judgeId);
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }

        try {
            String caseNumber = lawyerJudgeService.assignCaseToJudge(
                parsedJudgeId,
                parsedCaseId,
                presiding
            );
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Judge assigned to case " + caseNumber + "."
            );
        } catch (CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/cases/" + parsedCaseId;
    }

    /** Greffier suggestions for the quick-assign popup on the Case Detail page. */
    @GetMapping("/cases/{caseId}/greffier-search")
    @ResponseBody
    public List<AssignPersonOption> greffierSearch(
        @PathVariable String caseId,
        @RequestParam(required = false, name = "q") String query
    ) {
        try {
            return greffierService.searchAssignableGreffiersForCase(
                UUID.fromString(caseId),
                query,
                20
            );
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    /** Quick-assign a greffier to this case from the Case Detail page. */
    @PostMapping("/cases/{caseId}/assign-greffier")
    public String assignGreffier(
        @PathVariable String caseId,
        @RequestParam String greffierId,
        Model model,
        RedirectAttributes redirectAttributes,
        HttpServletResponse response,
        @AuthenticationPrincipal GreffierUserDetails user
    ) {
        UUID parsedCaseId;
        UUID parsedGreffierId;
        try {
            parsedCaseId = UUID.fromString(caseId);
            parsedGreffierId = UUID.fromString(greffierId);
        } catch (IllegalArgumentException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }

        try {
            String caseNumber = greffierService.assignCase(
                parsedGreffierId,
                parsedCaseId,
                user == null ? null : user.getUserId()
            );
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Greffier assigned to case " + caseNumber + "."
            );
        } catch (CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/cases/" + parsedCaseId;
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

    @GetMapping("/cases/{caseId}/disposition")
    public String disposition(
        @PathVariable String caseId,
        Model model,
        HttpServletResponse response
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            addDispositionModel(model, parsedCaseId);
            return "case-disposition";
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    @GetMapping("/cases/{caseId}/disposition/new")
    public String newDisposition(
        @PathVariable String caseId,
        Model model,
        HttpServletResponse response
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            if (!model.containsAttribute("createDispositionForm")) {
                model.addAttribute(
                    "createDispositionForm",
                    new CreateDispositionForm()
                );
            }
            addDispositionFormModel(model, parsedCaseId);
            return "case-disposition-form";
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    @PostMapping("/cases/{caseId}/disposition")
    public String createDisposition(
        @PathVariable String caseId,
        @Valid @ModelAttribute("createDispositionForm") CreateDispositionForm form,
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
                caseService.createDisposition(
                    parsedCaseId,
                    form,
                    user == null ? null : user.getUserId()
                );
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Disposition recorded successfully."
                );
                return "redirect:/cases/" + parsedCaseId + "/disposition";
            } catch (CaseDetailNotFoundException ex) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                model.addAttribute("activeNav", "cases");
                return "case-not-found";
            } catch (DispositionException ex) {
                if (ex.getFieldName() == null) {
                    bindingResult.reject("disposition.create.failed", ex.getMessage());
                } else {
                    bindingResult.rejectValue(
                        ex.getFieldName(),
                        "disposition.create.failed",
                        ex.getMessage()
                    );
                }
            }
        }

        addDispositionFormModel(model, parsedCaseId);
        return "case-disposition-form";
    }

    @GetMapping("/cases/{caseId}/appeal/new")
    public String newAppeal(
        @PathVariable String caseId,
        Model model,
        HttpServletResponse response
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            addAppealFormModel(model, parsedCaseId);
            return "case-appeal-form";
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    @PostMapping("/cases/{caseId}/appeal")
    public String createAppeal(
        @PathVariable String caseId,
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

        try {
            UUID appellateCaseId = caseService.initiateAppeal(
                parsedCaseId,
                user == null ? null : user.getUserId()
            );
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Appellate case created successfully."
            );
            return "redirect:/cases/" + appellateCaseId;
        } catch (CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        } catch (DispositionException ex) {
            model.addAttribute("appealError", ex.getMessage());
            addAppealFormModel(model, parsedCaseId);
            return "case-appeal-form";
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

    private void addDispositionModel(Model model, UUID caseId) {
        model.addAttribute("caseDetail", caseService.findDetail(caseId));
        model.addAttribute(
            "dispositionTab",
            caseService.findDispositionTab(caseId)
        );
        model.addAttribute("successMessage", model.asMap().get("successMessage"));
        model.addAttribute("activeNav", "cases");
    }

    private void addDispositionFormModel(Model model, UUID caseId) {
        addDispositionModel(model, caseId);
        model.addAttribute(
            "outcomes",
            caseService.findDispositionOutcomeOptions()
        );
    }

    private void addAppealFormModel(Model model, UUID caseId) {
        addDispositionModel(model, caseId);
    }
}
