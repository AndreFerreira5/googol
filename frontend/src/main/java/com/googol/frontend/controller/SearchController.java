package com.googol.frontend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import com.googol.backend.gateway.GatewayRemote;

import java.util.ArrayList;
import java.util.Arrays;

@RestController
public class SearchController {

    @Autowired
    private GatewayRemote gatewayRemote;

    @GetMapping("/search")
    public ArrayList<ArrayList<String>> search(@RequestParam("query") String query,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int pageSize,
                                               @RequestParam(defaultValue = "true") boolean isFreshSearch
                                               ) {
        try{
            String[] splitQuery = query.split(" ");
            if(splitQuery.length > 1){
                return gatewayRemote.searchWordSet(new ArrayList<>(Arrays.asList(splitQuery)),
                                            page,
                                            pageSize,
                                            isFreshSearch
                                            );
            } else {
                return gatewayRemote.searchWord(splitQuery[0],
                                                page,
                                                pageSize,
                                                isFreshSearch
                                                  );
            }
        } catch (Exception e){
            return null;
        }
    }
}
