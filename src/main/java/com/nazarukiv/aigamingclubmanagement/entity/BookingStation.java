package com.nazarukiv.aigamingclubmanagement.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

@Entity
@Table(name = "booking_stations")
public class BookingStation {

    @EmbeddedId
    private BookingStationId id;

    @MapsId("bookingId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @MapsId("stationId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private GamingStation station;

    protected BookingStation() {
    }

    // Persist the booking and station before creating their association.
    public BookingStation(Booking booking, GamingStation station) {
        this.id = new BookingStationId(booking.getId(), station.getId());
        this.booking = booking;
        this.station = station;
    }

    public BookingStationId getId() {
        return id;
    }

    public Booking getBooking() {
        return booking;
    }

    public GamingStation getStation() {
        return station;
    }
}
