package com.storyweaver.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    private static final Logger logger = LoggerFactory.getLogger(ImageController.class);

    @GetMapping("/{roomId}/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable UUID roomId, @PathVariable String filename) {
        try {
            Path imagePath = Path.of("generated-images", roomId.toString(), filename);
            File imageFile = imagePath.toFile();

            if (!imageFile.exists()) {
                logger.warn("Image file not found: {}", imagePath.toAbsolutePath());
                return ResponseEntity.notFound().build();
            }

            logger.info("Serving local image: {}", imagePath.toAbsolutePath());

            FileSystemResource resource = new FileSystemResource(imageFile);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (Exception e) {
            logger.error("Error serving image: {}/{}", roomId, filename, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/list/{roomId}")
    public ResponseEntity<?> listImages(@PathVariable UUID roomId) {
        try {
            Path roomDir = Path.of("generated-images", roomId.toString());
            File dir = roomDir.toFile();

            if (!dir.exists() || !dir.isDirectory()) {
                return ResponseEntity.ok().body("[]");
            }

            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jpg"));
            if (files == null) {
                return ResponseEntity.ok().body("[]");
            }

            StringBuilder response = new StringBuilder("[");
            for (int i = 0; i < files.length; i++) {
                if (i > 0) response.append(",");
                response.append("\"").append(files[i].getName()).append("\"");
            }
            response.append("]");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.toString());

        } catch (Exception e) {
            logger.error("Error listing images for room: {}", roomId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}