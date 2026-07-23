package kh.edu.paragoniu.court_portal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.UUID;
import kh.edu.paragoniu.court_portal.cases.DocumentTypeOption;
import kh.edu.paragoniu.court_portal.participants.CreateParticipantForm;
import kh.edu.paragoniu.court_portal.participants.ParticipantDirectoryRow;
import kh.edu.paragoniu.court_portal.participants.ParticipantDirectoryService;
import kh.edu.paragoniu.court_portal.participants.ParticipantDocumentRow;
import kh.edu.paragoniu.court_portal.participants.ParticipantInvolvedCaseRow;
import kh.edu.paragoniu.court_portal.participants.ParticipantNotFoundException;
import kh.edu.paragoniu.court_portal.participants.ParticipantProfileView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ConcurrentModel;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.view.AbstractView;

@ExtendWith(MockitoExtension.class)
class ParticipantDirectoryControllerTest {

    @Mock
    private ParticipantDirectoryService participantDirectoryService;

    @Test
    void participantsListRendersDirectory() throws Exception {
        UUID participantId = UUID.randomUUID();
        when(participantDirectoryService.search(eq(null), eq(null), any()))
            .thenReturn(
                new PageImpl<>(
                    List.of(
                        new ParticipantDirectoryRow(
                            participantId,
                            "Michael Henderson",
                            "Individual",
                            "badge--blue",
                            "m.henderson@email.com",
                            3
                        )
                    )
                )
            );
        when(participantDirectoryService.findPartyTypeOptions())
            .thenReturn(List.of("Individual", "Group"));

        mockMvc()
            .perform(get("/participants"))
            .andExpect(status().isOk())
            .andExpect(view().name("participants"))
            .andExpect(model().attributeExists("page", "partyTypeOptions"));

        verify(participantDirectoryService).search(eq(null), eq(null), any());
    }

    @Test
    void participantsListForwardsSearchAndPartyTypeFilter() throws Exception {
        when(participantDirectoryService.search(eq("michael"), eq("Group"), any()))
            .thenReturn(new PageImpl<>(List.of()));
        when(participantDirectoryService.findPartyTypeOptions())
            .thenReturn(List.of("Individual", "Group"));

        mockMvc()
            .perform(
                get("/participants")
                    .param("query", "michael")
                    .param("partyType", "Group")
            )
            .andExpect(status().isOk())
            .andExpect(view().name("participants"));

        verify(participantDirectoryService).search(eq("michael"), eq("Group"), any());
    }

    @Test
    void addParticipantFormRendersModal() throws Exception {
        when(participantDirectoryService.search(eq(null), eq(null), any()))
            .thenReturn(new PageImpl<>(List.of()));
        when(participantDirectoryService.findPartyTypeOptions())
            .thenReturn(List.of("Individual", "Group"));

        mockMvc()
            .perform(get("/participants/add"))
            .andExpect(status().isOk())
            .andExpect(view().name("participant-form"))
            .andExpect(
                model().attributeExists("createParticipantForm", "page")
            );
    }

    @Test
    void createParticipantRedirectsToDirectoryOnSuccess() {
        CreateParticipantForm form = new CreateParticipantForm();
        form.setPartyType("Individual");
        form.setName("Michael Henderson");
        form.setEmail("m.henderson@email.com");
        form.setPhone("555-0192");

        String view = new ParticipantDirectoryController(participantDirectoryService)
            .createParticipant(
                form,
                new BeanPropertyBindingResult(form, "createParticipantForm"),
                new ConcurrentModel(),
                new RedirectAttributesModelMap()
            );

        assertThat(view).isEqualTo("redirect:/participants");
        verify(participantDirectoryService).createParticipant(form);
    }

    @Test
    void createParticipantWithBindingErrorsReRendersFormWithoutCallingService() {
        CreateParticipantForm form = new CreateParticipantForm();
        form.setPartyType("Individual");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(
            form,
            "createParticipantForm"
        );
        bindingResult.rejectValue("name", "name.required", "Name is required.");
        when(participantDirectoryService.search(eq(null), eq(null), any()))
            .thenReturn(new PageImpl<>(List.of()));
        when(participantDirectoryService.findPartyTypeOptions())
            .thenReturn(List.of("Individual", "Group"));

        String view = new ParticipantDirectoryController(participantDirectoryService)
            .createParticipant(
                form,
                bindingResult,
                new ConcurrentModel(),
                new RedirectAttributesModelMap()
            );

        assertThat(view).isEqualTo("participant-form");
        verify(participantDirectoryService, never()).createParticipant(any());
    }

    @Test
    void profileRendersForValidParticipant() throws Exception {
        UUID participantId = UUID.randomUUID();
        when(participantDirectoryService.findProfile(participantId))
            .thenReturn(profile(participantId, null));

        mockMvc()
            .perform(get("/participants/{id}", participantId))
            .andExpect(status().isOk())
            .andExpect(view().name("participant-profile"))
            .andExpect(model().attributeExists("profile"));
    }

