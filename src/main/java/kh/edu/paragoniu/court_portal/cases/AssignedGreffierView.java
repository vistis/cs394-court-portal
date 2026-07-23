package kh.edu.paragoniu.court_portal.cases;

public record AssignedGreffierView(String name, String role, String assignedDate) implements java.io.Serializable {
    public static AssignedGreffierView unassigned() {
        return new AssignedGreffierView(
            "Unassigned",
            "No greffier assigned",
            ""
        );
    }
}
