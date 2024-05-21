package com.googol.frontend.handler;

import com.googol.backend.gateway.GatewayRemote;
import com.googol.frontend.rmi.Gateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;

@Component
public class WebSocketHandler extends TextWebSocketHandler {
    private static final Set<WebSocketSession> sessions = Collections.synchronizedSet(new HashSet<>());

    private final Gateway gateway;

    @Autowired
    public WebSocketHandler(Gateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);

        Optional<GatewayRemote> gatewayRemote = gateway.getOrReconnect();
        ArrayList<ArrayList<String>> systemInfo = gatewayRemote.map(remote -> {
            try{
                return remote.getSystemInfo();
            } catch (Exception e){
                System.out.println("[ERROR] Failed to get system info: " + e.getMessage());
                return null;
            }
        }).orElseGet(() -> null);

        if(systemInfo == null) return;

        String jsonMessage = buildJSONFromMessage(systemInfo);
        try {
            // Send the JSON message to the client
            session.sendMessage(new TextMessage(jsonMessage));
        } catch (Exception e) {
            System.out.println("Failed to send message: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }


    private static String buildJSONFromMessage(ArrayList<ArrayList<String>> message){
        // Use StringBuilder for efficient string concatenation
        StringBuilder jsonMessage = new StringBuilder();
        jsonMessage.append("{");

        // Append barrelsInfo
        jsonMessage.append("\"barrelsInfo\": [");
        ArrayList<String> barrelsInfo = message.get(0);
        for (int i = 0; i < barrelsInfo.size(); i++) {
            jsonMessage.append("\"").append(barrelsInfo.get(i)).append("\"");
            if (i < barrelsInfo.size() - 1) jsonMessage.append(",");
        }
        jsonMessage.append("],");

        // Append downloadersInfo
        jsonMessage.append("\"downloadersInfo\": [");
        ArrayList<String> downloadersInfo = message.get(1);
        for (int i = 0; i < downloadersInfo.size(); i++) {
            jsonMessage.append("\"").append(downloadersInfo.get(i)).append("\"");
            if (i < downloadersInfo.size() - 1) jsonMessage.append(",");
        }
        jsonMessage.append("],");

        // Append urlsToProcess as a numeric value
        jsonMessage.append("\"urlsToProcess\": ");
        ArrayList<String> urlsToProcess = message.get(2);
        if (!urlsToProcess.isEmpty()) {
            jsonMessage.append(urlsToProcess.get(0));
        } else {
            jsonMessage.append("0");
        }
        jsonMessage.append(",");

        // Append topSearches
        jsonMessage.append("\"topSearches\": [");
        ArrayList<String> toptenSearches = message.get(3);
        for (int i = 0; i < toptenSearches.size(); i++) {
            jsonMessage.append("\"").append(toptenSearches.get(i)).append("\"");
            if (i < toptenSearches.size() - 1) jsonMessage.append(",");
        }
        jsonMessage.append("]");

        jsonMessage.append("}");

        return jsonMessage.toString();
    }


    public static void broadcast(ArrayList<ArrayList<String>> message) {
        String jsonMessage = buildJSONFromMessage(message);

        synchronized (sessions) {
            for (WebSocketSession session : sessions) {
                try {
                    // Send the JSON message to the client
                    session.sendMessage(new TextMessage(jsonMessage));
                } catch (Exception e) {
                    System.out.println("Failed to send message: " + e.getMessage());
                }
            }
        }
    }
}
