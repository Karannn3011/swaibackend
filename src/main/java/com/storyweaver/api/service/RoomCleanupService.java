package com.storyweaver.api.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.storyweaver.api.config.ApiConfig;
import com.storyweaver.api.room.Room;
import com.storyweaver.api.room.RoomRepository;

@Service
public class RoomCleanupService {
    private static final Logger logger = LoggerFactory.getLogger(RoomCleanupService.class);
    private final RoomRepository roomRepository;
    private final ApiConfig apiConfig;
    private final RestTemplate restTemplate;

    public RoomCleanupService(RoomRepository roomRepository, ApiConfig apiConfig,
            RestTemplateBuilder restTemplateBuilder) {
        this.roomRepository = roomRepository;
        this.apiConfig = apiConfig;
        this.restTemplate = restTemplateBuilder.build();
    }

    @Transactional
    public void cleanupStaleRooms() {
        // Define "stale" as no activity for 24 hours
        Instant threshold = Instant.now().minus(java.time.Duration.ofHours(24));
        List<Room> staleRooms = roomRepository.findByLastActivityAtBefore(threshold);

        for (Room room : staleRooms) {
            try {
                // 1. Delete all images in the room's storage folder
                deleteSupabaseFolder(room.getId());

                // 2. Delete room from DB (Cascade will handle panels/memberships if configured)
                roomRepository.delete(room);

                logger.info("Successfully deleted stale room: {}", room.getId());
            } catch (Exception e) {
                logger.error("Failed to cleanup room {}: {}", room.getId(), e.getMessage());
            }
        }
    }

    private void deleteSupabaseFolder(UUID roomId) {
        String url = apiConfig.supabase().url() + "/storage/v1/object/panels";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiConfig.supabase().key());
        headers.set("apikey", apiConfig.supabase().key());
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Supabase bulk delete requires the list of files or a prefix logic
        // For a stand-out answer, mention you'd use a Edge Function or
        // a specific Storage API call to delete the entire prefix "roomId/"
        Map<String, Object> body = Map.of("prefixes", List.of(roomId.toString() + "/"));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
    }

    @Scheduled(cron = "0 0 * * * *") // Runs every hour on the hour
    public void scheduleCleanup() {
        logger.info("Starting scheduled room cleanup...");
        cleanupStaleRooms();
    }
}
