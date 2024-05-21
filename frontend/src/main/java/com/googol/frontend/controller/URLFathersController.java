package com.googol.frontend.controller;

import com.googol.backend.gateway.GatewayRemote;
import com.googol.frontend.rmi.Gateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Optional;

@RestController
@RequestMapping("/api/father")
public class URLFathersController {

    private final Gateway gateway;

    @Autowired
    public URLFathersController(Gateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping
    public ArrayList<String> index(@RequestParam("url") String url) {
        Optional<GatewayRemote> gatewayRemote = gateway.getOrReconnect();

        return gatewayRemote.map(remote -> {
            try{
                return remote.getFatherUrls(url);
            } catch (Exception e){
                System.out.println("[ERROR] Failed to get father URLs from " + url + ": " + e.getMessage());
                return null;
            }
        }).orElseGet(() -> null);
    }
}
