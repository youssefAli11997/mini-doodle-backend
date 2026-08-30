package com.example.doodle.meeting.api;

import com.example.doodle.meeting.application.MeetingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/users/{userId}")
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @PostMapping("/slots/{slotId}/meeting")
    @ResponseStatus(HttpStatus.CREATED)
    public MeetingResponse bookMeeting(
            @PathVariable("userId") UUID userId,
            @PathVariable("slotId") UUID slotId,
            @Valid @RequestBody BookMeetingRequest request
    ) {
        return meetingService.bookMeeting(userId, slotId, request);
    }

    @GetMapping("/meetings/{meetingId}")
    public MeetingResponse getMeeting(
            @PathVariable("userId") UUID userId,
            @PathVariable("meetingId") UUID meetingId
    ) {
        return meetingService.getMeeting(userId, meetingId);
    }
}
