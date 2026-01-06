// 스킬 변경 관련 API 엔드포인트를 제공하는 REST 컨트롤러
package com.example.skillsim.controller;

import com.example.skillsim.dto.RollRequest;
import com.example.skillsim.dto.RollResponse;
import com.example.skillsim.service.SkillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skills")
@CrossOrigin(origins = "http://localhost:3000")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @PostMapping("/roll")
    public RollResponse rollSkills(@Valid @RequestBody RollRequest request) {
        return skillService.rollSkills(request);
    }
}
