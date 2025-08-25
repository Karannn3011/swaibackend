package com.storyweaver.api.controller;

import com.storyweaver.api.panel.CreatePanelRequest;
import com.storyweaver.api.panel.Panel;
import com.storyweaver.api.panel.PanelRepository; // Import
import com.storyweaver.api.service.PanelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List; // Import
import java.util.UUID; // Import

@RestController
@RequestMapping("/api/panels")
public class PanelController {

    private final PanelService panelService;
    private final PanelRepository panelRepository; // Add

    // Update constructor
    public PanelController(PanelService panelService, PanelRepository panelRepository) {
        this.panelService = panelService;
        this.panelRepository = panelRepository;
    }

    @PostMapping
    public ResponseEntity<Panel> createPanel(@RequestBody CreatePanelRequest request) {
        // The service will now handle the logic
        Panel newPanel = panelService.createPanel(request.prompt(), request.roomId());
        return ResponseEntity.ok(newPanel);
    }

    // Add this new endpoint
    @GetMapping("/room/{roomId}")
    public ResponseEntity<List<Panel>> getPanelsForRoom(@PathVariable UUID roomId) {
        List<Panel> panels = panelRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
        return ResponseEntity.ok(panels);
    }
}