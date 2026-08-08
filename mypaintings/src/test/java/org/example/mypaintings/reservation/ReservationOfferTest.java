package org.example.mypaintings.reservation;

import org.example.mypaintings.exception.painting.IllegalStateTransitionException;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.example.mypaintings.reservation.ReservationStatus.*;

@ExtendWith(MockitoExtension.class)
class ReservationOfferTest {

    @Mock
    private Painting painting;

    @Mock
    private User hotel;

    private ReservationOffer offer;

    @BeforeEach
    void setUp() {
        // given
        offer = ReservationOffer.builder()
                .painting(painting)
                .hotel(hotel)
                .status(PENDING)
                .offerDate(Instant.now())
                .build();
    }

    @Nested
    @DisplayName("Builder and Constructor")
    class BuilderTests {

        @Test
        @DisplayName("Should default status to PENDING when provided status is null")
        void builderDefaultsToPending() {
            // when
            ReservationOffer result = ReservationOffer.builder()
                    .painting(painting)
                    .hotel(hotel)
                    .status(null)
                    .build();

            // then
            assertThat(result.getStatus()).isEqualTo(PENDING);
        }

        @Test
        @DisplayName("Should default offerDate to current time when provided date is null")
        void builderDefaultsToNow() {
            // given
            Instant before = Instant.now();

            // when
            ReservationOffer result = ReservationOffer.builder()
                    .painting(painting)
                    .hotel(hotel)
                    .status(PENDING)
                    .offerDate(null)
                    .build();

            // then
            assertThat(result.getOfferDate()).isNotNull();
            assertThat(result.getOfferDate()).isAfterOrEqualTo(before);
        }

        @Test
        @DisplayName("Should keep provided explicit status")
        void builderKeepsProvidedStatus() {
            // when
            ReservationOffer result = ReservationOffer.builder()
                    .painting(painting)
                    .hotel(hotel)
                    .status(ACCEPTED)
                    .build();

            // then
            assertThat(result.getStatus()).isEqualTo(ACCEPTED);
        }
    }

    @Nested
    @DisplayName("Entity State Transition Methods")
    class EntityTransitions {

        @ParameterizedTest(name = "PENDING -> {0} should be executed successfully on entity")
        @EnumSource(value = ReservationStatus.class,
                names = {"ACCEPTED", "REJECTED", "CANCELLED", "ARCHIVED"})
        void pendingCanTransitionToAllowed(ReservationStatus target) {
            // given
            setStatus(PENDING);

            // when
            applyTransition(target);

            // then
            assertThat(offer.getStatus()).isEqualTo(target);
        }

        @Test
        @DisplayName("ACCEPTED -> REVOKED should be executed successfully on entity")
        void acceptedToRevoked() {
            // given
            setStatus(ACCEPTED);

            // when
            offer.revoke();

            // then
            assertThat(offer.getStatus()).isEqualTo(REVOKED);
        }

        @ParameterizedTest(name = "REJECTED -> {0} should be executed successfully on entity")
        @EnumSource(value = ReservationStatus.class, names = {"PENDING", "ARCHIVED"})
        void rejectedCanTransitionTo(ReservationStatus target) {
            // given
            setStatus(REJECTED);

            // when
            applyTransition(target);

            // then
            assertThat(offer.getStatus()).isEqualTo(target);
        }

        @ParameterizedTest(name = "CANCELLED -> {0} should be executed successfully on entity")
        @EnumSource(value = ReservationStatus.class, names = {"PENDING", "ARCHIVED"})
        void cancelledCanTransitionTo(ReservationStatus target) {
            // given
            setStatus(CANCELLED);

            // when
            applyTransition(target);

            // then
            assertThat(offer.getStatus()).isEqualTo(target);
        }

        @ParameterizedTest(name = "REVOKED -> {0} should be executed successfully on entity")
        @EnumSource(value = ReservationStatus.class, names = {"PENDING", "ARCHIVED"})
        void revokedCanTransitionTo(ReservationStatus target) {
            // given
            setStatus(REVOKED);

            // when
            applyTransition(target);

            // then
            assertThat(offer.getStatus()).isEqualTo(target);
        }

