package org.example.mypaintings.painting.placement;


import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public enum PlacementStatus {
    IN_DELIVERY_TO_HOTEL,
    EXHIBITED,
    WAITING_FOR_RECEPTION_BY_CLIENT,
    WAITING_FOR_RECEPTION_BY_ARTIST,
    WAITING_FOR_SHIPPING_TO_CLIENT,
    WAITING_FOR_SHIPPING_TO_ARTIST,
    SENT_BY_HOTEL_TO_ARTIST,
    SENT_BY_HOTEL_TO_CLIENT,
    RECEIVED_BY_CLIENT,
    RETURNED_TO_ARTIST,
    REVOKED_RESERVATION;

    public boolean canTransitionTo(PlacementStatus nextStatus) {
        if (nextStatus == PlacementStatus.REVOKED_RESERVATION && this==IN_DELIVERY_TO_HOTEL) {
            return true;
        }

        return switch (this) {
            case IN_DELIVERY_TO_HOTEL ->
                    nextStatus == EXHIBITED ||
                            nextStatus == WAITING_FOR_RECEPTION_BY_ARTIST ||
                            nextStatus == WAITING_FOR_SHIPPING_TO_ARTIST;

            case EXHIBITED ->
                    nextStatus == WAITING_FOR_RECEPTION_BY_ARTIST ||
                            nextStatus == WAITING_FOR_RECEPTION_BY_CLIENT ||
                            nextStatus == WAITING_FOR_SHIPPING_TO_ARTIST ||
                            nextStatus == WAITING_FOR_SHIPPING_TO_CLIENT;

            case WAITING_FOR_SHIPPING_TO_CLIENT ->
                    nextStatus == SENT_BY_HOTEL_TO_CLIENT;

            case WAITING_FOR_SHIPPING_TO_ARTIST ->
                    nextStatus == SENT_BY_HOTEL_TO_ARTIST;

            case WAITING_FOR_RECEPTION_BY_CLIENT, SENT_BY_HOTEL_TO_CLIENT ->
                    nextStatus == RECEIVED_BY_CLIENT;

            case SENT_BY_HOTEL_TO_ARTIST, WAITING_FOR_RECEPTION_BY_ARTIST ->
                    nextStatus == RETURNED_TO_ARTIST;

            case RECEIVED_BY_CLIENT, RETURNED_TO_ARTIST, REVOKED_RESERVATION ->
                    false;
        };
    }

    public boolean isInStateForReservationRevoke() {
        return this==IN_DELIVERY_TO_HOTEL;
    }

    private static final Set<PlacementStatus> TERMINAL = EnumSet.of(
            RECEIVED_BY_CLIENT, RETURNED_TO_ARTIST, REVOKED_RESERVATION
    );

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean isSoldAndNotObtainedStatus() {
        return this == PlacementStatus.WAITING_FOR_RECEPTION_BY_CLIENT ||
                this == PlacementStatus.WAITING_FOR_SHIPPING_TO_CLIENT ||
                this == PlacementStatus.SENT_BY_HOTEL_TO_CLIENT;
    }


    public boolean isSoldStatus() {
        return isSoldAndNotObtainedStatus() || this == PlacementStatus.RECEIVED_BY_CLIENT;
    }

    public static List<PlacementStatus> getActionRequiredForSaleProcessing() {
        return Arrays.stream(PlacementStatus.values())
                .filter(PlacementStatus::isSoldAndNotObtainedStatus)
                .toList();
    }

    public List<PlacementStatus> getPossibleTransitionsForHotel() {
        return getPossibleTransitions().stream()
                .filter(HOTEL_ACTIONS::contains)
                .toList();
    }


    public List<PlacementStatus> getPossibleTransitions() {
        return Arrays.stream(PlacementStatus.values())
                .filter(this::canTransitionTo)
                .collect(Collectors.toList());
    }

    private static final Set<PlacementStatus> HOTEL_ACTIONS = Set.of(
            EXHIBITED,
            RECEIVED_BY_CLIENT,
            SENT_BY_HOTEL_TO_CLIENT,
            SENT_BY_HOTEL_TO_ARTIST
    );

    public static Set<PlacementStatus> getTerminalStatuses() {
        return TERMINAL;
    }

    private static final Set<PlacementStatus> ARTIST_ACTIONS = Set.of(
            WAITING_FOR_SHIPPING_TO_ARTIST,
            WAITING_FOR_RECEPTION_BY_ARTIST,
            RETURNED_TO_ARTIST
    );

    public List<PlacementStatus> getPossibleTransitionsForArtist() {
        return getPossibleTransitions().stream()
                .filter(ARTIST_ACTIONS::contains)
                .toList();
    }

    public static List<PlacementStatus> getOnStockOrResponsibleStatuses() {
        return List.of(
                IN_DELIVERY_TO_HOTEL,
                EXHIBITED,
                WAITING_FOR_SHIPPING_TO_ARTIST,
                WAITING_FOR_RECEPTION_BY_ARTIST,
                SENT_BY_HOTEL_TO_ARTIST
        );
    }



}