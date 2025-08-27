package com.storyweaver.api.user;

import com.storyweaver.api.service.AuthHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserProfileRepository userProfileRepository;
    private final AuthHelper authHelper;

    public UserController(UserProfileRepository userProfileRepository, AuthHelper authHelper) {
        this.userProfileRepository = userProfileRepository;
        this.authHelper = authHelper;
    }

    // Endpoint to get the current user's profile
    @GetMapping("/me")
    public ResponseEntity<UserProfile> getMyProfile() {
        UUID currentUserId = authHelper.getCurrentUserId();
        return userProfileRepository.findById(currentUserId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Endpoint to create or update a user's profile (i.e., set their username)
    @PostMapping("/me")
    public ResponseEntity<UserProfile> createOrUpdateProfile(@RequestBody Map<String, String> payload) {
        UUID currentUserId = authHelper.getCurrentUserId();
        String username = payload.get("username");

        if (username == null || username.trim().length() < 3) {
            return ResponseEntity.badRequest().build(); // Basic validation
        }

        UserProfile profile = new UserProfile();
        profile.setId(currentUserId);
        profile.setUsername(username.trim());
        UserProfile savedProfile = userProfileRepository.save(profile);
        return ResponseEntity.ok(savedProfile);
    }

    // Endpoint to get a batch of user profiles by their IDs
    @PostMapping("/profiles")
    public ResponseEntity<List<UserProfile>> getUserProfiles(@RequestBody List<UUID> userIds) {
        List<UserProfile> profiles = userProfileRepository.findAllById(userIds);
        return ResponseEntity.ok(profiles);
    }
}