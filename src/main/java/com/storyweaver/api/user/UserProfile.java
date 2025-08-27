package com.storyweaver.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import java.util.UUID;

@Data
@Entity
@Table(name = "user_profiles") // This table will store our usernames
public class UserProfile {

    @Id
    private UUID id; // This will be the same as the user's Supabase Auth UUID

    @Column(unique = true, nullable = false)
    private String username;
}