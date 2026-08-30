package com.example.doodle.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SchedulingMetrics {

    private final Counter meetingBookingSuccess;
    private final Counter meetingBookingConflict;
    private final Counter availabilityQuery;

    public SchedulingMetrics(MeterRegistry meterRegistry) {
        this.meetingBookingSuccess = Counter.builder("meeting_booking_success")
                .description("Successful meeting booking requests")
                .register(meterRegistry);
        this.meetingBookingConflict = Counter.builder("meeting_booking_conflict")
                .description("Meeting booking requests rejected because of booking conflicts")
                .register(meterRegistry);
        this.availabilityQuery = Counter.builder("availability_query")
                .description("Successful availability query requests")
                .register(meterRegistry);
    }

    public void recordMeetingBookingSuccess() {
        meetingBookingSuccess.increment();
    }

    public void recordMeetingBookingConflict() {
        meetingBookingConflict.increment();
    }

    public void recordAvailabilityQuery() {
        availabilityQuery.increment();
    }
}
