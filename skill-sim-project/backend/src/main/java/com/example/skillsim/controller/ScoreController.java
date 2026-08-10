package com.example.skillsim.controller;

import com.example.skillsim.dto.ScoreRequest;
import com.example.skillsim.dto.ScoreResponse;
import com.example.skillsim.dto.ScoreSkillOption;
import com.example.skillsim.dto.MethodologyResponse;
import com.example.skillsim.service.ScoreService;
import com.example.skillsim.service.MethodologyService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/score")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;
    private final MethodologyService methodologyService;

    @GetMapping("/skills")
    public List<ScoreSkillOption> listSkills(@RequestParam String cardType, @RequestParam String position) {
        return scoreService.listSkills(cardType, position);
    }

    @PostMapping
    public ScoreResponse calculate(@Valid @RequestBody ScoreRequest request) {
        return scoreService.calculate(request);
    }

    @GetMapping("/methodology")
    public MethodologyResponse getMethodology() {
        return methodologyService.getMethodology();
    }
}
