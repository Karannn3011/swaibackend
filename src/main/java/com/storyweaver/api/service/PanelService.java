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
        logger.info("Generating image for prompt: '{}'", prompt);

        // 1. Call the Hugging Face API to get the image bytes
        byte[] imageBytes = callHuggingFaceImageApi(prompt);
        logger.info("Successfully received image bytes from Hugging Face.");

        // 2. Upload the image to Supabase Storage
        //    (You will need to implement the logic for this part)
        //    For now, we'll use a placeholder URL.
        String imageUrl = uploadToSupabaseStorage(imageBytes, roomId);
        logger.info("Image uploaded to Supabase Storage at URL: {}", imageUrl);

        // 3. Save the new panel to the database
        Panel newPanel = new Panel();
        newPanel.setPrompt(prompt);
        newPanel.setRoomId(roomId);
        newPanel.setImageUrl(imageUrl);
        Panel savedPanel = panelRepository.save(newPanel);

        logger.info("New panel with ID {} saved to the database.", savedPanel.getId());
        return savedPanel;
    }

    private byte[] callHuggingFaceImageApi(String prompt) {
        logger.info("Calling HF with URL={} and token length={}",
                apiConfig.huggingFace().url(),
                apiConfig.huggingFace().token().length());

        return webClient.post()
                .uri(apiConfig.huggingFace().url())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiConfig.huggingFace().token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"inputs\": \"" + prompt + "\"}")
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }

    /**
     * Placeholder for the Supabase Storage upload logic.
     * You will need to implement this method to upload the image bytes
     * to your Supabase project and return the public URL.
     */
    private String uploadToSupabaseStorage(byte[] imageBytes, UUID roomId) {
        // TODO: Implement the logic to upload the image to Supabase Storage.
        // For now, returning a placeholder.
        return "https://example.com/placeholder.jpg";
    }
}