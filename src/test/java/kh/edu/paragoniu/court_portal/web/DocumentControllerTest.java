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
import kh.edu.paragoniu.court_portal.cases.CaseDetailView;
import kh.edu.paragoniu.court_portal.cases.CaseService;
import kh.edu.paragoniu.court_portal.cases.DocumentRow;
import kh.edu.paragoniu.court_portal.cases.DocumentService;
import kh.edu.paragoniu.court_portal.cases.DocumentTypeOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.AbstractView;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock
    private CaseService caseService;

    @Mock
    private DocumentService documentService;

    @Test
    void documentsTabRendersCaseDocuments() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(caseService.findDetail(caseId)).thenReturn(detail(caseId));
        when(documentService.findDocuments(eq(caseId), eq(null), eq(null), eq(null), any()))
            .thenReturn(
                new PageImpl<>(
                    List.of(
                        new DocumentRow(
                            "doc-1",
                            "Filing",
                            "Filing",
                            "badge--blue",
                            "Initial filing",
                            "Jul 16, 2026",
                            false
                        )
                    )
                )
            );
        when(documentService.findDocumentTypes())
            .thenReturn(List.of(new DocumentTypeOption("Filing", "Filing")));
        when(documentService.countDocuments(caseId)).thenReturn(1L);

        mockMvc()
            .perform(get("/cases/{caseId}/documents", caseId))
            .andExpect(status().isOk())
            .andExpect(view().name("case-documents"))
            .andExpect(model().attributeExists("caseDetail", "documentPage"));

        verify(documentService)
            .findDocuments(eq(caseId), eq(null), eq(null), eq(null), any());
    }

    @Test
    void documentsTabHandlesMalformedUuid() throws Exception {
        mockMvc()
            .perform(get("/cases/not-a-uuid/documents"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("case-not-found"));
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders
            .standaloneSetup(new DocumentController(caseService, documentService))
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
