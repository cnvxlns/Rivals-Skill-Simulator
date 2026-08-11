package com.example.skillsim.dto;

import com.example.skillsim.enums.Handedness;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 티어별 스킬 점수표 요청.
 *
 * <p>{@link ScoreRequest}에서 선택 슬롯과 카드 타입을 뺀 형태다. 카드 타입은 응답이 티어별로
 * 나뉘므로 받지 않는다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreTableRequest {

    @NotBlank
    private String position;

    private Integer battingOrder;

    private Integer pitcherSlot;

    private Handedness throwHand;

    private Handedness batHand;

    private Map<String, Double> userStats;
}
