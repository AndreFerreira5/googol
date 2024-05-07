package com.googol.frontend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import com.googol.backend.gateway.GatewayRemote;
import com.googol.frontend.rmi.Gateway;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final Gateway gateway;

    @Autowired
    public SearchController(Gateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping
    public ArrayList<ArrayList<String>> search(@RequestParam("query") String query,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int pageSize,
                                               @RequestParam(defaultValue = "true") boolean isFreshSearch
                                               ) {
        Optional<GatewayRemote> gatewayRemote = gateway.getOrReconnect();

        return gatewayRemote.map(remote -> {
            try{
                String[] splitQuery = query.split(" ");
                if(splitQuery.length > 1){
                    return remote.searchWordSet(new ArrayList<>(Arrays.asList(splitQuery)),
                            page,
                            pageSize,
                            isFreshSearch
                    );
                } else {
                    return remote.searchWord(splitQuery[0],
                            page,
                            pageSize,
                            isFreshSearch
                    );
                }
            } catch (Exception e){
                System.out.println("[ERROR] Failed to search: " + e.getMessage());
                return null;
            }
        }).orElseGet(() -> null);
    }
}
