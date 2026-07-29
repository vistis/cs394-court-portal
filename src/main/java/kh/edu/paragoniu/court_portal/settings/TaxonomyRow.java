package kh.edu.paragoniu.court_portal.settings;

/**
 * A display-ready row on a taxonomy management screen. {@code idLabel} is the
 * (optionally zero-padded) id; {@code badgeClass} is only set when the kind
 * shows a badge preview; {@code deletable} is true only when nothing references
 * the row (count == 0), which drives the red vs. greyed trash icon.
 */
public record TaxonomyRow(
    int id,
    String idLabel,
    String name,
    String displayName,
    String badgeClass,
    long count,
    boolean deletable
) {}
