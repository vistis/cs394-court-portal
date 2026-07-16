package kh.edu.paragoniu.court_portal.cases;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public class UploadDocumentForm {

    @NotBlank(message = "Document title is required.")
    @Size(max = 160, message = "Document title must be 160 characters or less.")
    private String title;

    @NotBlank(message = "Document type is required.")
    private String documentType = "Filing";

    private boolean confidential;
    private MultipartFile file;

    private String filingCategory;
    private String reliefSought;

    private String requestAction;
    private String motionStatus = "Pending";
    private String argumentSummary;
    private boolean hearingRequired;
    private String ruledByJudgeId;
    private String ruledAt;

    private String approvedMotionId;
    private String originalHearingId;
    private String newHearingId;
    private String continuanceReason;
    private String requestedByParty;

    private String evidenceType;
    private String exhibitNumber;
    private List<ChainOfCustodyForm> chainOfCustody = new ArrayList<>(
        List.of(new ChainOfCustodyForm())
    );

    private String originatingDispositionId;
    private String sentenceTerms;
    private String prejudiceStatus;

    private String targetDispositionId;
    private String groundsForAppeal;
    private String appellateCourtLevel;
    private boolean lowerCourtRecordVerified;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public boolean isConfidential() {
        return confidential;
    }

    public void setConfidential(boolean confidential) {
        this.confidential = confidential;
    }

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }

    public String getFilingCategory() {
        return filingCategory;
    }

    public void setFilingCategory(String filingCategory) {
        this.filingCategory = filingCategory;
    }

    public String getReliefSought() {
        return reliefSought;
    }

    public void setReliefSought(String reliefSought) {
        this.reliefSought = reliefSought;
    }

    public String getRequestAction() {
        return requestAction;
    }

    public void setRequestAction(String requestAction) {
        this.requestAction = requestAction;
    }

    public String getMotionStatus() {
        return motionStatus;
    }

    public void setMotionStatus(String motionStatus) {
        this.motionStatus = motionStatus;
    }

    public String getArgumentSummary() {
        return argumentSummary;
    }

    public void setArgumentSummary(String argumentSummary) {
        this.argumentSummary = argumentSummary;
    }

    public boolean isHearingRequired() {
        return hearingRequired;
    }

    public void setHearingRequired(boolean hearingRequired) {
        this.hearingRequired = hearingRequired;
    }

    public String getRuledByJudgeId() {
        return ruledByJudgeId;
    }

    public void setRuledByJudgeId(String ruledByJudgeId) {
        this.ruledByJudgeId = ruledByJudgeId;
    }

    public String getRuledAt() {
        return ruledAt;
    }

    public void setRuledAt(String ruledAt) {
        this.ruledAt = ruledAt;
    }

    public String getApprovedMotionId() {
        return approvedMotionId;
    }

    public void setApprovedMotionId(String approvedMotionId) {
        this.approvedMotionId = approvedMotionId;
    }

    public String getOriginalHearingId() {
        return originalHearingId;
    }

    public void setOriginalHearingId(String originalHearingId) {
        this.originalHearingId = originalHearingId;
    }

    public String getNewHearingId() {
        return newHearingId;
    }

    public void setNewHearingId(String newHearingId) {
        this.newHearingId = newHearingId;
    }

    public String getContinuanceReason() {
        return continuanceReason;
    }

    public void setContinuanceReason(String continuanceReason) {
        this.continuanceReason = continuanceReason;
    }

    public String getRequestedByParty() {
        return requestedByParty;
    }

    public void setRequestedByParty(String requestedByParty) {
        this.requestedByParty = requestedByParty;
    }

    public String getEvidenceType() {
        return evidenceType;
    }

    public void setEvidenceType(String evidenceType) {
        this.evidenceType = evidenceType;
    }

    public String getExhibitNumber() {
        return exhibitNumber;
    }

    public void setExhibitNumber(String exhibitNumber) {
        this.exhibitNumber = exhibitNumber;
    }

    public List<ChainOfCustodyForm> getChainOfCustody() {
        return chainOfCustody;
    }

    public void setChainOfCustody(List<ChainOfCustodyForm> chainOfCustody) {
        this.chainOfCustody = chainOfCustody;
    }

    public String getOriginatingDispositionId() {
        return originatingDispositionId;
    }

    public void setOriginatingDispositionId(String originatingDispositionId) {
        this.originatingDispositionId = originatingDispositionId;
    }

    public String getSentenceTerms() {
        return sentenceTerms;
    }

    public void setSentenceTerms(String sentenceTerms) {
        this.sentenceTerms = sentenceTerms;
    }

    public String getPrejudiceStatus() {
        return prejudiceStatus;
    }

    public void setPrejudiceStatus(String prejudiceStatus) {
        this.prejudiceStatus = prejudiceStatus;
    }

    public String getTargetDispositionId() {
        return targetDispositionId;
    }

    public void setTargetDispositionId(String targetDispositionId) {
        this.targetDispositionId = targetDispositionId;
    }

    public String getGroundsForAppeal() {
        return groundsForAppeal;
    }

    public void setGroundsForAppeal(String groundsForAppeal) {
        this.groundsForAppeal = groundsForAppeal;
    }

    public String getAppellateCourtLevel() {
        return appellateCourtLevel;
    }

    public void setAppellateCourtLevel(String appellateCourtLevel) {
        this.appellateCourtLevel = appellateCourtLevel;
    }

    public boolean isLowerCourtRecordVerified() {
        return lowerCourtRecordVerified;
    }

    public void setLowerCourtRecordVerified(boolean lowerCourtRecordVerified) {
        this.lowerCourtRecordVerified = lowerCourtRecordVerified;
    }
}
