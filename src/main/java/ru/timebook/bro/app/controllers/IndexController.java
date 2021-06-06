package ru.timebook.bro.app.controllers;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.timebook.bro.app.health.LivenessService;

@RestController
public class IndexController {
    private final LivenessService livenessService;

    public IndexController(LivenessService livenessService) {
        this.livenessService = livenessService;
    }

    @GetMapping(path = "/index")
    public String index(Model model) {
        return "index";
    }
}
