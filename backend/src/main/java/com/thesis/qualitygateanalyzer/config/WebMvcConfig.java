package com.thesis.qualitygateanalyzer.config;

import com.thesis.qualitygateanalyzer.controller.v1.ApiV1Controller;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Centralizes the {@code /api/v1} path prefix for every controller that
 * implements {@link ApiV1Controller}, so those controllers don't each
 * repeat it in a class-level {@code @RequestMapping}.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api/v1", HandlerTypePredicate.forAssignableType(ApiV1Controller.class));
    }
}
