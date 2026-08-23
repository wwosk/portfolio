package org.example.mypaintings.painting;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PessimisticLockException;
import org.example.mypaintings.exception.notfound.ResourceNotFoundException;
import org.example.mypaintings.painting.attachedimages.AttachedImage;
import org.example.mypaintings.painting.features.Dimensions;
import org.example.mypaintings.painting.features.PaintingMedium;
import org.example.mypaintings.painting.features.Unit;
import org.example.mypaintings.painting.styles.PaintingStyle;
import org.example.mypaintings.sale.financial.Money;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:painting_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.show-sql=false",
        "spring.test.database.replace=none"
})
class PaintingRepositoryTest {

    private static final long LATCH_TIMEOUT_SECONDS = 10L;
    private static final long FUTURE_TIMEOUT_SECONDS = 15L;
    private static final long LOCK_HOLD_MILLIS = 500L;

    @Autowired
    private PaintingRepository paintingRepository;

    @Autowired
    private TestEntityManager tem;

    @Autowired
    private PlatformTransactionManager txManager;

    @PersistenceContext
    private EntityManager em;

    private User artist;

    @BeforeEach
    void setUp() {
        // given
        em.createNativeQuery("DELETE FROM painting_painting_styles").executeUpdate();
        em.createNativeQuery("DELETE FROM attached_image").executeUpdate();
        em.createNativeQuery("DELETE FROM painting").executeUpdate();
        em.createNativeQuery("DELETE FROM painting_styles").executeUpdate();
        em.createNativeQuery("DELETE FROM app_users").executeUpdate();

        artist = new User(UserRole.ARTIST, "artist1", "Street 1, City", 50.0, 19.0);
        artist = tem.persistAndFlush(artist);
        tem.clear();
    }

    private PaintingStyle newStyle(String name) {
        return new PaintingStyle(name);
    }

    private AttachedImage newAttachment(String fileKey) {
        AttachedImage img = new AttachedImage();
        img.setFileKey(fileKey);
        img.setOriginalFileName(fileKey + ".jpg");
        img.setContentType("image/jpeg");
        img.setFileSize(1024L);
        return img;
    }

    private Painting buildPainting(String title, User artist,
                                   List<PaintingStyle> styles,
                                   List<AttachedImage> attachments) {
        Dimensions dimensions = new Dimensions(100.0, 80.0, Unit.CM);

        Painting painting = Painting.builder()
                .title(title)
                .price(new Money(new BigDecimal("1500.00"), "PLN"))
                .dimensions(dimensions)
                .artist(artist)
                .medium(PaintingMedium.OIL)
                .description("desc of " + title)
                .build();

        styles.forEach(painting::addStyle);
        attachments.forEach(painting::addAttachment);
        return painting;
    }

    private Painting persistPainting(String title) {
        PaintingStyle style = newStyle("style-" + title);
        tem.persist(style);
        AttachedImage img = newAttachment("key-" + title);
        tem.persistAndFlush(img);

        Painting p = buildPainting(title, artist, List.of(style), List.of(img));
        Painting saved = paintingRepository.saveAndFlush(p);
        tem.clear();
        return saved;
    }

    private Long setupCommittedPainting(String title) {
        TransactionTemplate setup = new TransactionTemplate(txManager);
        setup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        return setup.execute(status -> {
            User a = new User(UserRole.ARTIST, "artist-" + title, "Street 1, City", 50.0, 19.0);
            em.persist(a);

            PaintingStyle style = newStyle("style-" + title);
            em.persist(style);

            AttachedImage img = newAttachment("key-" + title);
            em.persist(img);

            Painting painting = Painting.builder()
                    .title(title)
                    .price(new Money(new BigDecimal("1500.00"), "PLN"))
                    .dimensions(new Dimensions(100.0, 80.0, Unit.CM))
                    .artist(a)
                    .medium(PaintingMedium.OIL)
                    .description("desc")
                    .build();
            painting.addStyle(style);
            painting.addAttachment(img);

            em.persist(painting);
            em.flush();
            return painting.getId();
        });
    }

