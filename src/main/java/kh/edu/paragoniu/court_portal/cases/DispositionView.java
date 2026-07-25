package kh.edu.paragoniu.court_portal.cases;

import java.util.UUID;

public record DispositionView(
    UUID dispositionId,
    String outcome,
    String dispositionDate,
    String rulingSummary,
    String presidingJudge
) implements java.io.Serializable {}
