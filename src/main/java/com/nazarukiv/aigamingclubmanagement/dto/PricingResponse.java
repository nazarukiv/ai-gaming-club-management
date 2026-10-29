package com.nazarukiv.aigamingclubmanagement.dto;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

public record PricingResponse(List<Long> stationIds, Duration duration, String currency, BigDecimal totalPrice) {

    public PricingResponse {
        stationIds = List.copyOf(stationIds);
    }
}
