package com.example.doodle.slot.api;

import com.example.doodle.slot.application.SlotDto;
import com.example.doodle.slot.application.SlotMapper;
import com.example.doodle.slot.application.TimeSlotService;
import com.example.doodle.slot.domain.SlotStatus;
import com.example.doodle.slot.domain.TimeSlot;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users/{userId}/slots")
public class SlotController {

    private final TimeSlotService timeSlotService;

    public SlotController(TimeSlotService timeSlotService) {
        this.timeSlotService = timeSlotService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SlotDto createSlot(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody CreateSlotRequest request
    ) {
        TimeSlot slot = timeSlotService.createSlot(userId, request);
        return SlotMapper.toDto(slot);
    }

    @GetMapping
    public List<SlotDto> listSlots(
            @PathVariable("userId") UUID userId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "status", required = false) SlotStatus status
    ) {
        List<TimeSlot> slots = timeSlotService.listSlots(userId, from, to, status);
        return slots.stream().map(SlotMapper::toDto).toList();
    }

    @GetMapping("/{slotId}")
    public SlotDto getSlot(
            @PathVariable("userId") UUID userId,
            @PathVariable("slotId") UUID slotId
    ) {
        TimeSlot slot = timeSlotService.getSlot(userId, slotId);
        return SlotMapper.toDto(slot);
    }

    @PatchMapping("/{slotId}")
    public SlotDto updateSlot(
            @PathVariable("userId") UUID userId,
            @PathVariable("slotId") UUID slotId,
            @Valid @RequestBody UpdateSlotRequest request
    ) {
        TimeSlot slot = timeSlotService.updateSlot(userId, slotId, request);
        return SlotMapper.toDto(slot);
    }

    @DeleteMapping("/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSlot(
            @PathVariable("userId") UUID userId,
            @PathVariable("slotId") UUID slotId
    ) {
        timeSlotService.deleteSlot(userId, slotId);
    }
}
