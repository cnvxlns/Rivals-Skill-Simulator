// 스킬 뽑기 요청 시 클라이언트가 전달하는 파라미터를 담는 DTO
package com.example.skillsim.dto;

import com.example.skillsim.enums.CardType;
import com.example.skillsim.enums.Level;
import com.example.skillsim.enums.TicketType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollRequest {

    @NotNull
    private TicketType ticketType;

    @NotNull
    private CardType cardType;

    // Whether to apply level protection per slot (size should match slot count)
    private List<Boolean> useLevelProtectionSlots;

    // Indices of slots to keep unchanged (0-based). Example: [0] locks first slot.
    private List<Integer> lockedSlots;

    // Optional: current slot state to preserve locked slots and apply protection comparisons.
    private List<Long> currentSkillIds;

    // Expected size 3 to match the three slots, but validated defensively in service
    private List<Level> currentLevels;

    // Position filter (e.g., PITCHER, BATTER). When provided, rolls are limited to that position.
    @NotBlank(message = "Position selection is required.")
    private String position;
}
