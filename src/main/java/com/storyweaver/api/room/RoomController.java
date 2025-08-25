package com.storyweaver.api.room;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomRepository roomRepository;

    public RoomController(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @PostMapping
    public ResponseEntity<Room> createRoom() {
        Room newRoom = new Room();
        Room savedRoom = roomRepository.save(newRoom);
        return ResponseEntity.ok(savedRoom);
    }
}