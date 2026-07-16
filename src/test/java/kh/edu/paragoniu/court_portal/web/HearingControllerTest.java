package kh.edu.paragoniu.court_portal.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.UUID;
import kh.edu.paragoniu.court_portal.cases.AssignedGreffierView;
import kh.edu.paragoniu.court_portal.cases.AssignedJudgeView;
import kh.edu.paragoniu.court_portal.cases.CaseDetailView;
import kh.edu.paragoniu.court_portal.cases.CaseService;
import kh.edu.paragoniu.court_portal.cases.FilterOption;
import kh.edu.paragoniu.court_portal.cases.HearingRow;
import kh.edu.paragoniu.court_portal.cases.HearingRescheduleView;
import kh.edu.paragoniu.court_portal.cases.HearingService;
import kh.edu.paragoniu.court_portal.cases.RescheduleHearingForm;
import kh.edu.paragoniu.court_portal.cases.ScheduleHearingForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.ConcurrentModel;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.view.AbstractView;

@ExtendWith(MockitoExtension.class)
class HearingControllerTest {

    @Mock
    private CaseService caseService;

    @Mock
    private HearingService hearingService;

    @Test
    void hearingsTabRendersUpcomingAndPastHearings() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(caseService.findDetail(caseId)).thenReturn(detail(caseId));
        when(hearingService.findUpcomingHearings(caseId))
            .thenReturn(
                List.of(
                    new HearingRow(
                        UUID.randomUUID().toString(),
                        "JUN",
                        "15",
                        "Pre-Trial Conference",
                        "10:00 AM - 11:30 AM",
                        "Courtroom A - Ground Floor",
                        "Hon. Sarah Jenkins",
                        "Scheduled",
                        "badge--amber"
                    )
                )
            );
        when(hearingService.findPastHearings(caseId)).thenReturn(List.of());

        mockMvc()
            .perform(get("/cases/{caseId}/hearings", caseId))
            .andExpect(status().isOk())
            .andExpect(view().name("case-hearings"))
            .andExpect(
                model()
                    .attributeExists(
                        "caseDetail",
                        "upcomingHearings",
                        "pastHearings"
                    )
            );

        verify(hearingService).findUpcomingHearings(caseId);
        verify(hearingService).findPastHearings(caseId);
    }

    @Test
    void hearingsTabHandlesMalformedUuid() throws Exception {
        mockMvc()
            .perform(get("/cases/not-a-uuid/hearings"))
            .andExpect(status().isNotFound())
            .andExpect(view().name("case-not-found"));
    }

    @Test
    void newHearingFormRendersOptions() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(caseService.findDetail(caseId)).thenReturn(detail(caseId));
        when(hearingService.findHearingTypeOptions())
            .thenReturn(List.of(new FilterOption(1, "Arraignment")));
        when(hearingService.findCourtroomOptions())
            .thenReturn(List.of(new FilterOption(1, "Courtroom A")));

        mockMvc()
            .perform(get("/cases/{caseId}/hearings/new", caseId))
            .andExpect(status().isOk())
            .andExpect(view().name("hearing-form"))
            .andExpect(
                model()
                    .attributeExists(
                        "caseDetail",
                        "scheduleHearingForm",
                        "hearingTypes",
                        "courtrooms"
                    )
            );
    }

    @Test
    void scheduleHearingRedirectsToHearingsTab() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(hearingService.scheduleHearing(eq(caseId), any(ScheduleHearingForm.class), eq(null)))
            .thenReturn(UUID.randomUUID());

        ScheduleHearingForm form = new ScheduleHearingForm();
        form.setHearingTypeId(1);
        form.setCourtroomId(1);
        form.setStartAt("2026-07-20T10:00");
        form.setEndAt("2026-07-20T11:00");

        String view = new HearingController(caseService, hearingService)
            .scheduleHearing(
                caseId.toString(),
                form,
                new BeanPropertyBindingResult(form, "scheduleHearingForm"),
                new ConcurrentModel(),
                new RedirectAttributesModelMap(),
                new org.springframework.mock.web.MockHttpServletResponse(),
                null
            );

        org.assertj.core.api.Assertions
            .assertThat(view)
            .isEqualTo("redirect:/cases/" + caseId + "/hearings");
        verify(hearingService).scheduleHearing(eq(caseId), any(ScheduleHearingForm.class), eq(null));
    }

    @Test
    void rescheduleHearingFormRendersModal() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID hearingId = UUID.randomUUID();
        when(caseService.findDetail(caseId)).thenReturn(detail(caseId));
        when(hearingService.findUpcomingHearings(caseId)).thenReturn(List.of());
        when(hearingService.findPastHearings(caseId)).thenReturn(List.of());
        when(hearingService.findCourtroomOptions())
            .thenReturn(List.of(new FilterOption(1, "Courtroom A")));
        when(hearingService.findRescheduleView(caseId, hearingId))
            .thenReturn(
                new HearingRescheduleView(
                    hearingId,
                    "Pre-Trial Conference",
                    "15 Jun 2026, 10:00 AM in Courtroom A",
                    1
                )
            );

        mockMvc()
            .perform(
                get(
                    "/cases/{caseId}/hearings/{hearingId}/reschedule",
                    caseId,
                    hearingId
                )
            )
            .andExpect(status().isOk())
            .andExpect(view().name("hearing-reschedule-form"))
            .andExpect(
                model()
                    .attributeExists(
                        "caseDetail",
                        "hearingReschedule",
                        "rescheduleHearingForm",
                        "courtrooms"
                    )
            );
    }

    @Test
    void rescheduleHearingRedirectsToHearingsTab() {
        UUID caseId = UUID.randomUUID();
        UUID hearingId = UUID.randomUUID();
        when(
            hearingService.rescheduleHearing(
                eq(caseId),
                eq(hearingId),
                any(RescheduleHearingForm.class),
                eq(null)
            )
        )
            .thenReturn(hearingId);

        RescheduleHearingForm form = new RescheduleHearingForm();
        form.setCourtroomId(1);
        form.setStartAt("2026-07-21T10:00");
        form.setEndAt("2026-07-21T11:00");

        String view = new HearingController(caseService, hearingService)
            .rescheduleHearing(
                caseId.toString(),
                hearingId.toString(),
                form,
                new BeanPropertyBindingResult(form, "rescheduleHearingForm"),
                new ConcurrentModel(),
                new RedirectAttributesModelMap(),
                new org.springframework.mock.web.MockHttpServletResponse(),
                null
            );

        org.assertj.core.api.Assertions
            .assertThat(view)
            .isEqualTo("redirect:/cases/" + caseId + "/hearings");
        verify(hearingService)
            .rescheduleHearing(
                eq(caseId),
                eq(hearingId),
                any(RescheduleHearingForm.class),
                eq(null)
            );
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders
            .standaloneSetup(new HearingController(caseService, hearingService))
            .setCustomArgumentResolvers(
                new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType()
                            .equals(kh.edu.paragoniu.court_portal.security.GreffierUserDetails.class);
                    }

                    @Override
                    public Object resolveArgument(
                        MethodParameter parameter,
                        ModelAndViewContainer mavContainer,
                        NativeWebRequest webRequest,
                        org.springframework.web.bind.support.WebDataBinderFactory binderFactory
                    ) {
                        return null;
                    }
                }
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
