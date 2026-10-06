package com.applyflow.controller;

import com.applyflow.dto.DashboardDtos.ActivityItem;
import com.applyflow.dto.DashboardDtos.AttentionItem;
import com.applyflow.dto.DashboardDtos.CalendarEvent;
import com.applyflow.dto.DashboardDtos.DashboardSummary;
import com.applyflow.service.CalendarService;
import com.applyflow.service.DashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class DashboardController {

    private final DashboardService dashboardService;
    private final CalendarService calendarService;

    public DashboardController(DashboardService dashboardService, CalendarService calendarService) {
        this.dashboardService = dashboardService;
        this.calendarService = calendarService;
    }

    @GetMapping("/api/dashboard/summary")
    public DashboardSummary summary() {
        return dashboardService.summary();
    }

    @GetMapping("/api/dashboard/activity")
    public List<ActivityItem> activity(@RequestParam(defaultValue = "20") int limit) {
        return dashboardService.activity(limit);
    }

    @GetMapping("/api/dashboard/attention")
    public List<AttentionItem> attention() {
        return dashboardService.attention();
    }

    @GetMapping("/api/calendar/events")
    public List<CalendarEvent> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return calendarService.events(from, to);
    }
}
