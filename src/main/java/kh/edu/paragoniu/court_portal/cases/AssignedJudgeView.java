package kh.edu.paragoniu.court_portal.cases;

public record AssignedJudgeView(String name, String licenseNumber) implements java.io.Serializable {
    public static AssignedJudgeView unassigned() {
        return new AssignedJudgeView("Unassigned", "");
    }
}
