package com.example.skillsim.dto;

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
}
