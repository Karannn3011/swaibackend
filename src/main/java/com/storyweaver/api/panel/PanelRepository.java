package com.storyweaver.api.panel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PanelRepository extends JpaRepository<Panel, Long> {

    List<Panel> findByRoomIdOrderByCreatedAtAsc(UUID roomId);

    List<Panel> findTop3ByRoomIdOrderByCreatedAtDesc(UUID roomId);
}