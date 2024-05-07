package com.googol.frontend.controller;

import com.googol.frontend.handler.WebSocketHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemMetricsController {
    @GetMapping("/trigger-update")
    public String triggerUpdate() {
        WebSocketHandler.broadcast("Hello, WebSocket Clients!");
        return "Update triggered.";
    }
}
