package com.storyweaver.api.service;

import com.storyweaver.api.config.ApiConfig;
import com.storyweaver.api.panel.Panel;
import com.storyweaver.api.panel.PanelRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
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

        byte[] imageBytes = callHuggingFaceImageApi(prompt);
        logger.info("Successfully received image bytes from Hugging Face. Size: {} bytes", imageBytes.length);

        // Save image locally first (for debugging and backup)
        String localImagePath = saveImageLocally(imageBytes, roomId, prompt);
        logger.info("Image saved locally at: {}", localImagePath);

        // Try to upload to Supabase, but don't fail the entire process if it fails
        String imageUrl;
        try {
            imageUrl = uploadToSupabaseStorage(imageBytes, roomId);
            logger.info("Image uploaded to Supabase Storage at URL: {}", imageUrl);
        } catch (Exception e) {
            logger.error("Failed to upload to Supabase, using local path as fallback", e);
            // Use local file path as fallback (you can serve this via a local endpoint later)
            imageUrl = "local://" + localImagePath;
        }

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

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.IMAGE_JPEG)); // Set specific Accept header for image
        headers.setBearerAuth(apiConfig.huggingFace().token());

        HttpEntity<String> requestEntity = new HttpEntity<>(jsonPayload, headers);

        ResponseEntity<byte[]> response = restTemplate.postForEntity(
                apiConfig.huggingFace().url(),
                requestEntity,
                byte[].class
        );

        return response.getBody();
    }

    private String saveImageLocally(byte[] imageBytes, UUID roomId, String prompt) {
        try {
            // Create images directory if it doesn't exist
            Path imagesDir = Path.of("generated-images");
            Files.createDirectories(imagesDir);

            // Create subdirectory for this room
            Path roomDir = imagesDir.resolve(roomId.toString());
            Files.createDirectories(roomDir);

            // Create filename with timestamp and sanitized prompt
            String sanitizedPrompt = prompt.replaceAll("[^a-zA-Z0-9\\s]", "")
                    .replaceAll("\\s+", "_")
                    .substring(0, Math.min(50, prompt.length()));
            String filename = System.currentTimeMillis() + "_" + sanitizedPrompt + ".jpg";
            Path imagePath = roomDir.resolve(filename);

            // Convert bytes to BufferedImage and save
            ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
            BufferedImage image = ImageIO.read(bis);

            if (image == null) {
                throw new RuntimeException("Could not read image from bytes");
            }

            // Save the image
            ImageIO.write(image, "jpg", imagePath.toFile());

            logger.info("Image saved locally: {} ({}x{} pixels)",
                    imagePath.toAbsolutePath(), image.getWidth(), image.getHeight());

            return imagePath.toAbsolutePath().toString();

        } catch (IOException e) {
            logger.error("Error saving image locally", e);
            throw new RuntimeException("Error saving image locally", e);
        }
    }

    private String uploadToSupabaseStorage(byte[] imageBytes, UUID roomId) {
        String bucketName = "panels";
        String fileName = roomId.toString() + "/" + System.currentTimeMillis() + ".jpg";
        String uploadPath = "/storage/v1/object/" + bucketName + "/" + fileName;
        String fullUrl = apiConfig.supabase().url() + uploadPath;

        Path tempFile = null;

        try {
            // Step 1: Convert bytes to BufferedImage and save as temp file
            logger.info("Converting bytes to image file for upload...");
            ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
            BufferedImage image = ImageIO.read(bis);

            if (image == null) {
                throw new RuntimeException("Could not read image from bytes");
            }

            // Create temp file
            tempFile = Files.createTempFile("panel-upload-", ".jpg");
            logger.info("Created temp file for upload: {}", tempFile.toString());

            // Write image to temp file
            ImageIO.write(image, "jpg", tempFile.toFile());
            logger.info("Successfully wrote image to temp file. Size: {} bytes", Files.size(tempFile));

            // Step 2: Upload the temp file (try both methods)
            return uploadFileToSupabase(tempFile, fullUrl, imageBytes);

        } catch (IOException e) {
            logger.error("Error creating or writing temp file for upload", e);
            throw new RuntimeException("Error processing image file for upload", e);
        } finally {
            // Clean up temp file
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    logger.info("Cleaned up upload temp file: {}", tempFile.toString());
                } catch (IOException e) {
                    logger.warn("Could not delete upload temp file: {}", tempFile.toString(), e);
                }
            }
        }
    }

    private String uploadFileToSupabase(Path tempFile, String fullUrl, byte[] originalBytes) {
        int maxRetries = 2;
        Exception lastException = null;

        // Try Method 1: Direct byte array upload
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.info("Trying direct byte upload (attempt {}/{}): {}", attempt, maxRetries, fullUrl);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.IMAGE_JPEG);
                headers.setBearerAuth(apiConfig.supabase().key());
                headers.set("apikey", apiConfig.supabase().key());
                headers.set("x-upsert", "true");
                headers.setContentLength(originalBytes.length);

                HttpEntity<byte[]> requestEntity = new HttpEntity<>(originalBytes, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                        fullUrl,
                        requestEntity,
                        String.class
                );

                if (response.getStatusCode().is2xxSuccessful()) {
                    logger.info("Successfully uploaded via direct bytes on attempt {}. Status: {}", attempt, response.getStatusCode());
                    return fullUrl;
                } else {
                    throw new RuntimeException("Upload failed with status: " + response.getStatusCode());
                }

            } catch (Exception e) {
                lastException = e;
                logger.warn("Direct byte upload attempt {} failed: {}", attempt, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // Try Method 2: Multipart file upload
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.info("Trying multipart file upload (attempt {}/{}): {}", attempt, maxRetries, fullUrl);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                headers.setBearerAuth(apiConfig.supabase().key());
                headers.set("apikey", apiConfig.supabase().key());
                headers.set("x-upsert", "true");

                FileSystemResource fileResource = new FileSystemResource(tempFile.toFile());

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", fileResource);

                HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                        fullUrl,
                        requestEntity,
                        String.class
                );

                if (response.getStatusCode().is2xxSuccessful()) {
                    logger.info("Successfully uploaded via multipart on attempt {}. Status: {}", attempt, response.getStatusCode());
                    return fullUrl;
                } else {
                    throw new RuntimeException("Upload failed with status: " + response.getStatusCode());
                }

            } catch (Exception e) {
                lastException = e;
                logger.warn("Multipart upload attempt {} failed: {}", attempt, e.getMessage());

                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        throw new RuntimeException("Error uploading file to storage after trying both methods", lastException);
    }
}