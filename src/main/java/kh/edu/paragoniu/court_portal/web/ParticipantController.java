package kh.edu.paragoniu.court_portal.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import kh.edu.paragoniu.court_portal.cases.AddCaseParticipantForm;
import kh.edu.paragoniu.court_portal.cases.CaseDetailNotFoundException;
import kh.edu.paragoniu.court_portal.cases.CaseParticipantException;
import kh.edu.paragoniu.court_portal.cases.CaseService;
import kh.edu.paragoniu.court_portal.cases.ParticipantService;
import lombok.RequiredArgsConstructor;
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
public class ParticipantController {

    private static final String DEFAULT_PARTY_TYPE = "Individual";

    private final CaseService caseService;
    private final ParticipantService participantService;

    @GetMapping("/cases/{caseId}/participants")
    public String participants(
        @PathVariable String caseId,
        @RequestParam(required = false) String query,
        @RequestParam(required = false) Integer roleId,
        Model model,
        HttpServletResponse response
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            addParticipantListModel(model, parsedCaseId, query, roleId);
            return "case-participants";
        } catch (IllegalArgumentException | CaseDetailNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    @GetMapping("/cases/{caseId}/participants/add")
    public String addParticipantForm(
        @PathVariable String caseId,
        @RequestParam(defaultValue = DEFAULT_PARTY_TYPE) String partyType,
        @RequestParam(required = false) String participantQuery,
        @RequestParam(required = false) String roleQuery,
        Model model,
        HttpServletResponse response
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            if (!model.containsAttribute("addCaseParticipantForm")) {
                AddCaseParticipantForm form = new AddCaseParticipantForm();
                form.setPartyType(partyType);
                model.addAttribute("addCaseParticipantForm", form);
            }
            addParticipantModalModel(
                model,
                parsedCaseId,
                partyType,
                participantQuery,
                roleQuery
            );
            return "case-participant-form";
        } catch (
            IllegalArgumentException
            | CaseDetailNotFoundException
            | CaseParticipantException ex
        ) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    @PostMapping("/cases/{caseId}/participants")
    public String addParticipant(
        @PathVariable String caseId,
        @Valid @ModelAttribute("addCaseParticipantForm") AddCaseParticipantForm form,
        BindingResult bindingResult,
        Model model,
        RedirectAttributes redirectAttributes,
        HttpServletResponse response
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
                participantService.addParticipant(parsedCaseId, form);
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Participant added to case successfully."
                );
                return "redirect:/cases/" + parsedCaseId + "/participants";
            } catch (CaseDetailNotFoundException ex) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                model.addAttribute("activeNav", "cases");
                return "case-not-found";
            } catch (CaseParticipantException ex) {
                if (ex.getFieldName() == null) {
                    bindingResult.reject("participant.add.failed", ex.getMessage());
                } else {
                    bindingResult.rejectValue(
                        ex.getFieldName(),
                        "participant.add.failed",
                        ex.getMessage()
                    );
                }
            }
        }

        addParticipantModalModel(
            model,
            parsedCaseId,
            form.getPartyType(),
            null,
            null
        );
        return "case-participant-form";
    }

    @PostMapping("/cases/{caseId}/participants/{participantId}/remove")
    public String removeParticipant(
        @PathVariable String caseId,
        @PathVariable String participantId,
        Model model,
        RedirectAttributes redirectAttributes,
        HttpServletResponse response
    ) {
        try {
            UUID parsedCaseId = UUID.fromString(caseId);
            UUID parsedParticipantId = UUID.fromString(participantId);
            participantService.removeParticipant(parsedCaseId, parsedParticipantId);
            redirectAttributes.addFlashAttribute(
                "successMessage",
                "Participant removed from case successfully."
            );
            return "redirect:/cases/" + parsedCaseId + "/participants";
        } catch (
            IllegalArgumentException
            | CaseDetailNotFoundException
            | CaseParticipantException ex
        ) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "cases");
            return "case-not-found";
        }
    }

    private void addParticipantListModel(
        Model model,
        UUID caseId,
        String query,
        Integer roleId
    ) {
        model.addAttribute("caseDetail", caseService.findDetail(caseId));
        model.addAttribute(
            "participants",
            participantService.findCaseParticipants(caseId, query, roleId)
        );
        model.addAttribute("roles", participantService.findRoleOptions());
        model.addAttribute("query", query);
        model.addAttribute("selectedRoleId", roleId);
        model.addAttribute("successMessage", model.asMap().get("successMessage"));
        model.addAttribute("activeNav", "cases");
    }

    private void addParticipantModalModel(
        Model model,
        UUID caseId,
        String partyType,
        String participantQuery,
        String roleQuery
    ) {
        addParticipantListModel(model, caseId, null, null);
        String selectedPartyType = normalizePartyType(partyType);
        model.addAttribute("selectedPartyType", selectedPartyType);
        model.addAttribute("participantQuery", participantQuery);
        model.addAttribute("roleQuery", roleQuery);
        model.addAttribute(
            "participantOptions",
            participantService.findParticipantOptions(
                caseId,
                selectedPartyType,
                participantQuery
            )
        );
        model.addAttribute("roleOptions", participantService.findRoleOptions());
    }

    private String normalizePartyType(String partyType) {
        if ("Group".equalsIgnoreCase(partyType)) {
            return "Group";
        }
        return DEFAULT_PARTY_TYPE;
    }
}
