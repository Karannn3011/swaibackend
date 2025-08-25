package com.storyweaver.api.room;

import com.storyweaver.api.service.AuthHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomRepository roomRepository;
    private final RoomMembershipRepository roomMembershipRepository;
    private final AuthHelper authHelper;

    public RoomController(
            RoomRepository roomRepository,
            RoomMembershipRepository roomMembershipRepository,
            AuthHelper authHelper
    ) {
        this.roomRepository = roomRepository;
        this.roomMembershipRepository = roomMembershipRepository;
        this.authHelper = authHelper;
    }

    @PostMapping
    public ResponseEntity<Room> createRoom() {
        // This method is correct.
        UUID currentUserId = authHelper.getCurrentUserId();
        Room newRoom = new Room();
        newRoom.setCurrentTurnUserId(currentUserId);
        newRoom.setCode(generateUniqueRoomCode());
        Room savedRoom = roomRepository.save(newRoom);

        RoomMembership membership = new RoomMembership();
        membership.setRoomId(savedRoom.getId());
        membership.setUserId(currentUserId);
        roomMembershipRepository.save(membership);

        return ResponseEntity.ok(savedRoom);
    }

    @PostMapping("/join/{code}")
    public ResponseEntity<Room> joinRoomByCode(@PathVariable String code) {
        // This method is correct.
        Room room = roomRepository.findByCode(code.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Room not found with this code"));
        return joinRoomById(room.getId());
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<Room> joinRoomById(@PathVariable UUID roomId) {
        UUID currentUserId = authHelper.getCurrentUserId();
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        if (roomMembershipRepository.findByRoomIdAndUserId(roomId, currentUserId).isPresent()) {
            return ResponseEntity.ok(room);
        }

        // ** THIS IS THE FIX for "Provided UUID required Long" **
        // We use the correct repository method to find members by the room's ID.
        List<RoomMembership> members = roomMembershipRepository.findByRoomIdOrderByJoinedAtAsc(roomId);
        if (members.size() >= 5) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
        }

        RoomMembership newMembership = new RoomMembership();
        newMembership.setRoomId(roomId);
        newMembership.setUserId(currentUserId);
        roomMembershipRepository.save(newMembership);
        return ResponseEntity.ok(room);
    }

    // ** THIS IS THE MISSING METHOD that fixes the 403 error **
    @GetMapping("/{roomId}")
    public ResponseEntity<RoomStateDto> getRoomState(@PathVariable UUID roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));

        List<RoomMembership> memberships = roomMembershipRepository.findByRoomIdOrderByJoinedAtAsc(roomId);
        List<UUID> memberIds = memberships.stream()
                .map(RoomMembership::getUserId)
                .collect(Collectors.toList());

        RoomStateDto roomState = new RoomStateDto(room, memberIds);
        return ResponseEntity.ok(roomState);
    }

    // Helper methods for code generation (these are correct)
    private String generateUniqueRoomCode() {
        String code;
        do {
            code = generateRandomCode(6);
        } while (roomRepository.findByCode(code).isPresent());
        return code;
    }

    private String generateRandomCode(int length) {
        final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom random = new SecureRandom();
        return random.ints(length, 0, CHARS.length())
                .mapToObj(CHARS::charAt)
                .map(Object::toString)
                .collect(Collectors.joining());
    }
}