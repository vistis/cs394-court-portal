package kh.edu.paragoniu.court_portal.cases;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import kh.edu.paragoniu.court_shared.entity.Case;
import kh.edu.paragoniu.court_shared.entity.CaseParticipant;
import kh.edu.paragoniu.court_shared.entity.CaseParticipantId;
import kh.edu.paragoniu.court_shared.entity.Participant;
import kh.edu.paragoniu.court_shared.entity.ParticipantRole;
import kh.edu.paragoniu.court_shared.repository.CaseParticipantRepository;
import kh.edu.paragoniu.court_shared.repository.CaseRepository;
import kh.edu.paragoniu.court_shared.repository.ParticipantRepository;
import kh.edu.paragoniu.court_shared.repository.ParticipantRoleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
public class ParticipantService {

    private static final String PARTY_INDIVIDUAL = "Individual";
    private static final String PARTY_GROUP = "Group";

    private final CaseRepository caseRepository;
    private final CaseParticipantRepository caseParticipantRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantRoleRepository participantRoleRepository;

    public ParticipantService(
        CaseRepository caseRepository,
        CaseParticipantRepository caseParticipantRepository,
        ParticipantRepository participantRepository,
        ParticipantRoleRepository participantRoleRepository
    ) {
        this.caseRepository = caseRepository;
        this.caseParticipantRepository = caseParticipantRepository;
        this.participantRepository = participantRepository;
        this.participantRoleRepository = participantRoleRepository;
    }

    @Transactional(readOnly = true)
    public List<CaseParticipantRow> findCaseParticipants(
        UUID caseId,
        String query,
        Integer roleId
    ) {
        ensureCaseExists(caseId);
        String normalizedQuery = normalize(query);
        return caseParticipantRepository
            .findByIdCaseId(caseId)
            .stream()
            .filter(row ->
                roleId == null ||
                roleId.equals(row.getParticipantRole().getRoleId())
            )
            .filter(row -> matchesQuery(row, normalizedQuery))
            .sorted(
                Comparator.comparing(row ->
                    row.getParticipantEntity().getName().toLowerCase(Locale.ENGLISH)
                )
            )
            .map(this::toRow)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<FilterOption> findRoleOptions() {
        return participantRoleRepository
            .findAll(Sort.by("roleName"))
            .stream()
            .map(role -> new FilterOption(role.getRoleId(), role.getRoleName()))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ParticipantOption> findParticipantOptions(
        UUID caseId,
        String partyType,
        String query
    ) {
        ensureCaseExists(caseId);
        String normalizedPartyType = validatePartyType(partyType);
        String normalizedQuery = normalize(query);
        List<UUID> assignedIds = caseParticipantRepository
            .findByIdCaseId(caseId)
            .stream()
            .map(row -> row.getParticipantEntity().getParticipantId())
            .toList();

        return participantRepository
            .findAll(Sort.by("name"))
            .stream()
            .filter(participant ->
                normalizedPartyType.equalsIgnoreCase(participant.getPartyType())
            )
            .filter(participant ->
                normalizedQuery.isBlank() ||
                participant
                    .getName()
                    .toLowerCase(Locale.ENGLISH)
                    .contains(normalizedQuery)
            )
            .filter(participant -> !assignedIds.contains(participant.getParticipantId()))
            .map(participant ->
                new ParticipantOption(
                    participant.getParticipantId(),
                    participant.getName(),
                    formatContactInfo(participant.getContactInfo())
                )
            )
            .toList();
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "publicCaseDetail", key = "#caseId")
    public void addParticipant(UUID caseId, AddCaseParticipantForm form) {
        Case caseEntity = caseRepository
            .findById(caseId)
            .orElseThrow(() -> new CaseDetailNotFoundException(caseId));
        String partyType = validatePartyType(form.getPartyType());

        Participant participant = participantRepository
            .findById(form.getParticipantId())
            .orElseThrow(() ->
                new CaseParticipantException(
                    "participantId",
                    "Selected participant does not exist."
                )
            );
        if (!partyType.equalsIgnoreCase(participant.getPartyType())) {
            throw new CaseParticipantException(
                "participantId",
                "Selected participant does not match the selected party type."
            );
        }

        ParticipantRole role = participantRoleRepository
            .findById(form.getRoleId())
            .orElseThrow(() ->
                new CaseParticipantException(
                    "roleId",
                    "Selected participant role does not exist."
                )
            );
        CaseParticipantId id = new CaseParticipantId(
            caseId,
            participant.getParticipantId()
        );
        if (caseParticipantRepository.existsById(id)) {
            throw new CaseParticipantException(
                "participantId",
                "This participant is already assigned to the case."
            );
        }

        CaseParticipant caseParticipant = new CaseParticipant();
        caseParticipant.setId(id);
        caseParticipant.setCaseEntity(caseEntity);
        caseParticipant.setParticipantEntity(participant);
        caseParticipant.setParticipantRole(role);
        caseParticipantRepository.save(caseParticipant);
    }

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = "publicCaseDetail", key = "#caseId")
    public void removeParticipant(UUID caseId, UUID participantId) {
        ensureCaseExists(caseId);
        CaseParticipantId id = new CaseParticipantId(caseId, participantId);
        if (!caseParticipantRepository.existsById(id)) {
            throw new CaseParticipantException(
                null,
                "This participant is not assigned to the case."
            );
        }
        caseParticipantRepository.deleteById(id);
    }

    private CaseParticipantRow toRow(CaseParticipant caseParticipant) {
        Participant participant = caseParticipant.getParticipantEntity();
        return new CaseParticipantRow(
            participant.getParticipantId(),
            participant.getName(),
            participant.getPartyType(),
            caseParticipant.getParticipantRole().getRoleName(),
            formatContactInfo(participant.getContactInfo())
        );
    }

    private boolean matchesQuery(CaseParticipant row, String normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            return true;
        }
        return (
            row
                .getParticipantEntity()
                .getName()
                .toLowerCase(Locale.ENGLISH)
                .contains(normalizedQuery) ||
            row
                .getParticipantRole()
                .getRoleName()
                .toLowerCase(Locale.ENGLISH)
                .contains(normalizedQuery)
        );
    }

    private String formatContactInfo(JsonNode contactInfo) {
        if (contactInfo == null || contactInfo.toString().isBlank()) {
            return "N/A";
        }
        String value = contactInfo.toString();
        if ("null".equalsIgnoreCase(value) || "{}".equals(value)) {
            return "N/A";
        }
        return value
            .replace("{", "")
            .replace("}", "")
            .replace("\"", "")
            .replace(",", ", ")
            .replace(":", ": ");
    }

    private void ensureCaseExists(UUID caseId) {
        if (!caseRepository.existsById(caseId)) {
            throw new CaseDetailNotFoundException(caseId);
        }
    }

    private String validatePartyType(String partyType) {
        if (partyType == null || partyType.isBlank()) {
            throw new CaseParticipantException("partyType", "Party type is required.");
        }
        if (PARTY_INDIVIDUAL.equalsIgnoreCase(partyType)) {
            return PARTY_INDIVIDUAL;
        }
        if (PARTY_GROUP.equalsIgnoreCase(partyType)) {
            return PARTY_GROUP;
        }
        throw new CaseParticipantException(
            "partyType",
            "Party type must be Individual or Group."
        );
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }
}
