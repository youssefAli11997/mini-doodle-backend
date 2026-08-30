package com.example.doodle.meeting.application;

import com.example.doodle.common.exception.BadRequestException;
import com.example.doodle.common.exception.ConflictException;
import com.example.doodle.common.exception.ResourceNotFoundException;
import com.example.doodle.meeting.api.BookMeetingRequest;
import com.example.doodle.meeting.api.MeetingResponse;
import com.example.doodle.meeting.persistence.MeetingEntity;
import com.example.doodle.meeting.persistence.MeetingParticipantEntity;
import com.example.doodle.meeting.persistence.MeetingParticipantRepository;
import com.example.doodle.meeting.persistence.MeetingRepository;
import com.example.doodle.slot.application.SlotMapper;
import com.example.doodle.slot.domain.SlotStatus;
import com.example.doodle.slot.persistence.TimeSlotEntity;
import com.example.doodle.slot.persistence.TimeSlotRepository;
import com.example.doodle.user.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;

    public MeetingService(
            MeetingRepository meetingRepository,
            MeetingParticipantRepository meetingParticipantRepository,
            TimeSlotRepository timeSlotRepository,
            UserRepository userRepository
    ) {
        this.meetingRepository = meetingRepository;
        this.meetingParticipantRepository = meetingParticipantRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public MeetingResponse bookMeeting(UUID ownerId, UUID slotId, BookMeetingRequest request) {
        TimeSlotEntity slot = timeSlotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("SLOT_NOT_FOUND", "Time slot " + slotId + " was not found."));

        if (!slot.getUserId().equals(ownerId)) {
            throw new ResourceNotFoundException("SLOT_NOT_FOUND", "Time slot " + slotId + " was not found for user " + ownerId + ".");
        }

        if (slot.getStatus() != SlotStatus.FREE) {
            throw new ConflictException("SLOT_ALREADY_BOOKED", "The requested time slot is already booked.");
        }

        validateParticipants(ownerId, request.participantIds());

        Instant now = Instant.now();
        UUID meetingId = UUID.randomUUID();
        MeetingEntity meeting = new MeetingEntity(
                meetingId,
                slotId,
                request.title(),
                request.description(),
                now,
                now
        );
        meetingRepository.saveAndFlush(meeting);

        List<MeetingParticipantEntity> participants = request.participantIds().stream()
                .map(participantId -> new MeetingParticipantEntity(meetingId, participantId))
                .toList();
        meetingParticipantRepository.saveAll(participants);

        slot.setStatus(SlotStatus.BUSY);
        slot.setUpdatedAt(now);
        timeSlotRepository.save(slot);

        return new MeetingResponse(
                meeting.getId(),
                meeting.getSlotId(),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getCreatedAt(),
                meeting.getUpdatedAt(),
                request.participantIds(),
                SlotMapper.toDto(SlotMapper.toDomain(slot))
        );
    }

    @Transactional(readOnly = true)
    public MeetingResponse getMeeting(UUID userId, UUID meetingId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("USER_NOT_FOUND", "User with ID " + userId + " was not found.");
        }

        MeetingEntity meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("MEETING_NOT_FOUND", "Meeting with ID " + meetingId + " was not found."));

        TimeSlotEntity slot = timeSlotRepository.findById(meeting.getSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("SLOT_NOT_FOUND", "Associated time slot was not found."));

        List<UUID> participantIds = meetingParticipantRepository.findByIdMeetingId(meetingId).stream()
                .map(MeetingParticipantEntity::getUserId)
                .toList();

        boolean belongsToCalendar = slot.getUserId().equals(userId) || participantIds.contains(userId);
        if (!belongsToCalendar) {
            throw new ResourceNotFoundException("MEETING_NOT_FOUND", "Meeting with ID " + meetingId + " was not found on user " + userId + "'s calendar.");
        }

        return new MeetingResponse(
                meeting.getId(),
                meeting.getSlotId(),
                meeting.getTitle(),
                meeting.getDescription(),
                meeting.getCreatedAt(),
                meeting.getUpdatedAt(),
                participantIds,
                SlotMapper.toDto(SlotMapper.toDomain(slot))
        );
    }

    private void validateParticipants(UUID ownerId, List<UUID> participantIds) {
        if (participantIds == null || participantIds.size() < 2) {
            throw new BadRequestException("INVALID_PARTICIPANTS", "A meeting must contain at least two participants.");
        }

        if (!participantIds.contains(ownerId)) {
            throw new BadRequestException("INVALID_PARTICIPANTS", "Slot owner must be included in participant list.");
        }

        Set<UUID> uniqueIds = new HashSet<>(participantIds);
        if (uniqueIds.size() != participantIds.size()) {
            throw new BadRequestException("INVALID_PARTICIPANTS", "Duplicate participant IDs are not allowed.");
        }

        long existingCount = userRepository.findAllById(participantIds).size();
        if (existingCount != participantIds.size()) {
            throw new BadRequestException("INVALID_PARTICIPANTS", "Every participant must reference an existing user.");
        }
    }
}
