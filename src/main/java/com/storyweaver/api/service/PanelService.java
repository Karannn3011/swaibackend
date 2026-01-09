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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
            AuthHelper authHelper) {
        this.panelRepository = panelRepository;
        this.apiConfig = apiConfig;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(60))
                .readTimeout(Duration.ofSeconds(120))
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

        if (!room.getCurrentTurnUserId().equals(currentUserId)) {
            throw new RuntimeException("It's not your turn!");
        }

        List<Panel> recentPanels = panelRepository.findTop3ByRoomIdOrderByCreatedAtDesc(roomId);
        Collections.reverse(recentPanels);
        List<String> previousPrompts = recentPanels.stream()
                .map(Panel::getPrompt)
                .collect(Collectors.toList());

        String finalPrompt;
        // ** THIS IS THE NEW STYLE SUFFIX **
        String styleSuffix = ", in the style of a graphic novel, comic book art, vibrant colors, detailed line work";

        if (previousPrompts.isEmpty()) {
            finalPrompt = prompt + styleSuffix; // Add style to the first panel
        } else {
            String contextSummary = generateStoryContext(previousPrompts);
            finalPrompt = contextSummary + ", " + prompt + styleSuffix; // Add style to subsequent panels
        }

        logger.info("Generated final prompt with context: '{}'", finalPrompt);

        byte[] imageBytes = callPollinationsImageApi(finalPrompt);
        String imageUrl = uploadToSupabaseStorage(imageBytes, roomId);

        Panel newPanel = new Panel();
        newPanel.setPrompt(prompt); // Save the original, short prompt
        newPanel.setRoomId(roomId);
        newPanel.setImageUrl(imageUrl);
        newPanel.setAuthorId(currentUserId);
        Panel savedPanel = panelRepository.save(newPanel);
        room.setLastActivityAt(java.time.Instant.now());
        advanceTurn(room);
        logger.info("Updating room {} last activity to: {}", room.getId(), room.getLastActivityAt());
        roomRepository.save(room);
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
                    String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException(
                        "Upload failed with status: " + response.getStatusCode() + " and body: " + response.getBody());
            }

            logger.info("Successfully uploaded to Supabase. Status: {}", response.getStatusCode());
            return fullUrl;

        } catch (Exception e) {
            logger.error("Error uploading image to Supabase Storage", e);
            throw new RuntimeException("Error uploading image to storage", e);
        }
    }

    public String generateStoryContext(List<String> previousPrompts) {
        if (previousPrompts.isEmpty()) {
            return ""; // Should not happen based on calling logic, but safe to have.
        }

        String storySoFar = String.join(". ", previousPrompts);

        // ** NEW DYNAMIC PROMPT LOGIC **
        // Let's set a base of 20 words per panel to make the summary grow.
        int wordCount = previousPrompts.size() * 20;

        String summaryPrompt = String.format(
                "Summarize the following events in a short, connected paragraph of about %d words: %s",
                wordCount,
                storySoFar);

        logger.info("Sending prompt for growing summary: '{}'", summaryPrompt);

        String textApiBaseUrl = "https://text.pollinations.ai/prompt/";

        try {
            String encodedPrompt = URLEncoder.encode(summaryPrompt, StandardCharsets.UTF_8.toString());
            String url = textApiBaseUrl + encodedPrompt;

            String summary = restTemplate.getForObject(url, String.class);

            if (summary == null || summary.trim().isEmpty()) {
                // Fallback to a simple concatenation if the AI fails
                return storySoFar;
            }

            return summary.trim().replace("\"", "");

        } catch (Exception e) {
            logger.error("Failed to generate growing story context, falling back to simple concatenation.", e);
            // If the text generation fails, our fallback is to just join the prompts
            // together.
            return storySoFar;
        }
    }

}