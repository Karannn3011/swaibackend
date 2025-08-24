package com.storyweaver.api.controller;

import com.storyweaver.api.panel.CreatePanelRequest;
import com.storyweaver.api.panel.Panel;
import com.storyweaver.api.service.PanelService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/panels")
public class PanelController {

    private final PanelService panelService;

    public PanelController(PanelService panelService) {
        this.panelService = panelService;
    }

    @PostMapping
    public ResponseEntity<Panel> createPanel(@RequestBody CreatePanelRequest request) {
        Panel newPanel = panelService.createPanel(request.prompt(), request.roomId());
        // We will refine the response later
        return ResponseEntity.ok(newPanel);
    }
}