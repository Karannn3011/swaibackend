package com.storyweaver.api.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

import java.time.Instant;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByCode(String code);
    
    // Find rooms where last_activity_at is older than the threshold
    List<Room> findByLastActivityAtBefore(Instant threshold);
}