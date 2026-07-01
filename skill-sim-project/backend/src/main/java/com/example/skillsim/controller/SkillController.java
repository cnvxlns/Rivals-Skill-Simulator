// 스킬 변경 관련 API 엔드포인트를 제공하는 REST 컨트롤러
package com.example.skillsim.controller;

import com.example.skillsim.dto.RollRequest;
import com.example.skillsim.dto.RollResponse;
import com.example.skillsim.service.SkillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skills")
@CrossOrigin(origins = {
    "http://localhost:3000",
    "https://rivals-skill-random-generator-api.onrender.com", // (선택: 자기 자신)
    "https://your-frontend.vercel.app" // 👈 나중에 Vercel 배포 주소가 나오면 여기 추가해야 함 (지금은 localhost만 있어도 됨)
})
@RequiredArgsConstructor
@Slf4j
public class SkillController {

    private final SkillService skillService;

    @PostMapping("/roll")
    public RollResponse rollSkills(@Valid @RequestBody RollRequest request) {
        return skillService.rollSkills(request);
    }

    @GetMapping("/initial")
    public RollResponse initialSkills(
            @RequestParam String cardType,
            @RequestParam String position,
            @RequestParam(required = false) String subPosition
    ) {
        return skillService.initialSlots(cardType, position, subPosition);
    }

    // New endpoint: fetch Moment tier themes filtered by position (case-insensitive, includes SHARED)
    @GetMapping("/themes")
    public List<String> getThemes(@RequestParam String position, @RequestParam(required = false) String subPosition) {
        log.info("[GET /themes] raw position='{}', raw subPosition='{}'", position, subPosition);
        return skillService.getMomentThemeNames(position, subPosition);
    }
}