    @Test
    @DisplayName("saveAndFlush and getOrThrow should return saved painting")
    void saveAndGetOrThrow_returnsPainting() {
        // given
        Painting saved = persistPainting("Mona Lisa");

        // when
        Painting found = paintingRepository.getOrThrow(saved.getId());

        // then
        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getTitle()).isEqualTo("Mona Lisa");
        assertThat(found.getArtist().getId()).isEqualTo(artist.getId());
        assertThat(found.getSizeCategory()).isNotNull();
        assertThat(found.getOrientation()).isNotNull();
    }

    @Test
    @DisplayName("getOrThrow should throw ResourceNotFoundException for missing id")
    void getOrThrow_throwsWhenMissing() {
        // given / when / then
        assertThatThrownBy(() -> paintingRepository.getOrThrow(999_999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("countByArtistId should return correct count for artist")
    void countByArtistId_counts() {
        // given
        persistPainting("A");
        persistPainting("B");

        // when / then
        assertThat(paintingRepository.countByArtistId(artist.getId())).isEqualTo(2L);
        assertThat(paintingRepository.countByArtistId(999_999L)).isZero();
    }

    @Test
    @DisplayName("findByIdWithDetails should fetch painting with attachments, artist and styles")
    void findByIdWithDetails_fetchesAllRelations() {
        // given
        Painting saved = persistPainting("Starry Night");

        // when
        Painting withDetails = paintingRepository.getWithDetailsOrThrow(saved.getId());

        // then
        assertThat(withDetails.getAttachments()).hasSize(1);
        assertThat(withDetails.getStyles()).hasSize(1);
        assertThat(withDetails.getArtist().getNickname()).isEqualTo("artist1");
    }

    @Test
    @DisplayName("getWithDetailsOrThrow should throw ResourceNotFoundException when missing")
    void getWithDetailsOrThrow_throws() {
        // given / when / then
        assertThatThrownBy(() -> paintingRepository.getWithDetailsOrThrow(123L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findAllByArtistIdWithAttachments should filter by artist and load attachments")
    void findAllByArtistIdWithAttachments_filtersByArtist() {
        // given
        persistPainting("P1");
        persistPainting("P2");

        User otherArtist = new User(UserRole.ARTIST, "artist2", "Other 2, City", 51.0, 20.0);
        tem.persistAndFlush(otherArtist);

        PaintingStyle style = newStyle("other-style");
        tem.persist(style);
        AttachedImage img = newAttachment("key-other");
        Painting other = buildPainting("Other", otherArtist, List.of(style), List.of(img));
        paintingRepository.saveAndFlush(other);
        tem.clear();

        // when
        List<Painting> result = paintingRepository.findAllByArtistIdWithAttachments(artist.getId());

        // then
        assertThat(result).hasSize(2)
                .extracting(Painting::getTitle)
                .containsExactlyInAnyOrder("P1", "P2");
        assertThat(result).allSatisfy(p ->
                assertThat(p.getAttachments()).isNotEmpty());
    }

    @Test
    @DisplayName("findAllByIdsWithDetails should return details for requested ids")
    void findAllByIdsWithDetails_returnsDetails() {
        // given
        Painting p1 = persistPainting("X");
        Painting p2 = persistPainting("Y");
        persistPainting("Z");

        // when
        List<Painting> result =
                paintingRepository.findAllByIdsWithDetails(List.of(p1.getId(), p2.getId()));

        // then
        assertThat(result).hasSize(2)
                .extracting(Painting::getTitle)
                .containsExactlyInAnyOrder("X", "Y");
        assertThat(result).allSatisfy(p -> {
            assertThat(p.getAttachments()).isNotEmpty();
            assertThat(p.getStyles()).isNotEmpty();
            assertThat(p.getArtist()).isNotNull();
        });
    }

    @Test
    @DisplayName("findByIdWithLock should allow two consecutive transactions to read record")
    void findByIdWithLock_shouldLockRecord() throws Exception {
        // given
        Long paintingId = setupCommittedPainting("Locked Painting");
        TransactionTemplate tx = new TransactionTemplate(txManager);
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        CountDownLatch lockAcquiredLatch = new CountDownLatch(1);
        CountDownLatch releaseLockLatch = new CountDownLatch(1);

        try {
            // when
            Future<String> thread1 = executorService.submit(() ->
                    tx.execute(status -> {
                        Painting p = paintingRepository.findByIdWithLock(paintingId)
                                .orElseThrow(() -> new AssertionError("Painting not found"));
                        lockAcquiredLatch.countDown();

                        try {
                            if (!releaseLockLatch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                                throw new AssertionError("releaseLockLatch timeout in thread1");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new AssertionError("thread1 interrupted", e);
                        }
                        return p.getTitle();
                    })
            );

            Future<String> thread2 = executorService.submit(() -> {
                try {
                    if (!lockAcquiredLatch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        throw new AssertionError("lockAcquiredLatch timeout in thread2");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("thread2 interrupted", e);
                }

                return tx.execute(status -> {
                    Painting p = paintingRepository.findByIdWithLock(paintingId)
                            .orElseThrow(() -> new AssertionError("Painting not found in thread2"));
                    return p.getTitle();
                });
            });

            Thread.sleep(200);
            releaseLockLatch.countDown();

            String result1 = thread1.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String result2 = thread2.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // then
            assertThat(result1).isEqualTo("Locked Painting");
            assertThat(result2).isEqualTo("Locked Painting");
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    @DisplayName("findByIdWithLock should block second transaction until lock is released")
    void findByIdWithLock_actuallyBlocksSecondTransaction() throws Exception {
        // given
        Long paintingId = setupCommittedPainting("Blocking Painting");
        TransactionTemplate tx = new TransactionTemplate(txManager);

        CountDownLatch lockAcquired = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            // when
            Future<?> holder = pool.submit(() -> tx.execute(status -> {
                paintingRepository.findByIdWithLock(paintingId).orElseThrow();
                lockAcquired.countDown();
                try {
                    releaseLock.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));

            assertThat(lockAcquired.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            AtomicReference<Long> waitMillis = new AtomicReference<>();
            Future<?> waiter = pool.submit(() -> tx.execute(status -> {
                long start = System.nanoTime();
                paintingRepository.findByIdWithLock(paintingId).orElseThrow();
                waitMillis.set(Duration.ofNanos(System.nanoTime() - start).toMillis());
                return null;
            }));

            Thread.sleep(LOCK_HOLD_MILLIS);
            assertThat(waiter.isDone()).isFalse();

            releaseLock.countDown();
            holder.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            waiter.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // then
            assertThat(waitMillis.get()).isGreaterThanOrEqualTo(LOCK_HOLD_MILLIS - 100L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("findByIdWithLock should release lock after transaction rollback")
    void findByIdWithLock_releasedAfterRollback() throws Exception {
        // given
        Long paintingId = setupCommittedPainting("Rollback Painting");
        TransactionTemplate tx = new TransactionTemplate(txManager);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            CountDownLatch lockAcquired = new CountDownLatch(1);

            // when
            Future<?> holder = pool.submit(() -> {
                try {
                    tx.execute(status -> {
                        paintingRepository.findByIdWithLock(paintingId).orElseThrow();
                        lockAcquired.countDown();
                        throw new RuntimeException("force rollback");
                    });
                } catch (RuntimeException ignored) {
                }
            });

            assertThat(lockAcquired.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            holder.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            long start = System.nanoTime();
            tx.execute(status -> {
                paintingRepository.findByIdWithLock(paintingId).orElseThrow();
                return null;
            });
            long elapsed = Duration.ofNanos(System.nanoTime() - start).toMillis();

            // then
            assertThat(elapsed).isLessThan(1_000L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("findByIdWithLock should return empty optional when id is missing")
    void findByIdWithLock_missingId_returnsEmpty() {
        // given / when
        Optional<Painting> result = paintingRepository.findByIdWithLock(999_999L);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByIdWithLock should block UPDATE query from another transaction")
    void findByIdWithLock_blocksUpdateFromOtherTransaction() throws Exception {
        // given
        Long paintingId = setupCommittedPainting("Write Lock Painting");
        TransactionTemplate tx = new TransactionTemplate(txManager);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            CountDownLatch lockAcquired = new CountDownLatch(1);
            CountDownLatch releaseLock = new CountDownLatch(1);

            // when
            Future<?> holder = pool.submit(() -> tx.execute(status -> {
                paintingRepository.findByIdWithLock(paintingId).orElseThrow();
                lockAcquired.countDown();
                try {
                    releaseLock.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));

            assertThat(lockAcquired.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            AtomicBoolean updateDone = new AtomicBoolean(false);
            Future<?> updater = pool.submit(() -> tx.execute(status -> {
                em.createNativeQuery("UPDATE painting SET title = 'changed' WHERE id = :id")
                        .setParameter("id", paintingId)
                        .executeUpdate();
                updateDone.set(true);
                return null;
            }));

            Thread.sleep(LOCK_HOLD_MILLIS);
            assertThat(updateDone.get()).isFalse();

            releaseLock.countDown();
            holder.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            updater.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // then
            assertThat(updateDone.get()).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @Disabled("Requires jakarta.persistence.lock.timeout configured in repository")
    @DisplayName("findByIdWithLock should throw PessimisticLockException on lock timeout")
    void findByIdWithLock_lockTimeout_throws() throws Exception {
        // given
        Long paintingId = setupCommittedPainting("Timeout Painting");
        TransactionTemplate tx = new TransactionTemplate(txManager);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            CountDownLatch lockAcquired = new CountDownLatch(1);
            CountDownLatch releaseLock = new CountDownLatch(1);

            // when
            Future<?> holder = pool.submit(() -> tx.execute(status -> {
                paintingRepository.findByIdWithLock(paintingId).orElseThrow();
                lockAcquired.countDown();
                try {
                    releaseLock.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            }));

            assertThat(lockAcquired.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            // then
            assertThatThrownBy(() -> tx.execute(status -> {
                paintingRepository.findByIdWithLock(paintingId).orElseThrow();
                return null;
            })).isInstanceOf(PessimisticLockException.class);

            releaseLock.countDown();
            holder.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }
}