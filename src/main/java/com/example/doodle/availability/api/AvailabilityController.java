package com.example.doodle.availability.api;

import com.example.doodle.availability.application.AvailabilityService;
import com.example.doodle.availability.domain.AvailabilityPeriod;
import com.example.doodle.slot.domain.SlotStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users/{userId}/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public List<AvailabilityPeriodResponse> getAvailability(
            @PathVariable("userId") UUID userId,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "status", required = false) SlotStatus status
    ) {
        List<AvailabilityPeriod> periods = availabilityService.getAvailability(userId, from, to, status);
        return periods.stream()
                .map(p -> new AvailabilityPeriodResponse(p.startTime(), p.endTime(), p.status()))
                .toList();
    }
}
