package org.example.mypaintings.painting.placement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlacementStatusTest {

    @Nested
    @DisplayName("State Graph Contract")
    class StateGraphContractTests {

        @Test
        @DisplayName("No status can transition to itself")
        void noStatusCanTransitionToItself() {
            // when & then
            for (PlacementStatus status : PlacementStatus.values()) {
                assertThat(status.canTransitionTo(status))
                        .as("Status %s should not have a transition to itself", status)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("Every non-terminal status has at least one transition")
        void everyNonTerminalStatusHasAtLeastOneTransition() {
            // when & then
            for (PlacementStatus status : PlacementStatus.values()) {
                if (!status.isTerminal()) {
                    assertThat(status.getPossibleTransitions())
                            .as("Status %s is not terminal, so it should have transitions", status)
                            .isNotEmpty();
                }
            }
        }

        @Test
        @DisplayName("Terminal statuses have no transitions")
        void terminalStatusesHaveNoTransitions() {
            // when & then
            for (PlacementStatus terminal : PlacementStatus.getTerminalStatuses()) {
                assertThat(terminal.getPossibleTransitions())
                        .as("Terminal status %s cannot have any transitions", terminal)
                        .isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Key Business Rules")
    class BusinessRulesTests {

        @Test
        @DisplayName("Reservation can only be revoked during delivery to hotel")
        void reservationCanOnlyBeRevokedDuringDeliveryToHotel() {
            // when
            List<PlacementStatus> statusesAbleToRevoke = Arrays.stream(PlacementStatus.values())
                    .filter(s -> s.canTransitionTo(PlacementStatus.REVOKED_RESERVATION))
                    .toList();

            // then
            assertThat(statusesAbleToRevoke)
                    .containsExactly(PlacementStatus.IN_DELIVERY_TO_HOTEL);
        }

        @Test
        @DisplayName("Sale to client can only be initiated from EXHIBITED")
        void saleToClientCanOnlyBeInitiatedFromExhibited() {
            // when
            List<PlacementStatus> statusesAbleToInitiateSale = Arrays.stream(PlacementStatus.values())
                    .filter(s -> s.canTransitionTo(PlacementStatus.WAITING_FOR_RECEPTION_BY_CLIENT)
                            || s.canTransitionTo(PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT))
                    .toList();

            // then
            assertThat(statusesAbleToInitiateSale)
                    .containsExactly(PlacementStatus.EXHIBITED);
        }

        @Test
        @DisplayName("Painting cannot be handed to client directly from the waiting-for-shipment state")
        void paintingCannotBeHandedToClientDirectlyFromWaitingForShipmentState() {
            // when & then
            assertThat(PlacementStatus.WAITING_FOR_RECEPTION_BY_CLIENT.canTransitionTo(PlacementStatus.RECEIVED_BY_CLIENT)).isTrue();
            assertThat(PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT.canTransitionTo(PlacementStatus.SENT_BY_HOTEL_TO_CLIENT)).isTrue();
            assertThat(PlacementStatus.SENT_BY_HOTEL_TO_CLIENT.canTransitionTo(PlacementStatus.RECEIVED_BY_CLIENT)).isTrue();

            assertThat(PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT.canTransitionTo(PlacementStatus.RECEIVED_BY_CLIENT)).isFalse();
            assertThat(PlacementStatus.WAITING_FOR_RECEPTION_BY_CLIENT.canTransitionTo(PlacementStatus.SENT_BY_HOTEL_TO_CLIENT)).isFalse();
        }

        @Test
        @DisplayName("Return path to artist is consistent")
        void returnPathToArtistIsConsistent() {
            // when & then
            assertThat(PlacementStatus.IN_DELIVERY_TO_HOTEL.canTransitionTo(PlacementStatus.WAITING_FOR_SHIPPING_TO_ARTIST)).isTrue();
            assertThat(PlacementStatus.EXHIBITED.canTransitionTo(PlacementStatus.WAITING_FOR_RECEPTION_BY_ARTIST)).isTrue();
            assertThat(PlacementStatus.WAITING_FOR_SHIPPING_TO_ARTIST.canTransitionTo(PlacementStatus.SENT_BY_HOTEL_TO_ARTIST)).isTrue();
            assertThat(PlacementStatus.WAITING_FOR_RECEPTION_BY_ARTIST.canTransitionTo(PlacementStatus.RETURNED_TO_ARTIST)).isTrue();
            assertThat(PlacementStatus.SENT_BY_HOTEL_TO_ARTIST.canTransitionTo(PlacementStatus.RETURNED_TO_ARTIST)).isTrue();
        }
    }

    @Nested
    @DisplayName("Sale Status Semantics")
    class SaleSemanticsTests {

        @Test
        @DisplayName("Sold but not received consists of exactly three specific statuses")
        void soldButNotReceivedIsExactlyTheseThreeStatuses() {
            // when & then
            assertThat(PlacementStatus.getActionRequiredForSaleProcessing())
                    .containsExactlyInAnyOrder(
                            PlacementStatus.WAITING_FOR_RECEPTION_BY_CLIENT,
                            PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT,
                            PlacementStatus.SENT_BY_HOTEL_TO_CLIENT
                    );
        }

        @Test
        @DisplayName("Sold status equals sold-but-not-received plus received-by-client")
        void soldEqualsSoldButNotReceivedPlusReceivedByClient() {
            // when & then
            for (PlacementStatus status : PlacementStatus.values()) {
                boolean expected = status.isSoldAndNotObtainedStatus()
                        || status == PlacementStatus.RECEIVED_BY_CLIENT;
                assertThat(status.isSoldStatus())
                        .as("isSoldStatus for %s", status)
                        .isEqualTo(expected);
            }
        }
    }

    @Nested
    @DisplayName("Role Filtering & Actions")
    class RoleFilteringTests {

        @Test
        @DisplayName("Artist cannot perform any commercial action")
        void artistCannotPerformAnyCommercialAction() {
            // when & then
            for (PlacementStatus status : PlacementStatus.values()) {
                List<PlacementStatus> artistActions = status.getPossibleTransitionsForArtist();
                assertThat(artistActions)
                        .as("Artist should not be able to perform a commercial action from %s", status)
                        .doesNotContain(
                                PlacementStatus.SENT_BY_HOTEL_TO_CLIENT,
                                PlacementStatus.RECEIVED_BY_CLIENT
                        );
            }
        }

        @ParameterizedTest
        @EnumSource(value = PlacementStatus.class, names = {
                "RECEIVED_BY_CLIENT", "RETURNED_TO_ARTIST", "REVOKED_RESERVATION"
        })
        @DisplayName("Terminal statuses do not allow any actions")
        void terminalStatusesDoNotAllowAnyActions(PlacementStatus terminal) {
            // when & then
            assertThat(terminal.getPossibleTransitionsForArtist()).isEmpty();
            assertThat(terminal.getPossibleTransitionsForHotel()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Additional Helper Methods")
    class AdditionalMethodsTests {

        @Test
        @DisplayName("Should return exact set of statuses considered on stock or responsible")
        void getOnStockOrResponsibleStatuses_shouldReturnCorrectStatuses() {
            // when
            List<PlacementStatus> statuses = PlacementStatus.getOnStockOrResponsibleStatuses();

            // then
            assertThat(statuses).containsExactlyInAnyOrder(
                    PlacementStatus.IN_DELIVERY_TO_HOTEL,
                    PlacementStatus.EXHIBITED,
                    PlacementStatus.WAITING_FOR_SHIPPING_TO_ARTIST,
                    PlacementStatus.WAITING_FOR_RECEPTION_BY_ARTIST,
                    PlacementStatus.SENT_BY_HOTEL_TO_ARTIST
            );
        }

        @ParameterizedTest
        @EnumSource(value = PlacementStatus.class)
        @DisplayName("Should return true only for IN_DELIVERY_TO_HOTEL in reservation revoke check")
        void isInStateForReservationRevoke_shouldValidateCorrectly(PlacementStatus status) {
            // when
            boolean result = status.isInStateForReservationRevoke();

            // then
            if (status == PlacementStatus.IN_DELIVERY_TO_HOTEL) {
                assertThat(result).isTrue();
            } else {
                assertThat(result).isFalse();
            }
        }
    }

    @Nested
    @DisplayName("Key Transitions Matrix")
    class KeyTransitionsTests {

        @ParameterizedTest(name = "Transition from {0} to {1} should be {2}")
        @CsvSource({
                "IN_DELIVERY_TO_HOTEL,               EXHIBITED,                                true",
                "IN_DELIVERY_TO_HOTEL,               REVOKED_RESERVATION,                      true",
                "IN_DELIVERY_TO_HOTEL,               RECEIVED_BY_CLIENT,                       false",
                "EXHIBITED,                           WAITING_FOR_RECEPTION_BY_CLIENT,          true",
                "EXHIBITED,                           WAITING_FOR_SHIPPING_TO_ARTIST,           true",
                "WAITING_FOR_SHIPPING_TO_CLIENT,     SENT_BY_HOTEL_TO_CLIENT,                  true",
                "WAITING_FOR_RECEPTION_BY_CLIENT,    RECEIVED_BY_CLIENT,                       true",
                "SENT_BY_HOTEL_TO_CLIENT,            RECEIVED_BY_CLIENT,                       true",
                "SENT_BY_HOTEL_TO_ARTIST,            RETURNED_TO_ARTIST,                       true",
                "RECEIVED_BY_CLIENT,                 EXHIBITED,                                false",
                "RETURNED_TO_ARTIST,                 EXHIBITED,                                false",
                "REVOKED_RESERVATION,                EXHIBITED,                                false",
        })
        void keyTransitions(PlacementStatus from, PlacementStatus to, boolean expected) {
            // when
            boolean result = from.canTransitionTo(to);

            // then
            assertThat(result)
                    .as("Transition %s -> %s", from, to)
                    .isEqualTo(expected);
        }
    }
}