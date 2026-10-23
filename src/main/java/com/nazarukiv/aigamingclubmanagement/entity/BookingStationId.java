package com.nazarukiv.aigamingclubmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class BookingStationId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "station_id", nullable = false)
    private Long stationId;

    protected BookingStationId() {
    }

    public BookingStationId(Long bookingId, Long stationId) {
        this.bookingId = bookingId;
        this.stationId = stationId;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public Long getStationId() {
        return stationId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BookingStationId that)) {
            return false;
        }
        return Objects.equals(bookingId, that.bookingId) && Objects.equals(stationId, that.stationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookingId, stationId);
    }
}
