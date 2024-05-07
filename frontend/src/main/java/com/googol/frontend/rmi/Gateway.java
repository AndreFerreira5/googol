package com.googol.frontend.rmi;

import com.googol.backend.gateway.GatewayRemote;
import com.googol.backend.gateway.UpdateCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import javax.annotation.PostConstruct;
import java.rmi.Naming;
import java.util.Optional;

@Configuration
public class Gateway {

    @Nullable
    private GatewayRemote gatewayRemote;
    private UpdateCallback clientCallback;

    @Value("${gateway.rmi.host}")
    private String gatewayRMIHost;

    @Value("${gateway.rmi.port}")
    private String gatewayRMIPort;

    @Value("${gateway.rmi.serviceName}")
    private String gatewayRMIServiceName;

    private String gatewayRMIURL;


    public GatewayRemote connectToGatewayRMI(){
        try{
            return (GatewayRemote) Naming.lookup(gatewayRMIURL);
        } catch (Exception e){
            System.err.println("[ERROR] Failed to connect to the Gateway RMI service: " + e.getMessage());
            return null;
        }
    }

    @PostConstruct
    public void init(){
        gatewayRMIURL = "//" + gatewayRMIHost + ":" + gatewayRMIPort + "/" + gatewayRMIServiceName;
        gatewayRemote = connectToGatewayRMI();

        // Register the callback
        if (gatewayRemote != null) {
            try {
                clientCallback = new UpdateCallbackImpl();
                gatewayRemote.registerUpdateCallback(clientCallback);
                System.out.println("Registered callback successfully.");
            } catch (Exception e) {
                System.err.println("[ERROR] Failed to register the callback: " + e.getMessage());
            }
        }
    }

    @Bean
    public Optional<GatewayRemote> gatewayRemote() {
        return Optional.ofNullable(gatewayRemote);
    }

    public Optional<GatewayRemote> getOrReconnect(){
        if(gatewayRemote == null){
            gatewayRemote = connectToGatewayRMI();
        }
        return Optional.ofNullable(gatewayRemote);
    }
}

