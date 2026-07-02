package com.example.skillsim.controller;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    @Test
    void checkHealthReturnsStatusOk() {
        HealthController controller = new HealthController();
        Map<String, String> response = controller.checkHealth();
        assertThat(response).containsEntry("status", "ok");
    }
}
