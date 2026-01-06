package com.example.skillsim.dto;

import com.example.skillsim.enums.CardType;
import com.example.skillsim.enums.Grade;
import com.example.skillsim.enums.TicketType;
import jakarta.validation.constraints.NotNull;
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

    private boolean useLevelProtection;

    // Indices of slots to keep unchanged (0-based). Example: [0] locks first slot.
    private List<Integer> lockedSlots;

    // Optional: current slot state to preserve locked slots and apply protection comparisons.
    private List<Long> currentSkillIds;

    // Expected size 3 to match the three slots, but validated defensively in service
    private List<Grade> currentGrades;
}
