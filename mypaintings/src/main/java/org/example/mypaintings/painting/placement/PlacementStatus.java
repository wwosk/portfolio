package org.example.mypaintings.painting.placement;


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


}