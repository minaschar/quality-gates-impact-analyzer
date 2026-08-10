package com.thesis.qualitygateanalyzer.controller.v1.health;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HealthControllerTest {

    private final HealthController controller = new HealthController();

    @Test
    void health_returnsUp() {
        ResponseEntity<Map<String, String>> response = controller.health();
        Assertions.assertNotNull(response.getBody());
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        assertThat(response.getBody().get("database")).isEqualTo("PostgreSQL");
    }
}
