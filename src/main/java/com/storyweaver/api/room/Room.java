package com.storyweaver.api.room;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp; // Import this
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @CreationTimestamp // Add this annotation
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}