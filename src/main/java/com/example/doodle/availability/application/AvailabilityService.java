package com.example.doodle.availability.application;

import com.example.doodle.availability.domain.AvailabilityPeriod;
import com.example.doodle.common.exception.BadRequestException;
import com.example.doodle.common.exception.ResourceNotFoundException;
import com.example.doodle.slot.domain.SlotStatus;
import com.example.doodle.slot.persistence.TimeSlotEntity;
import com.example.doodle.slot.persistence.TimeSlotRepository;
import com.example.doodle.user.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class AvailabilityService {

    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;

    public AvailabilityService(TimeSlotRepository timeSlotRepository, UserRepository userRepository) {
        this.timeSlotRepository = timeSlotRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AvailabilityPeriod> getAvailability(UUID userId, Instant from, Instant to, SlotStatus status) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("USER_NOT_FOUND", "User with ID " + userId + " was not found.");
        }

        if (from == null || to == null) {
            throw new BadRequestException("MISSING_PARAMETER", "Both 'from' and 'to' query parameters are required.");
        }

        if (!from.isBefore(to)) {
            throw new BadRequestException("INVALID_TIME_RANGE", "'from' timestamp must be strictly before 'to' timestamp.");
        }

        List<TimeSlotEntity> slots = timeSlotRepository.findByUserIdAndTimeRange(userId, from, to, status);

        List<AvailabilityPeriod> clippedPeriods = slots.stream()
                .map(slot -> {
                    Instant start = slot.getStartTime().isBefore(from) ? from : slot.getStartTime();
                    Instant end = slot.getEndTime().isAfter(to) ? to : slot.getEndTime();
                    return new AvailabilityPeriod(start, end, slot.getStatus());
                })
                .filter(period -> period.startTime().isBefore(period.endTime()))
                .sorted(Comparator.comparing(AvailabilityPeriod::startTime))
                .toList();

        return aggregatePeriods(clippedPeriods);
    }

    private List<AvailabilityPeriod> aggregatePeriods(List<AvailabilityPeriod> periods) {
        List<AvailabilityPeriod> aggregated = new ArrayList<>();
        for (AvailabilityPeriod current : periods) {
            if (aggregated.isEmpty()) {
                aggregated.add(current);
            } else {
                AvailabilityPeriod last = aggregated.get(aggregated.size() - 1);
                if (last.status() == current.status() && last.endTime().equals(current.startTime())) {
                    aggregated.set(aggregated.size() - 1, new AvailabilityPeriod(last.startTime(), current.endTime(), last.status()));
                } else {
                    aggregated.add(current);
                }
            }
        }
        return aggregated;
    }
}
