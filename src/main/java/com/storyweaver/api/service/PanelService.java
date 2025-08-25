package com.storyweaver.api.service;

import com.storyweaver.api.config.ApiConfig;
import com.storyweaver.api.panel.Panel;
import com.storyweaver.api.panel.PanelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Service
public class PanelService {

    private static final Logger logger = LoggerFactory.getLogger(PanelService.class);

    private final PanelRepository panelRepository;
    private final ApiConfig apiConfig;
    private final RestTemplate restTemplate;

    public PanelService(PanelRepository panelRepository, ApiConfig apiConfig, RestTemplateBuilder restTemplateBuilder) {
        this.panelRepository = panelRepository;
        this.apiConfig = apiConfig;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(60))
                .readTimeout(Duration.ofSeconds(120))
                .build();
    }

    public Panel createPanel(String prompt, UUID roomId) {
        logger.info("Generating image for prompt: '{}'", prompt);

        // Step 1: Get the image bytes from the Pollinations API
        byte[] imageBytes = callPollinationsImageApi(prompt);
        logger.info("Successfully received image bytes from Pollinations. Size: {} bytes", imageBytes.length);

        // Step 2: Upload the image bytes directly to Supabase
        String imageUrl = uploadToSupabaseStorage(imageBytes, roomId);
        logger.info("Image uploaded to Supabase Storage at URL: {}", imageUrl);

        // Step 3: Save the panel metadata to the database
        Panel newPanel = new Panel();
        newPanel.setPrompt(prompt);
        newPanel.setRoomId(roomId);
        newPanel.setImageUrl(imageUrl);
        Panel savedPanel = panelRepository.save(newPanel);

        logger.info("New panel with ID {} saved to the database.", savedPanel.getId());
        return savedPanel;
    }

    private byte[] callPollinationsImageApi(String prompt) {
        // The base URL for the Pollinations image generation API
        String baseUrl = "https://image.pollinations.ai/prompt/";

        try {
            // URL-encode the prompt to handle spaces and special characters safely
            String encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8.toString());

            // Construct the full URL with parameters for image size and to remove the logo
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + encodedPrompt)
                    .queryParam("width", "1024")
                    .queryParam("height", "1024")
                    .queryParam("nologo", "true")
                    .toUriString();

            logger.info("Calling Pollinations API: {}", url);

            // Make a simple GET request and get the image bytes directly
            byte[] imageBytes = restTemplate.getForObject(url, byte[].class);

            if (imageBytes == null || imageBytes.length == 0) {
                throw new RuntimeException("Received empty or null image response from Pollinations API");
            }
            return imageBytes;

        } catch (Exception e) {
            logger.error("Error calling Pollinations API", e);
            throw new RuntimeException("Error generating image via Pollinations API", e);
        }
    }

    private String uploadToSupabaseStorage(byte[] imageBytes, UUID roomId) {
        String bucketName = "panels";
        String fileName = roomId.toString() + "/" + System.currentTimeMillis() + ".jpg";
        String uploadPath = "/storage/v1/object/" + bucketName + "/" + fileName;
        String fullUrl = apiConfig.supabase().url() + uploadPath;

        try {
            logger.info("Uploading image to Supabase: {}", fullUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            headers.setBearerAuth(apiConfig.supabase().key());
            headers.set("apikey", apiConfig.supabase().key());
            headers.set("x-upsert", "true");
            headers.setContentLength(imageBytes.length);

            HttpEntity<byte[]> requestEntity = new HttpEntity<>(imageBytes, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    fullUrl,
                    requestEntity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Upload failed with status: " + response.getStatusCode() + " and body: " + response.getBody());
            }

            logger.info("Successfully uploaded to Supabase. Status: {}", response.getStatusCode());
            return fullUrl;

        } catch (Exception e) {
            logger.error("Error uploading image to Supabase Storage", e);
            throw new RuntimeException("Error uploading image to storage", e);
        }
    }
}