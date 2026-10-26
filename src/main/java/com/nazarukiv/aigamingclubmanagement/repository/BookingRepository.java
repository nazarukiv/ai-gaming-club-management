package com.nazarukiv.aigamingclubmanagement.repository;

import com.nazarukiv.aigamingclubmanagement.entity.Booking;
import com.nazarukiv.aigamingclubmanagement.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    List<Booking> findByUserId(Long userId);

    List<Booking> findByStatus(BookingStatus status);
}
