package com.nazarukiv.aigamingclubmanagement.dto;

import com.nazarukiv.aigamingclubmanagement.enums.StationType;

public record AvailabilityResponse(Long stationId, String stationName, StationType stationType,
                                   Integer stationNumber) {
}
