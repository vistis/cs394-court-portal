package kh.edu.paragoniu.court_portal.participants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import kh.edu.paragoniu.court_portal.cases.DocumentService;
import kh.edu.paragoniu.court_shared.entity.Participant;
import kh.edu.paragoniu.court_shared.repository.ParticipantRepository;
import kh.edu.paragoniu.court_shared.service.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockMultipartFile;
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

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private DocumentService documentService;

    @Mock
    private Query duplicateEmailQuery;

    /**
     * createParticipant always runs the duplicate-email check first, so every
     * existing test below needs this stubbed even though most of them aren't
     * "about" duplicates - lenient() so it doesn't trip strict-stubbing on
     * tests that never reach save() at all.
     */
    @BeforeEach
    void stubNoDuplicateEmailByDefault() {
        lenient()
            .when(entityManager.createNativeQuery(anyString()))
            .thenReturn(duplicateEmailQuery);
        lenient()
            .when(duplicateEmailQuery.setParameter(anyString(), any()))
            .thenReturn(duplicateEmailQuery);
        lenient().when(duplicateEmailQuery.getSingleResult()).thenReturn(0L);
    }

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
            s3ServiceProvider,
            mongoTemplate,
            documentService
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

    @Test
    void createParticipantWithPhotoUploadsUnderParticipantsPrefix() {
        S3Service s3Service = org.mockito.Mockito.mock(S3Service.class);
        when(s3ServiceProvider.getIfAvailable()).thenReturn(s3Service);
        when(s3Service.uploadFile(eq("participants"), anyString(), any(), any()))
            .thenReturn("participants/generated-key.jpg");
        when(participantRepository.save(any(Participant.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ParticipantDirectoryService service = new ParticipantDirectoryService(
            participantRepository,
            entityManager,
            new ConcurrentMapCacheManager("participantsDirectory"),
            JsonMapper.builder().build(),
            s3ServiceProvider,
            mongoTemplate,
            documentService
        );

        CreateParticipantForm form = new CreateParticipantForm();
        form.setPartyType("Individual");
        form.setName("Michael Henderson");
        form.setEmail("m.henderson@email.com");
        form.setPhone("555-0192");
        form.setProfileImage(
            new MockMultipartFile(
                "profileImage",
                "photo.jpg",
                "image/jpeg",
                "fake-image-bytes".getBytes()
            )
        );

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.createParticipant(form);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        ArgumentCaptor<Participant> captor = ArgumentCaptor.forClass(Participant.class);
        verify(participantRepository).save(captor.capture());
        assertThat(captor.getValue().getProfilePicturePath())
            .isEqualTo("participants/generated-key.jpg");
    }

    @Test
    void createParticipantWithoutPhotoStoresSentinelNotNull() {
        when(participantRepository.save(any(Participant.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ParticipantDirectoryService service = new ParticipantDirectoryService(
            participantRepository,
            entityManager,
            new ConcurrentMapCacheManager("participantsDirectory"),
            JsonMapper.builder().build(),
            s3ServiceProvider,
            mongoTemplate,
            documentService
        );

        CreateParticipantForm form = new CreateParticipantForm();
        form.setPartyType("Group");
        form.setName("TechFlow Inc.");
        form.setEmail("legal@techflow.io");
        form.setPhone("555-0111");

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.createParticipant(form);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // participants.profile_picture_path is NOT NULL at the DB level (matches
        // Judge/Lawyer/User), so "no photo" is represented by the "N/A" sentinel
        // already established for this column, never a null value.
        ArgumentCaptor<Participant> captor = ArgumentCaptor.forClass(Participant.class);
        verify(participantRepository).save(captor.capture());
        assertThat(captor.getValue().getProfilePicturePath()).isEqualTo("N/A");
        verify(s3ServiceProvider, never()).getIfAvailable();
    }

    @Test
    void createParticipantRejectsDuplicateEmail() {
        when(duplicateEmailQuery.getSingleResult()).thenReturn(1L);

        ParticipantDirectoryService service = new ParticipantDirectoryService(
            participantRepository,
            entityManager,
            new ConcurrentMapCacheManager("participantsDirectory"),
            JsonMapper.builder().build(),
            s3ServiceProvider,
            mongoTemplate,
            documentService
        );

        CreateParticipantForm form = new CreateParticipantForm();
        form.setPartyType("Individual");
        form.setName("riri");
        form.setEmail("naazaa070@gmail.com");
        form.setPhone("555-0192");

        assertThatThrownBy(() -> service.createParticipant(form))
            .isInstanceOf(ParticipantDirectoryException.class)
            .extracting(ex -> ((ParticipantDirectoryException) ex).getFieldName())
            .isEqualTo("email");

        verify(participantRepository, never()).save(any());
        verify(s3ServiceProvider, never()).getIfAvailable();
    }
}
