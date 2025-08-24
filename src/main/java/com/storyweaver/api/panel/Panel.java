package com.storyweaver.api.panel;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "panels")
public class Panel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", updatable = false, insertable = false)
    private Instant createdAt;

    private String prompt;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "room_id")
    private UUID roomId;

}