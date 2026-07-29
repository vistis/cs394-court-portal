package kh.edu.paragoniu.court_portal.cases;

/**
 * A judge or greffier suggestion for the quick-assign popups on the Case Detail
 * page. {@code alreadyAssigned} flags people already tied to the case so the UI
 * can show them without allowing a double-assignment.
 */
public record AssignPersonOption(
    String id,
    String name,
    String subtitle,
    boolean alreadyAssigned
) {}
