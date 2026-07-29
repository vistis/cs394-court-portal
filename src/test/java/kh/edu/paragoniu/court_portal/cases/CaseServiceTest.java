package kh.edu.paragoniu.court_portal.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import kh.edu.paragoniu.court_shared.entity.Case;
import kh.edu.paragoniu.court_shared.entity.CaseClassification;
import kh.edu.paragoniu.court_shared.entity.CaseStatus;
import kh.edu.paragoniu.court_shared.repository.AppealRepository;
import kh.edu.paragoniu.court_shared.repository.CaseClassificationRepository;
import kh.edu.paragoniu.court_shared.repository.CaseJudgeRepository;
import kh.edu.paragoniu.court_shared.repository.CaseRepository;
import kh.edu.paragoniu.court_shared.repository.DispositionOutcomeRepository;
import kh.edu.paragoniu.court_shared.repository.DispositionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Proves createCase only clears the "publicCases" and "caseList" caches
 * after the surrounding transaction commits, not before - the same
 * afterCommit discipline used throughout
 * participants/ParticipantDirectoryService, applied here to close the gap
 * this method used to have (@Transactional stacked with @CacheEvict, no
 * ordering guarantee between the two).
 */
@ExtendWith(MockitoExtension.class)
class CaseServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private CaseRepository caseRepository;

    @Mock
    private CaseClassificationRepository classificationRepository;

    @Mock
    private CaseJudgeRepository caseJudgeRepository;

    @Mock
    private DispositionRepository dispositionRepository;

    @Mock
    private DispositionOutcomeRepository dispositionOutcomeRepository;

    @Mock
    private AppealRepository appealRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void createCaseClearsPublicCasesCacheOnlyAfterCommit() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
            "publicCases",
            "caseList"
        );
        Cache cache = cacheManager.getCache("publicCases");
        cache.put("stale-key", "stale-value");
        Cache caseSearchCache = cacheManager.getCache("caseList");
        caseSearchCache.put("stale-search-key", "stale-search-value");

        CaseService service = new CaseService(
            entityManager,
            caseRepository,
            classificationRepository,
            caseJudgeRepository,
            dispositionRepository,
            dispositionOutcomeRepository,
            appealRepository,
            mongoTemplate,
            cacheManager
        );

        when(classificationRepository.findById(1))
            .thenReturn(Optional.of(new CaseClassification()));

        @SuppressWarnings("unchecked")
        TypedQuery<Long> countQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Long.class)))
            .thenReturn(countQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(0L);
        when(caseRepository.findByCaseNumber(anyString()))
            .thenReturn(Optional.empty());

        @SuppressWarnings("unchecked")
        TypedQuery<Integer> statusQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Integer.class)))
            .thenReturn(statusQuery);
        when(statusQuery.setParameter(anyString(), any())).thenReturn(statusQuery);
        when(statusQuery.getResultStream()).thenReturn(Stream.of(1));
        when(entityManager.getReference(CaseStatus.class, 1))
            .thenReturn(new CaseStatus());

        @SuppressWarnings("unchecked")
        TypedQuery<UUID> caseIdQuery = mock(TypedQuery.class);
        UUID newCaseId = UUID.randomUUID();
        when(entityManager.createQuery(anyString(), eq(UUID.class)))
            .thenReturn(caseIdQuery);
        when(caseIdQuery.setParameter(anyString(), any())).thenReturn(caseIdQuery);
        when(caseIdQuery.getSingleResult()).thenReturn(newCaseId);

        when(caseRepository.saveAndFlush(any(Case.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CreateCaseForm form = new CreateCaseForm();
        form.setTitle("State vs. Henderson");
        form.setDescription("Criminal proceedings.");
        form.setFiledDate(LocalDate.now());
        form.setClassificationId(1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            UUID result = service.createCase(form, UUID.randomUUID());
            assertThat(result).isEqualTo(newCaseId);

            // The INSERT has "happened" but the surrounding transaction has
            // not committed yet - both caches must still hold their stale
            // entries.
            assertThat(cache.get("stale-key")).isNotNull();
            assertThat(caseSearchCache.get("stale-search-key")).isNotNull();

            TransactionSynchronizationManager
                .getSynchronizations()
                .forEach(sync -> sync.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // Only once afterCommit fires should the caches be cleared.
        assertThat(cache.get("stale-key")).isNull();
        assertThat(caseSearchCache.get("stale-search-key")).isNull();
    }
}
