package com.nazarukiv.aigamingclubmanagement.repository;

import com.nazarukiv.aigamingclubmanagement.entity.GamingStation;
import com.nazarukiv.aigamingclubmanagement.enums.StationStatus;
import com.nazarukiv.aigamingclubmanagement.enums.StationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GamingStationRepository extends JpaRepository<GamingStation, Long> {

    List<GamingStation> findByType(StationType type);

    List<GamingStation> findByStatus(StationStatus status);
}
