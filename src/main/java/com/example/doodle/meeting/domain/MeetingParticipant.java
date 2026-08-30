package com.example.doodle.meeting.domain;

import java.util.UUID;

public record MeetingParticipant(
        UUID meetingId,
        UUID userId
) {
}