        @ParameterizedTest(name = "{0} -> {0} should be executed successfully (same state)")
        @EnumSource(ReservationStatus.class)
        @DisplayName("Transitioning entity to the exact same state is allowed")
        void sameStateIsAllowed(ReservationStatus status) {
            // given
            setStatus(status);

            // when
            applyTransition(status);

            // then
            assertThat(offer.getStatus()).isEqualTo(status);
        }
    }

    @Nested
    @DisplayName("Entity Invalid Transitions and Exceptions")
    class EntityForbiddenTransitions {

        @Test
        @DisplayName("PENDING -> REVOKED via entity method should throw IllegalStateTransitionException")
        void pendingCannotBeRevoked() {
            // given
            setStatus(PENDING);

            // when & then
            assertThatThrownBy(() -> offer.revoke())
                    .isInstanceOf(IllegalStateTransitionException.class);
            assertThat(offer.getStatus()).isEqualTo(PENDING);
        }

        @ParameterizedTest(name = "ACCEPTED -> {0} via entity method should throw IllegalStateTransitionException")
        @EnumSource(value = ReservationStatus.class,
                names = {"PENDING", "REJECTED", "CANCELLED", "ARCHIVED"})
        void acceptedCannotTransitionToOthers(ReservationStatus target) {
            // given
            setStatus(ACCEPTED);

            // when & then
            assertThatThrownBy(() -> applyTransition(target))
                    .isInstanceOf(IllegalStateTransitionException.class);
            assertThat(offer.getStatus()).isEqualTo(ACCEPTED);
        }

        @ParameterizedTest(name = "ARCHIVED -> {0} via entity method should throw IllegalStateTransitionException")
        @EnumSource(value = ReservationStatus.class,
                names = {"PENDING", "ACCEPTED", "REJECTED", "CANCELLED", "REVOKED"})
        void archivedIsTerminal(ReservationStatus target) {
            // given
            setStatus(ARCHIVED);

            // when & then
            assertThatThrownBy(() -> applyTransition(target))
                    .isInstanceOf(IllegalStateTransitionException.class);
            assertThat(offer.getStatus()).isEqualTo(ARCHIVED);
        }

        @Test
        @DisplayName("REJECTED -> ACCEPTED via entity method should throw IllegalStateTransitionException")
        void rejectedCannotBeAccepted() {
            // given
            setStatus(REJECTED);

            // when & then
            assertThatThrownBy(() -> offer.accept())
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("CANCELLED -> ACCEPTED via entity method should throw IllegalStateTransitionException")
        void cancelledCannotBeAccepted() {
            // given
            setStatus(CANCELLED);

            // when & then
            assertThatThrownBy(() -> offer.accept())
                    .isInstanceOf(IllegalStateTransitionException.class);
        }
    }

    @Nested
    @DisplayName("Entity Edge Cases and Error Handling")
    class EntityEdgeCases {

        @Test
        @DisplayName("Should throw IllegalStateException when entity status is null during transition attempt")
        void nullStatusThrowsIllegalStateException() {
            // given
            ReservationOffer nullStatusOffer = ReservationOffer.builder()
                    .painting(painting)
                    .hotel(hotel)
                    .status(null)
                    .build();
            setStatusViaReflection(nullStatusOffer, null);

            // when & then
            assertThatThrownBy(nullStatusOffer::accept)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no status");
        }

        @Test
        @DisplayName("IllegalStateTransitionException thrown by entity should contain source and target state names")
        void exceptionMessageContainsStates() {
            // given
            setStatus(ARCHIVED);

            // when & then
            assertThatThrownBy(() -> offer.cancel())
                    .isInstanceOf(IllegalStateTransitionException.class)
                    .hasMessageContaining("ARCHIVED")
                    .hasMessageContaining("CANCELLED");
        }
    }

    private void setStatus(ReservationStatus status) {
        setStatusViaReflection(offer, status);
    }

    private static void setStatusViaReflection(ReservationOffer target, ReservationStatus status) {
        try {
            var field = ReservationOffer.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(target, status);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set status via reflection", e);
        }
    }

    private void applyTransition(ReservationStatus target) {
        switch (target) {
            case PENDING -> offer.retryCreatingOffer();
            case ACCEPTED -> offer.accept();
            case REJECTED -> offer.reject();
            case CANCELLED -> offer.cancel();
            case REVOKED -> offer.revoke();
            case ARCHIVED -> offer.archive();
        }
    }
}