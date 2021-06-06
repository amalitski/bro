package ru.timebook.bro.app.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.timebook.bro.app.health.LivenessService;

@RestController
@RequestMapping(path = "/health")
public class HealthController {
    private final LivenessService livenessService;

    public HealthController(LivenessService livenessService) {
        this.livenessService = livenessService;
    }

    @GetMapping(path = "/liveness", produces = "application/json")
    public ResponseEntity<LivenessService.Response> health() {
        return ResponseEntity.ok(livenessService.health());
    }
}
