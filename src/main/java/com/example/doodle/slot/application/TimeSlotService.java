package com.example.doodle.slot.application;

import com.example.doodle.common.exception.BadRequestException;
import com.example.doodle.common.exception.ConflictException;
import com.example.doodle.common.exception.ResourceNotFoundException;
import com.example.doodle.slot.api.CreateSlotRequest;
import com.example.doodle.slot.api.UpdateSlotRequest;
import com.example.doodle.slot.domain.SlotStatus;
import com.example.doodle.slot.domain.TimeSlot;
import com.example.doodle.slot.persistence.TimeSlotEntity;
import com.example.doodle.slot.persistence.TimeSlotRepository;
import com.example.doodle.user.persistence.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;

    public TimeSlotService(TimeSlotRepository timeSlotRepository, UserRepository userRepository) {
        this.timeSlotRepository = timeSlotRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TimeSlot createSlot(UUID userId, CreateSlotRequest request) {
        verifyUserExists(userId);

        if (request.startTime() == null || request.endTime() == null || !request.startTime().isBefore(request.endTime())) {
            throw new BadRequestException("INVALID_TIME_RANGE", "startTime must be strictly before endTime.");
        }

        TimeSlot slot = TimeSlot.createFree(userId, request.startTime(), request.endTime());
        TimeSlotEntity entity = SlotMapper.toEntity(slot);
        try {
            TimeSlotEntity saved = timeSlotRepository.saveAndFlush(entity);
            return SlotMapper.toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("SLOT_OVERLAP", "The requested time slot overlaps with an existing slot.");
        }
    }

    @Transactional(readOnly = true)
    public List<TimeSlot> listSlots(UUID userId, Instant from, Instant to, SlotStatus status) {
        verifyUserExists(userId);

        if (from != null && to != null) {
            if (!from.isBefore(to)) {
                throw new BadRequestException("INVALID_TIME_RANGE", "from must be strictly before to.");
            }
            List<TimeSlotEntity> entities = timeSlotRepository.findByUserIdAndTimeRange(userId, from, to, status);
            return entities.stream()
                    .map(SlotMapper::toDomain)
                    .map(slot -> clipSlot(slot, from, to))
                    .toList();
        }

        // If no bounds provided, query all for the user
        Instant minInstant = Instant.EPOCH;
        Instant maxInstant = Instant.parse("9999-12-31T23:59:59Z");
        List<TimeSlotEntity> entities = timeSlotRepository.findByUserIdAndTimeRange(userId, minInstant, maxInstant, status);
        return entities.stream().map(SlotMapper::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public TimeSlot getSlot(UUID userId, UUID slotId) {
        verifyUserExists(userId);
        return timeSlotRepository.findByIdAndUserId(slotId, userId)
                .map(SlotMapper::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("SLOT_NOT_FOUND", "Time slot " + slotId + " was not found for user " + userId));
    }

    @Transactional
    public TimeSlot updateSlot(UUID userId, UUID slotId, UpdateSlotRequest request) {
        verifyUserExists(userId);

        TimeSlotEntity entity = timeSlotRepository.findByIdAndUserId(slotId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("SLOT_NOT_FOUND", "Time slot " + slotId + " was not found for user " + userId));

        if (entity.getStatus() != SlotStatus.FREE) {
            throw new ConflictException("SLOT_NOT_FREE", "Cannot modify a busy time slot.");
        }

        if (request.startTime() == null || request.endTime() == null || !request.startTime().isBefore(request.endTime())) {
            throw new BadRequestException("INVALID_TIME_RANGE", "startTime must be strictly before endTime.");
        }

        entity.setStartTime(request.startTime());
        entity.setEndTime(request.endTime());
        entity.setUpdatedAt(Instant.now());

        try {
            TimeSlotEntity saved = timeSlotRepository.saveAndFlush(entity);
            return SlotMapper.toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("SLOT_OVERLAP", "The updated time slot overlaps with an existing slot.");
        }
    }

    @Transactional
    public void deleteSlot(UUID userId, UUID slotId) {
        verifyUserExists(userId);

        TimeSlotEntity entity = timeSlotRepository.findByIdAndUserId(slotId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("SLOT_NOT_FOUND", "Time slot " + slotId + " was not found for user " + userId));

        if (entity.getStatus() != SlotStatus.FREE) {
            throw new ConflictException("SLOT_NOT_FREE", "Cannot delete a busy time slot.");
        }

        timeSlotRepository.delete(entity);
    }

    private void verifyUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("USER_NOT_FOUND", "User with ID " + userId + " was not found.");
        }
    }

    private TimeSlot clipSlot(TimeSlot slot, Instant from, Instant to) {
        Instant clippedStart = slot.startTime().isBefore(from) ? from : slot.startTime();
        Instant clippedEnd = slot.endTime().isAfter(to) ? to : slot.endTime();
        return new TimeSlot(
                slot.id(),
                slot.userId(),
                clippedStart,
                clippedEnd,
                slot.status(),
                slot.createdAt(),
                slot.updatedAt()
        );
    }
}
