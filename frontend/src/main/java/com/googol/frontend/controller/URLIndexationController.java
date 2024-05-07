package com.googol.frontend.controller;

import com.googol.backend.gateway.GatewayRemote;
import com.googol.frontend.rmi.Gateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Optional;

@RestController
@RequestMapping("/api/index")
public class URLIndexationController {

    private final Gateway gateway;

    @Autowired
    public URLIndexationController(Gateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping
    public boolean index(@RequestParam("url") String url) {
        Optional<GatewayRemote> gatewayRemote = gateway.getOrReconnect();

        return gatewayRemote.map(remote -> {
            try{
                return remote.addUrlToUrlsDeque(url);
            } catch (Exception e){
                System.out.println("[ERROR] Failed to index: " + e.getMessage());
                return false;
            }
        }).orElseGet(() -> false);
    }

    @PostMapping("/multiple")
    public ArrayList<Integer> index(@RequestParam("urls") ArrayList<String> urls) {
        Optional<GatewayRemote> gatewayRemote = gateway.getOrReconnect();

        return gatewayRemote.map(remote -> {
            try{
                return remote.addUrlsToUrlsDeque(urls);
            } catch (Exception e){
                System.out.println("[ERROR] Failed to index: " + e.getMessage());
                return null;
            }
        }).orElseGet(() -> null);
    }
}
