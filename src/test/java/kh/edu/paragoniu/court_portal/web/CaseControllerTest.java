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
import kh.edu.paragoniu.court_portal.cases.AssignedGreffierView;
import kh.edu.paragoniu.court_portal.cases.AssignedJudgeView;
import kh.edu.paragoniu.court_portal.cases.CaseDetailNotFoundException;
import kh.edu.paragoniu.court_portal.cases.CaseDetailView;
import kh.edu.paragoniu.court_portal.cases.CaseRow;
import kh.edu.paragoniu.court_portal.cases.CaseService;
import kh.edu.paragoniu.court_portal.cases.DocketActivityTypeOption;
import kh.edu.paragoniu.court_portal.cases.DocketEntryRow;
import kh.edu.paragoniu.court_portal.cases.DispositionTabView;
import kh.edu.paragoniu.court_portal.cases.DispositionView;
import kh.edu.paragoniu.court_portal.cases.FilterOption;
import kh.edu.paragoniu.court_portal.greffier.GreffierService;
import kh.edu.paragoniu.court_portal.legal.LawyerJudgeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.AbstractView;

@ExtendWith(MockitoExtension.class)
class CaseControllerTest {

    @Mock
    private CaseService caseService;

    @Mock
    private LawyerJudgeService lawyerJudgeService;

    @Mock
    private GreffierService greffierService;

    @Test
    void caseDetailRendersExistingCaseForAuthorizedUser() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(caseService.findDetail(caseId)).thenReturn(detail(caseId));

        mockMvc()
            .perform(get("/cases/{caseId}", caseId))
            .andExpect(status().isOk())
            .andExpect(view().name("case-detail"))
            .andExpect(model().attributeExists("caseDetail"));

        verify(caseService).findDetail(caseId);
    }

    @Test
    void caseDetailHandlesMissingCase() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(caseService.findDetail(caseId))
            .thenThrow(new CaseDetailNotFoundException(caseId));

        mockMvc()
            .perform(get("/cases/{caseId}", caseId))
            .andExpect(status().isNotFound())
            .andExpect(view().name("case-not-found"));
    }

    @Test
    void caseDetailHandlesMalformedUuid() throws Exception {
        mockMvc()
            .perform(get("/cases/not-a-uuid"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("case-not-found"));
    }

    @Test
    void caseDirectoryIncludesRowsWithCaseIdsForDetailLinks() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(caseService.search(any(), any(), any(), any(), any(), any()))
            .thenReturn(
                new PageImpl<>(
                    List.of(
                        new CaseRow(
                            caseId,
                            "CMS-2026-0001",
                            "State vs. Henderson",
                            "Criminal",
                            "Filing Open",
                            "badge--green",
                            "Jul 16, 2026",
                            "Hon. Sarah Jenkins"
                        )
                    )
                )
            );
        when(caseService.findStatusOptions()).thenReturn(List.of());
        when(caseService.findClassificationOptions()).thenReturn(List.of());

        mockMvc()
            .perform(get("/cases"))
            .andExpect(status().isOk())
            .andExpect(view().name("cases"))
            .andExpect(model().attributeExists("page"));

        verify(caseService)
            .search(eq(null), eq(null), eq(null), eq(null), eq(null), any());
    }

    @Test
    void docketSheetRendersEntriesForExistingCase() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(caseService.findDetail(caseId)).thenReturn(detail(caseId));
        when(caseService.findDocketEntries(eq(caseId), eq(null), eq(null), any()))
            .thenReturn(
                new PageImpl<>(
                    List.of(
                        new DocketEntryRow(
                            "Jul 16, 2026",
                            "Filing",
                            "badge--blue",
                            "Motion filed.",
                            "J. Thompson"
                        )
                    )
                )
            );
        when(caseService.findDocketActivityTypeOptions(caseId))
            .thenReturn(List.of(new DocketActivityTypeOption("FILING", "Filing")));

        mockMvc()
            .perform(get("/cases/{caseId}/docket", caseId))
            .andExpect(status().isOk())
            .andExpect(view().name("case-docket"))
            .andExpect(model().attributeExists("caseDetail", "docketPage"));
    }

    @Test
    void docketSheetHandlesMalformedUuid() throws Exception {
        mockMvc()
            .perform(get("/cases/not-a-uuid/docket"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("case-not-found"));
    }

    @Test
    void dispositionTabRendersEmptyStateForExistingCase() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(caseService.findDetail(caseId)).thenReturn(detail(caseId));
        when(caseService.findDispositionTab(caseId))
            .thenReturn(new DispositionTabView(null, false, "", ""));

        mockMvc()
            .perform(get("/cases/{caseId}/disposition", caseId))
            .andExpect(status().isOk())
            .andExpect(view().name("case-disposition"))
            .andExpect(model().attributeExists("caseDetail", "dispositionTab"));
    }

    @Test
    void newDispositionFormRendersOutcomeOptions() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(caseService.findDetail(caseId)).thenReturn(detail(caseId));
        when(caseService.findDispositionTab(caseId))
            .thenReturn(
                new DispositionTabView(
                    new DispositionView(
                        UUID.randomUUID(),
                        "Guilty Verdict",
                        "Jul 16, 2026",
                        "Final ruling.",
                        "Hon. Sarah Jenkins"
                    ),
                    false,
                    "",
                    ""
                )
            );
        when(caseService.findDispositionOutcomeOptions())
            .thenReturn(List.of(new FilterOption(1, "Guilty Verdict")));

        mockMvc()
            .perform(get("/cases/{caseId}/disposition/new", caseId))
            .andExpect(status().isOk())
            .andExpect(view().name("case-disposition-form"))
            .andExpect(
                model()
                    .attributeExists(
                        "caseDetail",
                        "dispositionTab",
                        "createDispositionForm",
                        "outcomes"
                    )
            );
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders
            .standaloneSetup(
                new CaseController(caseService, lawyerJudgeService, greffierService)
            )
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
