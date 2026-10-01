package com.example.scheduler.api;

import com.example.scheduler.service.ScheduleApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ScheduleController {
    private final ScheduleApplicationService scheduleService;

    public ScheduleController(ScheduleApplicationService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping("/schedule/fixed")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse createFixed(@RequestHeader("X-User-Id") String ownerId,
                                        @Valid @RequestBody CreateFixedScheduleRequest request) {
        return scheduleService.createFixed(ownerId, request);
    }

    @PostMapping("/schedule/recurrence")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse createRecurrence(@RequestHeader("X-User-Id") String ownerId,
                                             @Valid @RequestBody CreateRecurrenceScheduleRequest request) {
        return scheduleService.createRecurrence(ownerId, request);
    }

    @GetMapping("/myschedules")
    public List<ScheduleResponse> listSchedules(@RequestHeader("X-User-Id") String ownerId) {
        return scheduleService.listSchedules(ownerId);
    }
}
