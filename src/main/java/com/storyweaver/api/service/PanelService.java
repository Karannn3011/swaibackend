package com.storyweaver.api.service;

import com.storyweaver.api.config.ApiConfig;
import com.storyweaver.api.panel.Panel;
import com.storyweaver.api.panel.PanelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;

@Service
public class PanelService {

    // --- TEMPORARY DEBUGGING CHANGE: Added a logger ---
    private static final Logger logger = LoggerFactory.getLogger(PanelService.class);

    private final PanelRepository panelRepository;
    private final WebClient webClient;
    private final ApiConfig apiConfig;

    public PanelService(PanelRepository panelRepository, ApiConfig apiConfig) {
        this.panelRepository = panelRepository;
        this.apiConfig = apiConfig;
        this.webClient = WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public Panel createPanel(String prompt, UUID roomId) {
        // --- TEMPORARY DEBUGGING CHANGE: We only call the API and log the result ---
        logger.info("Attempting to call lightweight debugging model...");
        callHuggingFaceTextApi(prompt);
        logger.info("API call successful. The rest of the logic is skipped for this test.");

        // The rest of the logic is disabled for this test.
        // byte[] imageBytes = ...
        // uploadToSupabaseStorage(...)
        // panelRepository.save(...)

        return null; // We return null because we are not creating a panel in this test.
    }

    // --- TEMPORARY DEBUGGING CHANGE: This method now expects a String (JSON) response, not bytes ---
    private void callHuggingFaceTextApi(String prompt) {
        logger.info("Calling HF with URL={} and token length={}",
                apiConfig.huggingFace().url(),
                apiConfig.huggingFace().token().length());

        String response = webClient.post()
                .uri(apiConfig.huggingFace().url()) // should be full model URL
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiConfig.huggingFace().token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"inputs\": \"" + prompt + "\"}")
                .retrieve()
                .bodyToMono(String.class)
                .block();


        logger.info("Successfully received response from Hugging Face: {}", response);

    }
}