package org.example.mypaintings.painting.placement;

import org.example.mypaintings.exception.access.UnauthorizedAccessException;
import org.example.mypaintings.exception.painting.IllegalStateTransitionException;
import org.example.mypaintings.painting.Painting;
import org.example.mypaintings.painting.features.*;
import org.example.mypaintings.sale.financial.Money;
import org.example.mypaintings.user.User;
import org.example.mypaintings.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class PaintingPlacementTest {

    private static final long HOTEL_ID = 42L;
    private static final long OTHER_HOTEL_ID = 99L;

    private Painting painting;
    private User hotel;

    @BeforeEach
    void setUp() throws Exception {
        hotel = new User(UserRole.HOTEL, "grandHotel", "Museum Street 1, Amsterdam", 52.3676, 4.9041);
        setId(hotel, HOTEL_ID);

        User otherHotel = new User(UserRole.HOTEL, "otherHotel", "Other Street 2, Paris", 48.8566, 2.3522);
        setId(otherHotel, OTHER_HOTEL_ID);

        User artist = new User(UserRole.ARTIST, "vanGogh", "Artist Street 3", 50.0, 20.0);
        setId(artist, 1L);

        painting = Painting.builder()
                .artist(artist)
                .title("Starry Night")
                .dimensions(new Dimensions(1.0, 1.0, Unit.CM))
                .price(new Money(BigDecimal.TEN, "EUR"))
                .medium(PaintingMedium.OIL)
                .styles(Set.of())
                .build();
        setId(painting, 7L);
    }

    private static void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }

    @Nested
    @DisplayName("startCycle")
    class StartCycleTests {

        @Test
        @DisplayName("Should create placement in initial IN_DELIVERY_TO_HOTEL state with empty note and unarchived")
        void startCycle_shouldCreatePlacementInInitialState() {
            // when
            PaintingPlacement placement = PaintingPlacement.startCycle(painting, hotel);

            // then
            assertThat(placement.getId()).isNull();
            assertThat(placement.getPainting()).isSameAs(painting);
            assertThat(placement.getHotel()).isSameAs(hotel);
            assertThat(placement.getPlacementStatus()).isEqualTo(PlacementStatus.IN_DELIVERY_TO_HOTEL);
            assertThat(placement.getNote()).isEmpty();
            assertThat(placement.isArchived()).isFalse();
            assertThat(placement.getPlacementDate()).isNull();
        }
    }

    @Nested
    @DisplayName("transitionTo - Allowed Transitions")
    class AllowedTransitionsTests {

        @Test
        @DisplayName("Should change status successfully without archiving when transition is allowed")
        void transitionTo_shouldChangeStatus_whenTransitionAllowed() {
            // given
            PaintingPlacement placement = PaintingPlacement.startCycle(painting, hotel);

            // when
            placement.transitionTo(PlacementStatus.EXHIBITED);

            // then
            assertThat(placement.getPlacementStatus()).isEqualTo(PlacementStatus.EXHIBITED);
            assertThat(placement.isArchived()).isFalse();
        }

        @Test
        @DisplayName("Should set archived to true when reaching terminal RECEIVED_BY_CLIENT status")
        void transitionTo_shouldArchive_whenTerminalStatusReached() {
            // given
            PaintingPlacement placement = PaintingPlacement.startCycle(painting, hotel);
            placement.transitionTo(PlacementStatus.EXHIBITED);
            placement.transitionTo(PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT);
            placement.transitionTo(PlacementStatus.SENT_BY_HOTEL_TO_CLIENT);

            // when
            placement.transitionTo(PlacementStatus.RECEIVED_BY_CLIENT);

            // then
            assertThat(placement.getPlacementStatus()).isEqualTo(PlacementStatus.RECEIVED_BY_CLIENT);
            assertThat(placement.isArchived()).isTrue();
        }

        @Test
        @DisplayName("Should set archived to true when reaching terminal RETURNED_TO_ARTIST status")
        void transitionTo_shouldArchive_whenReturnedToArtist() {
            // given
            PaintingPlacement placement = PaintingPlacement.startCycle(painting, hotel);

            // when
            placement.transitionTo(PlacementStatus.WAITING_FOR_SHIPPING_TO_ARTIST);
            placement.transitionTo(PlacementStatus.SENT_BY_HOTEL_TO_ARTIST);
            placement.transitionTo(PlacementStatus.RETURNED_TO_ARTIST);

            // then
            assertThat(placement.getPlacementStatus()).isEqualTo(PlacementStatus.RETURNED_TO_ARTIST);
            assertThat(placement.isArchived()).isTrue();
        }

        @Test
        @DisplayName("Should allow REVOKED_RESERVATION from initial state and archive placement")
        void transitionTo_shouldAllowRevokedReservation_fromInitialState() {
            // given
            PaintingPlacement placement = PaintingPlacement.startCycle(painting, hotel);

            // when
            placement.transitionTo(PlacementStatus.REVOKED_RESERVATION);

            // then
            assertThat(placement.getPlacementStatus()).isEqualTo(PlacementStatus.REVOKED_RESERVATION);
            assertThat(placement.isArchived()).isTrue();
        }
    }

    @Nested
    @DisplayName("transitionTo - Forbidden Transitions & Error Handling")
    class ForbiddenTransitionsTests {

        @Test
        @DisplayName("Should throw IllegalStateTransitionException when transition is not allowed")
        void transitionTo_shouldThrow_whenTransitionNotAllowed() {
            // given
            PaintingPlacement placement = PaintingPlacement.startCycle(painting, hotel);

            // when & then
            assertThatThrownBy(() -> placement.transitionTo(PlacementStatus.RECEIVED_BY_CLIENT))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("Should throw IllegalStateTransitionException when trying to transition from terminal status")
        void transitionTo_shouldThrow_whenFromTerminalStatus() {
            // given
            PaintingPlacement placement = PaintingPlacement.startCycle(painting, hotel);
            placement.transitionTo(PlacementStatus.REVOKED_RESERVATION);

            // when & then
            assertThatThrownBy(() -> placement.transitionTo(PlacementStatus.EXHIBITED))
                    .isInstanceOf(IllegalStateTransitionException.class);
        }

        @Test
        @DisplayName("Should not mutate placement state when transition throws exception")
        void transitionTo_shouldNotMutateState_whenTransitionThrows() {
            // given
            PaintingPlacement placement = PaintingPlacement.startCycle(painting, hotel);

            // when
            Throwable thrown = catchThrowable(() ->
                    placement.transitionTo(PlacementStatus.RECEIVED_BY_CLIENT));

            // then
            assertThat(thrown).isInstanceOf(IllegalStateTransitionException.class);
            assertThat(placement.getPlacementStatus()).isEqualTo(PlacementStatus.IN_DELIVERY_TO_HOTEL);
            assertThat(placement.isArchived()).isFalse();
        }
    }

    @Nested
    @DisplayName("verifyHotelAccess")
    class VerifyHotelAccessTests {

        @Test
        @DisplayName("Should not throw exception when hotel ID matches placement hotel")
        void verifyHotelAccess_shouldNotThrow_whenHotelMatches() {
            // given
            PaintingPlacement placement = PaintingPlacement.startCycle(painting, hotel);

            // when & then
            placement.verifyHotelAccess(HOTEL_ID);
        }

        @Test
        @DisplayName("Should throw UnauthorizedAccessException when hotel ID does not match placement hotel")
        void verifyHotelAccess_shouldThrow_whenHotelDoesNotMatch() {
            // given
            PaintingPlacement placement = PaintingPlacement.startCycle(painting, hotel);

            // when & then
            assertThatThrownBy(() -> placement.verifyHotelAccess(OTHER_HOTEL_ID))
                    .isInstanceOf(UnauthorizedAccessException.class);
        }
    }

    @Nested
    @DisplayName("Note Management")
    class NoteTests {

        @Test
        @DisplayName("Should allow setting a custom note")
        void note_shouldBeSettable() {
            // given
            PaintingPlacement placement = PaintingPlacement.startCycle(painting, hotel);

            // when
            placement.setNote("Delivered in perfect condition");

            // then
            assertThat(placement.getNote()).isEqualTo("Delivered in perfect condition");
        }

        @Test
        @DisplayName("Should initialize note as empty string rather than null")
        void note_shouldBeEmptyString_initially() {
            // when
            PaintingPlacement placement = PaintingPlacement.startCycle(painting, hotel);

            // then
            assertThat(placement.getNote()).isNotNull().isEmpty();
        }
    }
}