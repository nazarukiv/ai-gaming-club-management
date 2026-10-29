package com.nazarukiv.aigamingclubmanagement.service;

import com.nazarukiv.aigamingclubmanagement.dto.AvailabilityResponse;
import com.nazarukiv.aigamingclubmanagement.enums.BookingStatus;
import com.nazarukiv.aigamingclubmanagement.enums.StationStatus;
import com.nazarukiv.aigamingclubmanagement.enums.StationType;
import com.nazarukiv.aigamingclubmanagement.repository.GamingStationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Informational searches only; BookingService performs the final check under database locks. */
@Service
@Transactional(readOnly = true)
public class AvailabilityService {

    private final GamingStationRepository stations;

    public AvailabilityService(GamingStationRepository stations) {
        this.stations = stations;
    }

    public List<AvailabilityResponse> getAvailableStations(Instant startTime, Instant endTime, StationType stationType) {
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time are required");
        }
        if (!startTime.isAfter(Instant.now())) {
            throw new IllegalArgumentException("Start time must be in the future");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        // StationType restricts callers to PC or PS5; null is not a valid type.
        if (stationType == null) {
            throw new IllegalArgumentException("Station type is required");
        }

        return stations.findAvailableStations(stationType, StationStatus.AVAILABLE, BookingStatus.CONFIRMED,
                        startTime, endTime).stream()
                .map(station -> new AvailabilityResponse(station.getId(), station.getName(), station.getType(),
                        station.getStationNumber()))
                .toList();
    }

    /** For this MVP, adjacency means consecutive station numbers, not physical proximity. */
    public List<AvailabilityResponse> findAdjacentPCs(Instant startTime, Instant endTime, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Adjacent PC count must be positive");
        }
        List<AvailabilityResponse> available = getAvailableStations(startTime, endTime, StationType.PC);
        int groupStart = 0;
        for (int i = 0; i < available.size(); i++) {
            if (i > 0 && (long) available.get(i).stationNumber() != (long) available.get(i - 1).stationNumber() + 1) {
                groupStart = i;
            }
            if (i - groupStart + 1 == count) {
                return List.copyOf(available.subList(groupStart, i + 1));
            }
        }
        return List.of();
    }
}
