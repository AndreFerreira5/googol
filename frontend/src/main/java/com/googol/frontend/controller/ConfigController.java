package com.googol.frontend.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Value("${server.address}")
    private String host;

    @Value("${server.port}")
    private String port;

    @Value("${websocket.endpoint}")
    private String websocketEndpoint;

    @GetMapping(value = "/host", produces = "text/plain")
    public String getHost() {
        return host;
    }

    @GetMapping(value = "/port", produces = "text/plain")
    public String getPort() {
        return port;
    }

    @GetMapping(value = "/websocket-endpoint", produces = "text/plain")
    public String getWebsocketEndpoint() {
        return websocketEndpoint;
    }
}
