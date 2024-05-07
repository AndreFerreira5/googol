package com.googol.frontend.controller;

import com.googol.backend.gateway.GatewayRemote;
import com.googol.frontend.rmi.Gateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Optional;

@RestController
@RequestMapping("/api/fathers")
public class FatherURLsController {

    private final Gateway gateway;

    @Autowired
    public FatherURLsController(Gateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping
    public ArrayList<String> getFatherUrls(@RequestParam("url") String url) {
        Optional<GatewayRemote> gatewayRemote = gateway.getOrReconnect();

        return gatewayRemote.map(remote -> {
            try{
                return remote.getFatherUrls(url);
            } catch (Exception e){
                System.out.println("[ERROR] Failed to get father urls of " + url + ": " + e.getMessage());
                return null;
            }
        }).orElseGet(() -> null);
    }

    @GetMapping("/multiple")
    public ArrayList<ArrayList<String>> getFatherUrls(@RequestParam("urls") ArrayList<String> urls) {
        Optional<GatewayRemote> gatewayRemote = gateway.getOrReconnect();

        return gatewayRemote.map(remote -> {
            try{
                return remote.getFatherUrls(urls);
            } catch (Exception e){
                System.out.println("[ERROR] Failed to get father urls of " + urls + ": " + e.getMessage());
                return null;
            }
        }).orElseGet(() -> null);
    }
}
