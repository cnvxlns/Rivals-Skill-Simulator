package com.example.skillsim.dto;

import com.example.skillsim.enums.Handedness;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreRequest {
    @NotBlank
    private String cardType;

    @NotBlank
    private String position;

    @Valid
    @NotEmpty
    private List<ScoreSelection> selections;

    private Integer battingOrder;

    private Integer pitcherSlot;

    private Map<String, Double> userStats;
    /** 선수 본인의 투구 방향. 미지정 시 우완으로 간주한다. */
    private Handedness throwHand;
    /** 선수 본인의 타격 방향. 미지정 시 우타로 간주한다. */
    private Handedness batHand;
}
