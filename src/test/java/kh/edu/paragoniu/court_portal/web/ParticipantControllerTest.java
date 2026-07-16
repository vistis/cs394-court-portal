package kh.edu.paragoniu.court_portal.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.UUID;
import kh.edu.paragoniu.court_portal.cases.AddCaseParticipantForm;
import kh.edu.paragoniu.court_portal.cases.AssignedGreffierView;
import kh.edu.paragoniu.court_portal.cases.AssignedJudgeView;
import kh.edu.paragoniu.court_portal.cases.CaseDetailView;
import kh.edu.paragoniu.court_portal.cases.CaseParticipantRow;
import kh.edu.paragoniu.court_portal.cases.CaseService;
import kh.edu.paragoniu.court_portal.cases.FilterOption;
import kh.edu.paragoniu.court_portal.cases.ParticipantOption;
import kh.edu.paragoniu.court_portal.cases.ParticipantService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ConcurrentModel;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.servlet.view.AbstractView;

@ExtendWith(MockitoExtension.class)
class ParticipantControllerTest {

    @Mock
    private CaseService caseService;

    @Mock
    private ParticipantService participantService;

    @Test
    void participantsTabRendersCaseParticipants() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        when(caseService.findDetail(caseId)).thenReturn(detail(caseId));
        when(participantService.findCaseParticipants(caseId, "michael", 2))
            .thenReturn(
                List.of(
                    new CaseParticipantRow(
                        participantId,
                        "Michael Henderson",
                        "Individual",
                        "Defendant",
                        "email: michael@example.com"
                    )
                )
            );
        when(participantService.findRoleOptions())
            .thenReturn(List.of(new FilterOption(2, "Defendant")));

        mockMvc()
            .perform(
                get("/cases/{caseId}/participants", caseId)
                    .param("query", "michael")
                    .param("roleId", "2")
            )
            .andExpect(status().isOk())
            .andExpect(view().name("case-participants"))
            .andExpect(
                model().attributeExists("caseDetail", "participants", "roles")
            );

        verify(participantService).findCaseParticipants(caseId, "michael", 2);
    }

    @Test
    void participantsTabHandlesMalformedUuid() throws Exception {
        mockMvc()
            .perform(get("/cases/not-a-uuid/participants"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("case-not-found"));
    }

    @Test
    void addParticipantFormRendersOptions() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(caseService.findDetail(caseId)).thenReturn(detail(caseId));
        when(participantService.findCaseParticipants(caseId, null, null))
            .thenReturn(List.of());
        when(participantService.findRoleOptions())
            .thenReturn(List.of(new FilterOption(4, "Witness")));
        when(participantService.findParticipantOptions(caseId, "Group", "Sakura"))
            .thenReturn(
                List.of(
                    new ParticipantOption(
                        UUID.randomUUID(),
                        "Sakura Org",
                        "phone: 555-0192"
                    )
                )
            );

        mockMvc()
            .perform(
                get("/cases/{caseId}/participants/add", caseId)
                    .param("partyType", "Group")
                    .param("participantQuery", "Sakura")
            )
            .andExpect(status().isOk())
            .andExpect(view().name("case-participant-form"))
            .andExpect(
                model()
                    .attributeExists(
                        "caseDetail",
                        "addCaseParticipantForm",
                        "participantOptions",
                        "roleOptions"
                    )
            );
    }

    @Test
    void addParticipantRedirectsToParticipantsTab() {
        UUID caseId = UUID.randomUUID();
        AddCaseParticipantForm form = new AddCaseParticipantForm();
        form.setPartyType("Individual");
        form.setParticipantId(UUID.randomUUID());
        form.setRoleId(4);

        String view = new ParticipantController(caseService, participantService)
            .addParticipant(
                caseId.toString(),
                form,
                new BeanPropertyBindingResult(form, "addCaseParticipantForm"),
                new ConcurrentModel(),
                new RedirectAttributesModelMap(),
                new MockHttpServletResponse()
            );

        org.assertj.core.api.Assertions
            .assertThat(view)
            .isEqualTo("redirect:/cases/" + caseId + "/participants");
        verify(participantService).addParticipant(eq(caseId), any(AddCaseParticipantForm.class));
    }

    @Test
    void removeParticipantRedirectsToParticipantsTab() {
        UUID caseId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        String view = new ParticipantController(caseService, participantService)
            .removeParticipant(
                caseId.toString(),
                participantId.toString(),
                new ConcurrentModel(),
                new RedirectAttributesModelMap(),
                new MockHttpServletResponse()
            );

        org.assertj.core.api.Assertions
            .assertThat(view)
            .isEqualTo("redirect:/cases/" + caseId + "/participants");
        verify(participantService).removeParticipant(caseId, participantId);
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders
            .standaloneSetup(new ParticipantController(caseService, participantService))
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

    private CaseDetailView detail(UUID caseId) {
        return new CaseDetailView(
            caseId,
            "CMS-2026-0001",
            "State vs. Henderson",
            "Criminal proceedings relating to alleged theft of property.",
            "Criminal",
            "Filing Open",
            "badge--green",
            "Jul 16, 2026",
            "Jul 16, 2026",
            "",
            "Internal",
            new AssignedJudgeView("Hon. Sarah Jenkins", "JDG-001"),
            new AssignedGreffierView("J. Thompson", "Chief Greffier", "Jul 16, 2026")
        );
    }
}
