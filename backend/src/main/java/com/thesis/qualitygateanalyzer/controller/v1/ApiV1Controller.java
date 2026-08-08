package com.thesis.qualitygateanalyzer.controller.v1;

/**
 * Marker interface for REST controllers under the {@code /api/v1} namespace.
 * <p>
 * Implementing this interface is what makes {@code WebMvcConfig} prepend the
 * {@code /api/v1} prefix to all of a controller's request mappings, so
 * individual controllers only need to declare their own resource path (if
 * any) and never repeat the version prefix.
 */
public interface ApiV1Controller {
}
