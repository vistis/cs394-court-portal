package kh.edu.paragoniu.court_portal.cases;

public record DispositionTabView(
    DispositionView disposition,
    boolean appealExists,
    String appellateCaseNumber,
    String appellateCaseId
) {
    public boolean hasDisposition() {
        return disposition != null;
    }
}
