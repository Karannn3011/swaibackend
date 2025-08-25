package com.storyweaver.api.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomMembershipRepository extends JpaRepository<RoomMembership, Long> {
    List<RoomMembership> findByRoomIdOrderByJoinedAtAsc(UUID roomId);
    Optional<RoomMembership> findByRoomIdAndUserId(UUID roomId, UUID userId);
}