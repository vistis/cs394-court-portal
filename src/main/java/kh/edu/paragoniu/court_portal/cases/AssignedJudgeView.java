package kh.edu.paragoniu.court_portal.cases;

public record AssignedJudgeView(String name, String licenseNumber) {
    public static AssignedJudgeView unassigned() {
        return new AssignedJudgeView("Unassigned", "");
    }
}
