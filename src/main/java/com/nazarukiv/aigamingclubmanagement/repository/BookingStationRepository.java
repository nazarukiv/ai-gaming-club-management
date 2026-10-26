package com.nazarukiv.aigamingclubmanagement.repository;

import com.nazarukiv.aigamingclubmanagement.entity.BookingStation;
import com.nazarukiv.aigamingclubmanagement.entity.BookingStationId;
import com.nazarukiv.aigamingclubmanagement.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BookingStationRepository extends JpaRepository<BookingStation, BookingStationId> {

    @Query("""
            select count(bs) > 0 from BookingStation bs
            where bs.station.id in :stationIds
              and bs.booking.status = :status
              and bs.booking.startTime < :endTime
              and bs.booking.endTime > :startTime
            """)
    boolean existsOverlappingBooking(@Param("stationIds") List<Long> stationIds,
                                     @Param("status") BookingStatus status,
                                     @Param("startTime") Instant startTime,
                                     @Param("endTime") Instant endTime);

    List<BookingStation> findByBookingId(Long bookingId);

    List<BookingStation> findByStationId(Long stationId);
}
