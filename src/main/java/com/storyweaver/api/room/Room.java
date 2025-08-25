package com.storyweaver.api.room;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "created_at", updatable = false, insertable = false)
    private Instant createdAt;

    // You can add other properties like a room name or description later
}