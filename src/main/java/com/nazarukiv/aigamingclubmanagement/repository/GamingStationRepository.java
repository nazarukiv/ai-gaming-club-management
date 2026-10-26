package com.nazarukiv.aigamingclubmanagement.repository;

import com.nazarukiv.aigamingclubmanagement.entity.GamingStation;
import com.nazarukiv.aigamingclubmanagement.enums.StationStatus;
import com.nazarukiv.aigamingclubmanagement.enums.StationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GamingStationRepository extends JpaRepository<GamingStation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GamingStation s where s.id = :id")
    Optional<GamingStation> findByIdForUpdate(@Param("id") Long id);

    List<GamingStation> findByType(StationType type);

    List<GamingStation> findByStatus(StationStatus status);
}
