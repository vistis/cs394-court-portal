package kh.edu.paragoniu.court_portal.web;

import java.time.LocalDate;
import kh.edu.paragoniu.court_portal.cases.CaseRow;
import kh.edu.paragoniu.court_portal.cases.CaseService;
import kh.edu.paragoniu.court_shared.repository.CaseClassificationRepository;
import kh.edu.paragoniu.court_shared.repository.CaseStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class CaseController {

    private static final int PAGE_SIZE = 8;

    private final CaseService caseService;
    private final CaseStatusRepository caseStatusRepository;
    private final CaseClassificationRepository caseClassificationRepository;

    @GetMapping("/cases")
    public String cases(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) Integer classification,
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate to,
        @RequestParam(defaultValue = "0") int page,
        Model model
    ) {
        Pageable pageable = PageRequest.of(
            Math.max(page, 0),
            PAGE_SIZE,
            Sort.by(Sort.Direction.DESC, "filedAt")
        );
        Page<CaseRow> result = caseService.search(
            q,
            classification,
            status,
            from,
            to,
            pageable
        );

        model.addAttribute("page", result);
        model.addAttribute("statuses", caseStatusRepository.findAll());
        model.addAttribute(
            "classifications",
            caseClassificationRepository.findAll()
        );
        model.addAttribute("q", q);
        model.addAttribute("selectedClassification", classification);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("activeNav", "cases");
        return "cases";
    }
}
