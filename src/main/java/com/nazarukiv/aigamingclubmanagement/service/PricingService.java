package com.nazarukiv.aigamingclubmanagement.service;

import com.nazarukiv.aigamingclubmanagement.dto.PricingResponse;
import com.nazarukiv.aigamingclubmanagement.entity.GamingStation;
import com.nazarukiv.aigamingclubmanagement.exception.StationNotFoundException;
import com.nazarukiv.aigamingclubmanagement.repository.GamingStationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class PricingService {

    private static final BigDecimal PC_HOURLY_PRICE = new BigDecimal("5.00");
    private static final BigDecimal PS5_HOURLY_PRICE = new BigDecimal("7.00");
    private static final BigDecimal SECONDS_PER_HOUR = new BigDecimal("3600");

    private final GamingStationRepository stations;

    public PricingService(GamingStationRepository stations) {
        this.stations = stations;
    }

    public PricingResponse calculatePrice(List<Long> stationIds, Instant startTime, Instant endTime) {
        if (stationIds == null || stationIds.isEmpty()) {
            throw new IllegalArgumentException("At least one station must be selected");
        }
        if (stationIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Station IDs must be positive numbers");
        }
        if (new HashSet<>(stationIds).size() != stationIds.size()) {
            throw new IllegalArgumentException("Duplicate station IDs are not allowed");
        }
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start time and end time are required");
        }
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }

        List<GamingStation> selectedStations = stations.findAllById(stationIds);
        if (selectedStations.size() != stationIds.size()) {
            throw new StationNotFoundException("One or more selected stations do not exist");
        }
        BigDecimal hourlyTotal = BigDecimal.ZERO;
        for (GamingStation station : selectedStations) {
            hourlyTotal = hourlyTotal.add(switch (station.getType()) {
                case PC -> PC_HOURLY_PRICE;
                case PS5 -> PS5_HOURLY_PRICE;
            });
        }
        Duration duration = Duration.between(startTime, endTime);
        BigDecimal seconds = BigDecimal.valueOf(duration.getSeconds())
                .add(BigDecimal.valueOf(duration.getNano(), 9));
        // Multiply before dividing so only the final total is rounded, including fractional seconds.
        BigDecimal totalPrice = hourlyTotal.multiply(seconds).divide(SECONDS_PER_HOUR, 2, RoundingMode.HALF_UP);
        return new PricingResponse(stationIds, duration, "GBP", totalPrice);
    }
}
