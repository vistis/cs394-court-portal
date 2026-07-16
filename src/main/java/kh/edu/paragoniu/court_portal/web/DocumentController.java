package kh.edu.paragoniu.court_portal.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import kh.edu.paragoniu.court_portal.cases.CaseDetailNotFoundException;
import kh.edu.paragoniu.court_portal.cases.CaseService;
import kh.edu.paragoniu.court_portal.cases.DocumentException;
import kh.edu.paragoniu.court_portal.cases.DocumentService;
import kh.edu.paragoniu.court_portal.cases.UploadDocumentForm;
import kh.edu.paragoniu.court_portal.cases.UpdateMotionStatusForm;
import kh.edu.paragoniu.court_portal.security.GreffierUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
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
public class DocumentController {

    private static final int DOCUMENT_PAGE_SIZE = 8;

    private final CaseService caseService;
    private final DocumentService documentService;

    @GetMapping("/cases/{caseId}/documents")
    public String documents(
        @PathVariable String caseId,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String documentType,
        @RequestParam(required = false) @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate uploadedDate,
        @RequestParam(defaultValue = "0") int page,
        Model model,
        HttpServletResponse response
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            addDocumentListModel(
                model,
                parsedCaseId,
                query,
                documentType,
                uploadedDate,
                Math.max(page, 0)
            );
            return "case-documents";
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    @GetMapping("/cases/{caseId}/documents/new")
    public String newDocument(
        @PathVariable String caseId,
        Model model,
        HttpServletResponse response,
        @AuthenticationPrincipal GreffierUserDetails user
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            if (!model.containsAttribute("uploadDocumentForm")) {
                model.addAttribute("uploadDocumentForm", new UploadDocumentForm());
            }
            addDocumentFormModel(model, parsedCaseId, user);
            return "case-document-form";
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    @PostMapping("/cases/{caseId}/documents")
    public String createDocument(
        @PathVariable String caseId,
        @Valid @ModelAttribute("uploadDocumentForm") UploadDocumentForm form,
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
                documentService.createDocument(
                    parsedCaseId,
                    form,
                    user == null ? null : user.getUserId()
                );
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Document uploaded successfully."
                );
                return "redirect:/cases/" + parsedCaseId + "/documents";
            } catch (CaseDetailNotFoundException ex) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                model.addAttribute("activeNav", "cases");
                return "case-not-found";
            } catch (DocumentException ex) {
                if (ex.getFieldName() == null) {
                    bindingResult.reject("document.upload.failed", ex.getMessage());
                } else {
                    bindingResult.rejectValue(
                        ex.getFieldName(),
                        "document.upload.failed",
                        ex.getMessage()
                    );
                }
            }
        }

        addDocumentFormModel(model, parsedCaseId, user);
        return "case-document-form";
    }

    @GetMapping("/cases/{caseId}/documents/{documentId}")
    public String documentViewer(
        @PathVariable String caseId,
        @PathVariable String documentId,
        Model model,
        HttpServletResponse response
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            addDocumentViewerModel(model, parsedCaseId, documentId);
            return "case-document-view";
        } catch (
            IllegalArgumentException
            | CaseDetailNotFoundException
            | DocumentException ex
        ) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    @GetMapping("/cases/{caseId}/documents/{documentId}/motion-status")
    public String motionStatusForm(
        @PathVariable String caseId,
        @PathVariable String documentId,
        Model model,
        HttpServletResponse response
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            addDocumentViewerModel(model, parsedCaseId, documentId);
            if (!model.containsAttribute("updateMotionStatusForm")) {
                model.addAttribute(
                    "updateMotionStatusForm",
                    documentService.findMotionStatusForm(parsedCaseId, documentId)
                );
            }
            model.addAttribute(
                "motionStatuses",
                documentService.findMotionStatuses()
            );
            model.addAttribute("judgeOptions", documentService.findJudgeOptions());
            model.addAttribute("showMotionStatusModal", true);
            return "case-document-view";
        } catch (
            IllegalArgumentException
            | CaseDetailNotFoundException
            | DocumentException ex
        ) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    @PostMapping("/cases/{caseId}/documents/{documentId}/motion-status")
    public String updateMotionStatus(
        @PathVariable String caseId,
        @PathVariable String documentId,
        @Valid @ModelAttribute("updateMotionStatusForm") UpdateMotionStatusForm form,
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
                documentService.updateMotionStatus(
                    parsedCaseId,
                    documentId,
                    form,
                    user == null ? null : user.getUserId()
                );
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Motion status updated successfully."
                );
                return "redirect:/cases/" + parsedCaseId + "/documents/" + documentId;
            } catch (CaseDetailNotFoundException ex) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                model.addAttribute("activeNav", "cases");
                return "case-not-found";
            } catch (DocumentException ex) {
                if (ex.getFieldName() == null) {
                    bindingResult.reject(
                        "motion.status.update.failed",
                        ex.getMessage()
                    );
                } else {
                    bindingResult.rejectValue(
                        ex.getFieldName(),
                        "motion.status.update.failed",
                        ex.getMessage()
                    );
                }
            }
        }

        addDocumentViewerModel(model, parsedCaseId, documentId);
        model.addAttribute("motionStatuses", documentService.findMotionStatuses());
        model.addAttribute("judgeOptions", documentService.findJudgeOptions());
        model.addAttribute("showMotionStatusModal", true);
        return "case-document-view";
    }

    @GetMapping("/cases/{caseId}/documents/{documentId}/download")
    public String downloadDocument(
        @PathVariable String caseId,
        @PathVariable String documentId,
        HttpServletResponse response,
        Model model
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            String url = documentService.findDownloadUrl(parsedCaseId, documentId);
            if (url == null || url.isBlank()) {
                throw new DocumentException(null, "Document file is unavailable.");
            }
            return "redirect:" + url;
        } catch (
            IllegalArgumentException
            | CaseDetailNotFoundException
            | DocumentException ex
        ) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    private void addDocumentListModel(
        Model model,
        UUID caseId,
        String query,
        String documentType,
        LocalDate uploadedDate,
        int page
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), DOCUMENT_PAGE_SIZE);
        model.addAttribute("caseDetail", caseService.findDetail(caseId));
        model.addAttribute(
            "documentPage",
            documentService.findDocuments(
                caseId,
                query,
                documentType,
                uploadedDate,
                pageable
            )
        );
        model.addAttribute("documentTypes", documentService.findDocumentTypes());
        model.addAttribute("documentCount", documentService.countDocuments(caseId));
        model.addAttribute("query", query);
        model.addAttribute("selectedDocumentType", documentType);
        model.addAttribute("uploadedDate", uploadedDate);
        model.addAttribute("successMessage", model.asMap().get("successMessage"));
        model.addAttribute("activeNav", "cases");
    }

    private void addDocumentViewerModel(
        Model model,
        UUID caseId,
        String documentId
    ) {
        model.addAttribute("caseDetail", caseService.findDetail(caseId));
        model.addAttribute(
            "documentDetail",
            documentService.findDocument(caseId, documentId)
        );
        model.addAttribute("successMessage", model.asMap().get("successMessage"));
        model.addAttribute("activeNav", "cases");
    }

    private void addDocumentFormModel(
        Model model,
        UUID caseId,
        GreffierUserDetails user
    ) {
        model.addAttribute("caseDetail", caseService.findDetail(caseId));
        model.addAttribute("documentTypes", documentService.findDocumentTypes());
        model.addAttribute("motionOptions", documentService.findMotionOptions(caseId));
        model.addAttribute("hearingOptions", documentService.findHearingOptions(caseId));
        model.addAttribute(
            "dispositionOptions",
            documentService.findDispositionOptions(caseId)
        );
        model.addAttribute("judgeOptions", documentService.findJudgeOptions());
        model.addAttribute("handlerOptions", documentService.findHandlerOptions());
        model.addAttribute(
            "submittedByDisplay",
            user == null ? "Current user" : user.getDisplayName()
        );
        model.addAttribute("activeNav", "cases");
    }
}
