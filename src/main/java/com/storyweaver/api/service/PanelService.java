package com.storyweaver.api.service;

import com.storyweaver.api.config.ApiConfig;
import com.storyweaver.api.panel.Panel;
import com.storyweaver.api.panel.PanelRepository;
import com.storyweaver.api.room.Room;
import com.storyweaver.api.room.RoomMembership;
import com.storyweaver.api.room.RoomMembershipRepository;
import com.storyweaver.api.room.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class PanelService {

    private static final Logger logger = LoggerFactory.getLogger(PanelService.class);

    // These are the fields that need to be initialized
    private final PanelRepository panelRepository;
    private final ApiConfig apiConfig;
    private final RestTemplate restTemplate;
    private final RoomRepository roomRepository;
    private final RoomMembershipRepository roomMembershipRepository;
    private final AuthHelper authHelper;

    // This is the correct constructor
    public PanelService(
            PanelRepository panelRepository,
            ApiConfig apiConfig,
            RestTemplateBuilder restTemplateBuilder,
            RoomRepository roomRepository,
            RoomMembershipRepository roomMembershipRepository,
            AuthHelper authHelper
    ) {
        this.panelRepository = panelRepository;
        this.apiConfig = apiConfig;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(60))
                .setReadTimeout(Duration.ofSeconds(120))
                .build();
        this.roomRepository = roomRepository;
        this.roomMembershipRepository = roomMembershipRepository;
        this.authHelper = authHelper;
    }

    @Transactional // Ensures the whole method succeeds or fails together
    public Panel createPanel(String prompt, UUID roomId) {
        UUID currentUserId = authHelper.getCurrentUserId();
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        // 1. Check if it's the user's turn
        if (!room.getCurrentTurnUserId().equals(currentUserId)) {
            throw new RuntimeException("It's not your turn!");
        }

        // ... (image generation and upload logic remains the same)
        byte[] imageBytes = callPollinationsImageApi(prompt);
        String imageUrl = uploadToSupabaseStorage(imageBytes, roomId);

        // 2. Create and save the new panel
        Panel newPanel = new Panel();
        newPanel.setPrompt(prompt);
        newPanel.setRoomId(roomId);
        newPanel.setImageUrl(imageUrl);
        newPanel.setAuthorId(currentUserId);
        Panel savedPanel = panelRepository.save(newPanel);

        // 3. Advance the turn to the next user
        advanceTurn(room);

        return savedPanel;
    }

    private void advanceTurn(Room room) {
        List<RoomMembership> members = roomMembershipRepository.findByRoomIdOrderByJoinedAtAsc(room.getId());
        if (members.size() <= 1) {
            return; // Turn doesn't change if there's only one person
        }

        UUID currentTurnUserId = room.getCurrentTurnUserId();
        int currentIndex = -1;
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).getUserId().equals(currentTurnUserId)) {
                currentIndex = i;
                break;
            }
        }

        int nextIndex = (currentIndex + 1) % members.size();
        UUID nextUserId = members.get(nextIndex).getUserId();

        room.setCurrentTurnUserId(nextUserId);
        roomRepository.save(room);
        logger.info("Advanced turn in room {} to user {}", room.getId(), nextUserId);
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