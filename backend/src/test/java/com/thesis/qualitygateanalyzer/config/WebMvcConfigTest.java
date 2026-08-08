package com.thesis.qualitygateanalyzer.config;

import com.thesis.qualitygateanalyzer.controller.v1.ApiV1Controller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies, against a real (but minimal, DB-free) Spring MVC context, that
 * {@link WebMvcConfig} prepends {@code /api/v1} to
 * {@link ApiV1Controller} implementations at request-routing time — the part
 * unit tests that instantiate controllers directly (bypassing Spring MVC
 * routing) can't cover. Uses throwaway controllers instead of the real ones
 * so it doesn't need a datasource.
 */
@SpringJUnitWebConfig(WebMvcConfigTest.TestConfig.class)
class WebMvcConfigTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void bareMethodPath_isPrefixedWithApiV1() throws Exception {
        mockMvc.perform(get("/api/v1/ping")).andExpect(status().isOk()).andExpect(content().string("pong"));
        mockMvc.perform(get("/ping")).andExpect(status().isNotFound());
    }

    @Test
    void classLevelResourcePath_isPrefixedWithApiV1() throws Exception {
        mockMvc.perform(get("/api/v1/widgets")).andExpect(status().isOk()).andExpect(content().string("widgets"));
        mockMvc.perform(get("/widgets")).andExpect(status().isNotFound());
    }

    @RestController
    static class BareController implements ApiV1Controller {
        @GetMapping("/ping")
        public String ping() {
            return "pong";
        }
    }

    @RestController
    @RequestMapping("/widgets")
    static class ResourceController implements ApiV1Controller {
        @GetMapping
        public String list() {
            return "widgets";
        }
    }

    @EnableWebMvc
    @Configuration
    static class TestConfig extends WebMvcConfig {
        @Bean
        BareController bareController() {
            return new BareController();
        }

        @Bean
        ResourceController resourceController() {
            return new ResourceController();
        }
    }
}
