package kh.edu.paragoniu.court_portal.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import kh.edu.paragoniu.court_portal.participants.CreateParticipantForm;
import kh.edu.paragoniu.court_portal.participants.ParticipantDirectoryException;
import kh.edu.paragoniu.court_portal.participants.ParticipantDirectoryRow;
import kh.edu.paragoniu.court_portal.participants.ParticipantDirectoryService;
import kh.edu.paragoniu.court_portal.participants.ParticipantNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
public class ParticipantDirectoryController {

    private static final int PAGE_SIZE = 20;

    private final ParticipantDirectoryService participantDirectoryService;

    @GetMapping("/participants")
    public String participants(
        @RequestParam(required = false) String query,
        @RequestParam(required = false) String partyType,
        @RequestParam(defaultValue = "0") int page,
        Model model
    ) {
        addDirectoryModel(model, query, partyType, page);
        return "participants";
    }

    @GetMapping("/participants/add")
    public String addParticipantForm(Model model) {
        if (!model.containsAttribute("createParticipantForm")) {
            model.addAttribute("createParticipantForm", new CreateParticipantForm());
        }
        addDirectoryModel(model, null, null, 0);
        return "participant-form";
    }

    @PostMapping("/participants")
    public String createParticipant(
        @Valid @ModelAttribute(
            "createParticipantForm"
        ) CreateParticipantForm form,
        BindingResult bindingResult,
        Model model,
        RedirectAttributes redirectAttributes
    ) {
        if (!bindingResult.hasErrors()) {
            try {
                participantDirectoryService.createParticipant(form);
                redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Participant registered successfully."
                );
                return "redirect:/participants";
            } catch (ParticipantDirectoryException ex) {
                if (ex.getFieldName() == null) {
                    bindingResult.reject("participant.create.failed", ex.getMessage());
                } else {
                    bindingResult.rejectValue(
                        ex.getFieldName(),
                        "participant.create.failed",
                        ex.getMessage()
                    );
                }
            }
        }

        addDirectoryModel(model, null, null, 0);
        return "participant-form";
    }

    @GetMapping("/participants/{participantId}")
    public String profile(
        @PathVariable String participantId,
        Model model,
        HttpServletResponse response
    ) {
        return renderProfileTab(participantId, "participant-profile", model, response);
    }

    @GetMapping("/participants/{participantId}/cases")
    public String involvedCases(
        @PathVariable String participantId,
        Model model,
        HttpServletResponse response
    ) {
        return renderProfileTab(participantId, "participant-cases", model, response);
    }

    @GetMapping("/participants/{participantId}/documents")
    public String documents(
        @PathVariable String participantId,
        Model model,
        HttpServletResponse response
    ) {
        return renderProfileTab(participantId, "participant-documents", model, response);
    }

    private String renderProfileTab(
        String participantId,
        String viewName,
        Model model,
        HttpServletResponse response
    ) {
        try {
            UUID id = UUID.fromString(participantId);
            model.addAttribute("profile", participantDirectoryService.findProfile(id));
            model.addAttribute("activeNav", "participants");
            return viewName;
        } catch (IllegalArgumentException | ParticipantNotFoundException ex) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("activeNav", "participants");
            return "participant-not-found";
        }
    }

    private void addDirectoryModel(
        Model model,
        String query,
        String partyType,
        int page
    ) {
        Pageable pageable = PageRequest.of(
            Math.max(page, 0),
            PAGE_SIZE,
            Sort.by("name")
        );
        Page<ParticipantDirectoryRow> result = participantDirectoryService.search(
            query,
            partyType,
            pageable
        );

        model.addAttribute("page", result);
        model.addAttribute(
            "partyTypeOptions",
            participantDirectoryService.findPartyTypeOptions()
        );
        model.addAttribute("query", query);
        model.addAttribute("selectedPartyType", partyType);
        model.addAttribute("successMessage", model.asMap().get("successMessage"));
        model.addAttribute("activeNav", "participants");
    }
}
