package com.example.skillsim.controller;

import java.util.Collections;
import java.util.Map;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@CrossOrigin(originPatterns = {
    "http://localhost:3000",
    "https://rivals-skill-random-generator-api.onrender.com",
    "https://*.vercel.app"
})
public class HealthController {

    @GetMapping
    public Map<String, String> checkHealth() {
        return Collections.singletonMap("status", "ok");
    }
}