    @Test
    void profileReturns404ForMalformedUuid() throws Exception {
        mockMvc()
            .perform(get("/participants/not-a-uuid"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("participant-not-found"));
    }

    @Test
    void profileReturns404WhenParticipantNotFound() throws Exception {
        UUID participantId = UUID.randomUUID();
        when(participantDirectoryService.findProfile(participantId))
            .thenThrow(new ParticipantNotFoundException(participantId));

        mockMvc()
            .perform(get("/participants/{id}", participantId))
            .andExpect(status().isNotFound())
            .andExpect(view().name("participant-not-found"));
    }

    @Test
    void profileShowsStoredImageWhenProfilePicturePathIsSet() throws Exception {
        UUID participantId = UUID.randomUUID();
        when(participantDirectoryService.findProfile(participantId))
            .thenReturn(
                profile(participantId, "https://cdn.example.com/participants/photo.jpg")
            );

        ParticipantProfileView captured = (ParticipantProfileView) mockMvc()
            .perform(get("/participants/{id}", participantId))
            .andExpect(status().isOk())
            .andReturn()
            .getModelAndView()
            .getModel()
            .get("profile");

        assertThat(captured.profileImageUrl())
            .isEqualTo("https://cdn.example.com/participants/photo.jpg");
    }

    @Test
    void profileFallsBackToInitialsWhenNoProfilePicture() throws Exception {
        UUID participantId = UUID.randomUUID();
        when(participantDirectoryService.findProfile(participantId))
            .thenReturn(profile(participantId, null));

        ParticipantProfileView captured = (ParticipantProfileView) mockMvc()
            .perform(get("/participants/{id}", participantId))
            .andReturn()
            .getModelAndView()
            .getModel()
            .get("profile");

        assertThat(captured.profileImageUrl()).isNull();
        assertThat(captured.initials()).isEqualTo("MH");
    }

    @Test
    void involvedCasesTabRendersRowsWithPerCaseRole() throws Exception {
        UUID participantId = UUID.randomUUID();
        UUID caseIdOne = UUID.randomUUID();
        UUID caseIdTwo = UUID.randomUUID();
        when(participantDirectoryService.findProfile(participantId))
            .thenReturn(profile(participantId, null));
        when(participantDirectoryService.findInvolvedCases(participantId))
            .thenReturn(
                List.of(
                    new ParticipantInvolvedCaseRow(
                        caseIdOne,
                        "CR-2026-0881",
                        "State vs. Henderson",
                        "Criminal Felony",
                        "Open",
                        "badge--green",
                        "Defendant"
                    ),
                    new ParticipantInvolvedCaseRow(
                        caseIdTwo,
                        "CV-2024-0015",
                        "TechFlow Inc. vs. Henderson",
                        "Commercial Dispute",
                        "Pending",
                        "badge--amber",
                        "Respondent"
                    )
                )
            );

        List<ParticipantInvolvedCaseRow> captured;
        var result = mockMvc()
            .perform(get("/participants/{id}/cases", participantId))
            .andExpect(status().isOk())
            .andExpect(view().name("participant-cases"))
            .andExpect(model().attributeExists("profile", "involvedCases"))
            .andReturn();
        @SuppressWarnings("unchecked")
        List<ParticipantInvolvedCaseRow> rows = (List<ParticipantInvolvedCaseRow>) result
            .getModelAndView()
            .getModel()
            .get("involvedCases");
        captured = rows;

        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).role()).isEqualTo("Defendant");
        assertThat(captured.get(1).role()).isEqualTo("Respondent");
        assertThat(captured.get(0).caseId()).isEqualTo(caseIdOne);
        assertThat(captured.get(1).caseId()).isEqualTo(caseIdTwo);
    }

    @Test
    void involvedCasesTabRendersEmptyStateWhenParticipantHasNoCases() throws Exception {
        UUID participantId = UUID.randomUUID();
        when(participantDirectoryService.findProfile(participantId))
            .thenReturn(profile(participantId, null));
        when(participantDirectoryService.findInvolvedCases(participantId))
            .thenReturn(List.of());

        mockMvc()
            .perform(get("/participants/{id}/cases", participantId))
            .andExpect(status().isOk())
            .andExpect(view().name("participant-cases"))
            .andExpect(model().attributeExists("involvedCases"));
    }

    @Test
    void documentsTabRendersRowsAcrossDifferentCases() throws Exception {
        UUID participantId = UUID.randomUUID();
        UUID caseIdOne = UUID.randomUUID();
        UUID caseIdTwo = UUID.randomUUID();
        when(participantDirectoryService.findProfile(participantId))
            .thenReturn(profile(participantId, null));
        when(participantDirectoryService.findDocumentTypeOptions())
            .thenReturn(List.of(new DocumentTypeOption("Filing", "Filing")));
        when(
            participantDirectoryService.findDocuments(
                eq(participantId),
                eq(null),
                eq(null),
                any()
            )
        )
            .thenReturn(
                new PageImpl<>(
                    List.of(
                        new ParticipantDocumentRow(
                            "doc-1",
                            "Affidavit of Residency",
                            "Filing",
                            "badge--blue",
                            caseIdOne,
                            "CR-2026-0881",
                            "Jan 15, 2026",
                            false
                        ),
                        new ParticipantDocumentRow(
                            "doc-2",
                            "Financial Disclosure",
                            "Evidence",
                            "badge--orange",
                            caseIdTwo,
                            "CV-2024-0015",
                            "Aug 05, 2024",
                            true
                        )
                    )
                )
            );

        var result = mockMvc()
            .perform(get("/participants/{id}/documents", participantId))
            .andExpect(status().isOk())
            .andExpect(view().name("participant-documents"))
            .andExpect(model().attributeExists("profile", "documentPage", "documentTypes"))
            .andReturn();

        @SuppressWarnings("unchecked")
        PageImpl<ParticipantDocumentRow> documentPage = (PageImpl<ParticipantDocumentRow>) result
            .getModelAndView()
            .getModel()
            .get("documentPage");

        assertThat(documentPage.getContent()).hasSize(2);
        assertThat(documentPage.getContent().get(0).caseId()).isEqualTo(caseIdOne);
        assertThat(documentPage.getContent().get(1).caseId()).isEqualTo(caseIdTwo);
        assertThat(documentPage.getContent().get(0).confidential()).isFalse();
        assertThat(documentPage.getContent().get(1).confidential()).isTrue();
    }

    @Test
    void documentsTabForwardsSearchQuery() throws Exception {
        UUID participantId = UUID.randomUUID();
        when(participantDirectoryService.findProfile(participantId))
            .thenReturn(profile(participantId, null));
        when(participantDirectoryService.findDocumentTypeOptions())
            .thenReturn(List.of());
        when(
            participantDirectoryService.findDocuments(
                eq(participantId),
                eq("affidavit"),
                eq(null),
                any()
            )
        )
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc()
            .perform(
                get("/participants/{id}/documents", participantId)
                    .param("query", "affidavit")
            )
            .andExpect(status().isOk());

        verify(participantDirectoryService)
            .findDocuments(eq(participantId), eq("affidavit"), eq(null), any());
    }

    @Test
    void documentsTabForwardsDocumentTypeFilter() throws Exception {
        UUID participantId = UUID.randomUUID();
        when(participantDirectoryService.findProfile(participantId))
            .thenReturn(profile(participantId, null));
        when(participantDirectoryService.findDocumentTypeOptions())
            .thenReturn(List.of());
        when(
            participantDirectoryService.findDocuments(
                eq(participantId),
                eq(null),
                eq("Evidence"),
                any()
            )
        )
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc()
            .perform(
                get("/participants/{id}/documents", participantId)
                    .param("documentType", "Evidence")
            )
            .andExpect(status().isOk());

        verify(participantDirectoryService)
            .findDocuments(eq(participantId), eq(null), eq("Evidence"), any());
    }

    @Test
    void documentsTabRendersEmptyStateWhenParticipantHasNoDocuments() throws Exception {
        UUID participantId = UUID.randomUUID();
        when(participantDirectoryService.findProfile(participantId))
            .thenReturn(profile(participantId, null));
        when(participantDirectoryService.findDocumentTypeOptions())
            .thenReturn(List.of());
        when(
            participantDirectoryService.findDocuments(
                eq(participantId),
                eq(null),
                eq(null),
                any()
            )
        )
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc()
            .perform(get("/participants/{id}/documents", participantId))
            .andExpect(status().isOk())
            .andExpect(view().name("participant-documents"))
            .andExpect(model().attributeExists("documentPage"));
    }

    private ParticipantProfileView profile(UUID participantId, String profileImageUrl) {
        return new ParticipantProfileView(
            participantId,
            "Michael Henderson",
            "MH",
            "Individual",
            "badge--blue",
            "Full Legal Name",
            "m.henderson@email.com",
            "555-0128",
            profileImageUrl
        );
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders
            .standaloneSetup(new ParticipantDirectoryController(participantDirectoryService))
            .setSingleView(
                new AbstractView() {
                    @Override
                    protected void renderMergedOutputModel(
                        java.util.Map<String, Object> model,
                        jakarta.servlet.http.HttpServletRequest request,
                        jakarta.servlet.http.HttpServletResponse response
                    ) {}
                }
            )
            .build();
    }
}
