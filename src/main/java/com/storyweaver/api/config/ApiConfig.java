package com.storyweaver.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "api")
public record ApiConfig(HuggingFace huggingFace, Supabase supabase) {
    public record HuggingFace(String url, String token) {}
    public record Supabase(String url, String key, String jwtSecret) {}
}