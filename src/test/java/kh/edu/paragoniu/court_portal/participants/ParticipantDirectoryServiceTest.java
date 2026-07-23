package kh.edu.paragoniu.court_portal.participants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import kh.edu.paragoniu.court_shared.entity.Participant;
import kh.edu.paragoniu.court_shared.repository.ParticipantRepository;
import kh.edu.paragoniu.court_shared.service.S3Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Proves createParticipant only evicts the directory cache after the surrounding
 * transaction commits, not before. In production the transaction boundary is
 * opened by @Transactional; here we drive TransactionSynchronizationManager
 * directly (it's just a ThreadLocal registry) to simulate that boundary without
 * a real database or Spring context.
 */
@ExtendWith(MockitoExtension.class)
class ParticipantDirectoryServiceTest {

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ObjectProvider<S3Service> s3ServiceProvider;

    @Test
    void createParticipantEvictsDirectoryCacheOnlyAfterCommit() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
            "participantsDirectory"
        );
        Cache cache = cacheManager.getCache("participantsDirectory");
        cache.put("stale-key", "stale-value");

        ParticipantDirectoryService service = new ParticipantDirectoryService(
            participantRepository,
            entityManager,
            cacheManager,
            JsonMapper.builder().build(),
            s3ServiceProvider
        );

        when(participantRepository.save(any(Participant.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CreateParticipantForm form = new CreateParticipantForm();
        form.setPartyType("Individual");
        form.setName("Michael Henderson");
        form.setEmail("m.henderson@email.com");
        form.setPhone("555-0192");

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.createParticipant(form);

            // The INSERT has "happened" (repository.save was called) but the
            // surrounding transaction has not committed yet - the cache must
            // still hold its stale entry at this point.
            assertThat(cache.get("stale-key")).isNotNull();

            TransactionSynchronizationManager
                .getSynchronizations()
                .forEach(sync -> sync.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // Only once afterCommit fires should the cache be cleared.
        assertThat(cache.get("stale-key")).isNull();
    }
}
