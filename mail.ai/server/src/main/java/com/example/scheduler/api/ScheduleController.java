package com.example.scheduler.api;

import com.example.scheduler.service.ScheduleApplicationService;
import com.example.scheduler.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class ScheduleController {
    private final ScheduleApplicationService scheduleService;
    private final CurrentUser currentUser;

    public ScheduleController(ScheduleApplicationService scheduleService, CurrentUser currentUser) {
        this.scheduleService = scheduleService;
        this.currentUser = currentUser;
    }

    @PostMapping("/schedule/fixed")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse createFixed(HttpServletRequest servletRequest,
                                        @Valid @RequestBody CreateFixedScheduleRequest request) {
        return scheduleService.createFixed(currentUser.requireOwnerId(servletRequest), request);
    }

    @PostMapping("/schedule/recurrence")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse createRecurrence(HttpServletRequest servletRequest,
                                             @Valid @RequestBody CreateRecurrenceScheduleRequest body) {
        return scheduleService.createRecurrence(currentUser.requireOwnerId(servletRequest), body);
    }

    @GetMapping("/myschedules")
    public List<ScheduleResponse> listSchedules(HttpServletRequest request) {
        return scheduleService.listSchedules(currentUser.requireOwnerId(request));
    }
}
