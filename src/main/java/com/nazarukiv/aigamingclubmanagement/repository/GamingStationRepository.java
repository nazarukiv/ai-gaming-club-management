package com.nazarukiv.aigamingclubmanagement.repository;

import com.nazarukiv.aigamingclubmanagement.entity.GamingStation;
import com.nazarukiv.aigamingclubmanagement.enums.BookingStatus;
import com.nazarukiv.aigamingclubmanagement.enums.StationStatus;
import com.nazarukiv.aigamingclubmanagement.enums.StationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GamingStationRepository extends JpaRepository<GamingStation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GamingStation s where s.id = :id")
    Optional<GamingStation> findByIdForUpdate(@Param("id") Long id);

    List<GamingStation> findByType(StationType type);

    List<GamingStation> findByStatus(StationStatus status);

    // Same half-open overlap rule as BookingStationRepository.existsOverlappingBooking.
    @Query("""
            select s from GamingStation s
            where s.type = :type and s.status = :stationStatus
              and not exists (
                  select bs.id from BookingStation bs
                  where bs.station = s
                    and bs.booking.status = :bookingStatus
                    and bs.booking.startTime < :endTime
                    and bs.booking.endTime > :startTime
              )
            order by s.stationNumber
            """)
    List<GamingStation> findAvailableStations(@Param("type") StationType type,
                                             @Param("stationStatus") StationStatus stationStatus,
                                             @Param("bookingStatus") BookingStatus bookingStatus,
                                             @Param("startTime") Instant startTime,
                                             @Param("endTime") Instant endTime);
}
