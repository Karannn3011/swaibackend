package com.storyweaver.api.room;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class RoomStateDto {
    private Room room;
    private List<UUID> members;
}