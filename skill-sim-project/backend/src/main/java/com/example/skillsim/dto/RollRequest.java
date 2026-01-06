package com.example.skillsim.dto;

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

    private boolean useProtection;

    // Expected size 3 to match the three slots, but validated defensively in service
    private List<Grade> currentGrades;
}
