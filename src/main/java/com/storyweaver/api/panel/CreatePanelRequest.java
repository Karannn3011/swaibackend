package com.storyweaver.api.panel;


import java.util.UUID;

public record CreatePanelRequest(String prompt, UUID roomId) {
}