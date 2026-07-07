package org.example.mypaintings.sale.event;


public record PaintingSoldEvent(long paintingId, boolean deliveryNeeded) {
}