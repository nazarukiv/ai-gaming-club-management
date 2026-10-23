package com.nazarukiv.aigamingclubmanagement.repository;

import com.nazarukiv.aigamingclubmanagement.entity.BookingStation;
import com.nazarukiv.aigamingclubmanagement.entity.BookingStationId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingStationRepository extends JpaRepository<BookingStation, BookingStationId> {

    List<BookingStation> findByBookingId(Long bookingId);

    List<BookingStation> findByStationId(Long stationId);
}
