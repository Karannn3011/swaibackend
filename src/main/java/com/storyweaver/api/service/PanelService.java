package com.storyweaver.api.service;

import com.storyweaver.api.config.ApiConfig;
import com.storyweaver.api.panel.Panel;
import com.storyweaver.api.panel.PanelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient; // Import TcpClient

import java.time.Duration;
import java.util.UUID;
import io.netty.channel.ChannelOption; // Import ChannelOption

@Service
public class PanelService {

    private static final Logger logger = LoggerFactory.getLogger(PanelService.class);

    private final PanelRepository panelRepository;
    private final ApiConfig apiConfig;
    private final WebClient huggingFaceWebClient;
    private final WebClient supabaseWebClient;

    public PanelService(PanelRepository panelRepository, ApiConfig apiConfig) {
        this.panelRepository = panelRepository;
        this.apiConfig = apiConfig;

        // --- Create a robust HttpClient configuration for both clients ---
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000) // 30-second connection timeout
                .responseTimeout(Duration.ofSeconds(90)); // 90-second response timeout

        // --- Apply the same configuration to both WebClients ---
        this.huggingFaceWebClient = WebClient.builder()
                .baseUrl(apiConfig.huggingFace().url())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiConfig.huggingFace().token())
                .build();

        this.supabaseWebClient = WebClient.builder()
                .baseUrl(apiConfig.supabase().url())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiConfig.supabase().key())
                .defaultHeader("apikey", apiConfig.supabase().key())
                .build();
    }

    // ... (createPanel, callHuggingFaceImageApi, and uploadToSupabaseStorage methods remain the same)
    public Panel createPanel(String prompt, UUID roomId) {
        logger.info("Generating image for prompt: '{}'", prompt);

        byte[] imageBytes = callHuggingFaceImageApi(prompt);
        logger.info("Successfully received image bytes from Hugging Face.");

        String imageUrl = uploadToSupabaseStorage(imageBytes, roomId);
        logger.info("Image uploaded to Supabase Storage at URL: {}", imageUrl);

        Panel newPanel = new Panel();
        newPanel.setPrompt(prompt);
        newPanel.setRoomId(roomId);
        newPanel.setImageUrl(imageUrl);
        Panel savedPanel = panelRepository.save(newPanel);

        logger.info("New panel with ID {} saved to the database.", savedPanel.getId());
        return savedPanel;
    }

    private byte[] callHuggingFaceImageApi(String prompt) {
        logger.info("Calling Hugging Face API with JSON payload...");
        String sanitizedPrompt = prompt.replace("\"", "\\\"");
        String jsonPayload = "{\"inputs\": \"" + sanitizedPrompt + "\"}";

        return huggingFaceWebClient.post()
                .uri("")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(jsonPayload), String.class)
                .retrieve()
                .bodyToMono(byte[].class)
                .block();
    }

    private String uploadToSupabaseStorage(byte[] imageBytes, UUID roomId) {
        String bucketName = "panels";
        String fileName = roomId.toString() + "/" + System.currentTimeMillis() + ".jpg";
        String uploadPath = "/storage/v1/object/" + bucketName + "/" + fileName;

        try {
            supabaseWebClient.post()
                    .uri(uploadPath)
                    .contentType(MediaType.IMAGE_JPEG)
                    .header("x-upsert", "true")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(imageBytes.length))
                    .body(BodyInserters.fromValue(imageBytes))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            return apiConfig.supabase().url() + uploadPath;

        } catch (Exception e) {
            logger.error("Failed to upload image to Supabase Storage", e);
            throw new RuntimeException("Error uploading image to storage.", e);
        }
    }
}