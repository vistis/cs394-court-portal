package kh.edu.paragoniu.court_portal.settings;

/**
 * The four master-data taxonomies managed under System Settings. Each is a
 * simple {@code (id, name)} lookup table referenced by a fact table via a
 * foreign key; the metadata here (table/column names + display strings) drives
 * one generic management screen for all four.
 *
 * <p>Table and column names come only from this enum (never user input), so the
 * generic native SQL in {@link TaxonomyService} is safe to interpolate them.
 */
public enum TaxonomyKind {
    CLASSIFICATIONS(
        "case-classifications",
        "case_classifications",
        "classification_id",
        "cases",
        "classification_id",
        "Manage Case Classifications",
        "Manage Case Classifications",
        "Add or remove case classification types used across the system.",
        "Add Classification",
        "Enter new classification name...",
        "Add Classification",
        "Classification Name",
        "Case Count",
        false,
        false,
        false,
        false,
        null
    ),
    STATUSES(
        "case-statuses",
        "case_statuses",
        "status_id",
        "cases",
        "status_id",
        "Case Statuses",
        "Manage Case Statuses",
        "Configure the custom lifecycle statuses available for court cases.",
        "Add New Status",
        "Enter new status name...",
        "Add Status",
        "Status Name",
        "Case Count",
        true,
        true,
        false,
        false,
        null
    ),
    HEARING_TYPES(
        "hearing-types",
        "hearing_types",
        "hearing_type_id",
        "hearings",
        "hearing_type_id",
        "Hearing Types",
        "Manage Hearing Types",
        "Configure the standard hearing categories used for court scheduling. " +
        "Changes here will propagate to all scheduling modules.",
        "Add New Entry",
        "Enter new hearing type...",
        "Add Type",
        "Hearing Type Name",
        "Scheduled Count",
        false,
        false,
        false,
        false,
        null
    ),
    OUTCOMES(
        "disposition-outcomes",
        "disposition_outcomes",
        "outcome_type_id",
        "dispositions",
        "outcome_type_id",
        "Disposition Outcomes",
        "Manage Disposition Outcomes",
        "Configure the official outcome types used when closing or appealing cases.",
        "Add New Outcome Type",
        "Enter outcome name (e.g., Acquitted)...",
        "Add Outcome",
        "Outcome Name",
        "Usage Count",
        false,
        false,
        true,
        true,
        "Locked outcomes (gray trash icon) cannot be deleted as they are " +
        "currently associated with active or closed cases."
    );

    private final String slug;
    private final String table;
    private final String idColumn;
    private final String refTable;
    private final String refColumn;
    private final String breadcrumbLabel;
    private final String title;
    private final String subtitle;
    private final String addTitle;
    private final String placeholder;
    private final String addButtonLabel;
    private final String nameHeader;
    private final String countHeader;
    private final boolean showBadge;
    private final boolean prettifyName;
    private final boolean zeroPadId;
    private final boolean countPill;
    private final String footnote;

    TaxonomyKind(
        String slug,
        String table,
        String idColumn,
        String refTable,
        String refColumn,
        String breadcrumbLabel,
        String title,
        String subtitle,
        String addTitle,
        String placeholder,
        String addButtonLabel,
        String nameHeader,
        String countHeader,
        boolean showBadge,
        boolean prettifyName,
        boolean zeroPadId,
        boolean countPill,
        String footnote
    ) {
        this.slug = slug;
        this.table = table;
        this.idColumn = idColumn;
        this.refTable = refTable;
        this.refColumn = refColumn;
        this.breadcrumbLabel = breadcrumbLabel;
        this.title = title;
        this.subtitle = subtitle;
        this.addTitle = addTitle;
        this.placeholder = placeholder;
        this.addButtonLabel = addButtonLabel;
        this.nameHeader = nameHeader;
        this.countHeader = countHeader;
        this.showBadge = showBadge;
        this.prettifyName = prettifyName;
        this.zeroPadId = zeroPadId;
        this.countPill = countPill;
        this.footnote = footnote;
    }

    public static TaxonomyKind fromSlug(String slug) {
        for (TaxonomyKind k : values()) {
            if (k.slug.equals(slug)) {
                return k;
            }
        }
        return null;
    }

    public String slug() {
        return slug;
    }

    public String table() {
        return table;
    }

    public String idColumn() {
        return idColumn;
    }

    public String refTable() {
        return refTable;
    }

    public String refColumn() {
        return refColumn;
    }

    public String breadcrumbLabel() {
        return breadcrumbLabel;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    public String addTitle() {
        return addTitle;
    }

    public String placeholder() {
        return placeholder;
    }

    public String addButtonLabel() {
        return addButtonLabel;
    }

    public String nameHeader() {
        return nameHeader;
    }

    public String countHeader() {
        return countHeader;
    }

    public boolean showBadge() {
        return showBadge;
    }

    public boolean prettifyName() {
        return prettifyName;
    }

    public boolean zeroPadId() {
        return zeroPadId;
    }

    public boolean countPill() {
        return countPill;
    }

    public String footnote() {
        return footnote;
    }
}
