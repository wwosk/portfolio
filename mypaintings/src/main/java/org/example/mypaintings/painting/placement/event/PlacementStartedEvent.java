package org.example.mypaintings.painting.placement.event;

public record PlacementStartedEvent(long paintingId, long hotelId, long acceptedOfferId) {
}