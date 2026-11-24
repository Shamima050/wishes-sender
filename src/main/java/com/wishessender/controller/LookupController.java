package com.wishessender.controller;

import com.wishessender.util.Rules;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** GET /api/lookups - every allowed value for the dropdowns (read-only, no login needed). */
@RestController
public class LookupController {

    private final JdbcTemplate jdbc;

    public LookupController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/lookups")
    public Map<String, Object> lookups() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("eventTypes", jdbc.queryForList("SELECT code, label FROM event_types ORDER BY sort_order"));
        data.put("relations", Rules.RELATIONS);
        data.put("genders", Rules.GENDERS);
        data.put("religions", Rules.RELIGIONS);
        data.put("festivalReligions", Rules.FESTIVAL_RELIGIONS);
        data.put("tones", Rules.TONES);
        data.put("languages", Rules.LANGUAGES);
        data.put("wishSources", Rules.WISH_SOURCES);
        data.put("wishStatuses", Rules.WISH_STATUSES);
        data.put("giftCategories", Rules.GIFT_CATEGORIES);
        data.put("maxRemindDays", Rules.MAX_REMIND_DAYS);
        return data;
    }
}
