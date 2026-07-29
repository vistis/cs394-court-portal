package kh.edu.paragoniu.court_portal.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import kh.edu.paragoniu.court_shared.entity.Case;
import kh.edu.paragoniu.court_shared.entity.CaseParticipant;
import kh.edu.paragoniu.court_shared.entity.Participant;
import kh.edu.paragoniu.court_shared.entity.ParticipantRole;
import kh.edu.paragoniu.court_shared.repository.CaseParticipantRepository;
import kh.edu.paragoniu.court_shared.repository.CaseRepository;
import kh.edu.paragoniu.court_shared.repository.ParticipantRepository;
import kh.edu.paragoniu.court_shared.repository.ParticipantRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Proves addParticipant only clears "caseParticipants" (and the other caches
 * it's responsible for) after the surrounding transaction commits, not
 * before - same afterCommit discipline as the rest of this module. This is
 * the newly added eviction target: findCaseParticipants just gained its own
 * @Cacheable, so writes here now need to invalidate it too.
 */
@ExtendWith(MockitoExtension.class)
class ParticipantServiceTest {

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private CaseParticipantRepository caseParticipantRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private ParticipantRoleRepository participantRoleRepository;

    @Test
    void addParticipantClearsCaseParticipantsCacheOnlyAfterCommit() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
            "caseParticipants",
            "participantsDirectory",
            "publicCaseDetail"
        );
        Cache caseParticipantsCache = cacheManager.getCache("caseParticipants");
        caseParticipantsCache.put("stale-key", "stale-value");

        ParticipantService service = new ParticipantService(
            caseRepository,
            caseParticipantRepository,
            participantRepository,
            participantRoleRepository,
            cacheManager
        );

        UUID caseId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        when(caseRepository.findById(caseId)).thenReturn(Optional.of(new Case()));

        Participant participant = new Participant();
        participant.setParticipantId(participantId);
        participant.setPartyType("Individual");
        when(participantRepository.findById(participantId))
            .thenReturn(Optional.of(participant));

        ParticipantRole role = new ParticipantRole();
        when(participantRoleRepository.findById(4)).thenReturn(Optional.of(role));

        when(caseParticipantRepository.existsById(any())).thenReturn(false);
        when(caseParticipantRepository.save(any(CaseParticipant.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        AddCaseParticipantForm form = new AddCaseParticipantForm();
        form.setPartyType("Individual");
        form.setParticipantId(participantId);
        form.setRoleId(4);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.addParticipant(caseId, form);

            // The INSERT has "happened" but the surrounding transaction has
            // not committed yet - the cache must still hold its stale entry.
            assertThat(caseParticipantsCache.get("stale-key")).isNotNull();

            TransactionSynchronizationManager
                .getSynchronizations()
                .forEach(sync -> sync.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // Only once afterCommit fires should the cache be cleared.
        assertThat(caseParticipantsCache.get("stale-key")).isNull();
    }
}
