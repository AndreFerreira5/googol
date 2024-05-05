package com.googol.frontend.rmi;

import com.googol.backend.gateway.GatewayRemote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.annotation.PostConstruct;
import java.rmi.Naming;

@Configuration
public class Gateway {

    //@Autowired
    private GatewayRemote gatewayRemote;

    //@Autowired
    //private RmiUpdateListener updateListener;

    @PostConstruct
    public void init(){
        try{
            // TODO change this to get the host and port dynamically
            gatewayRemote = (GatewayRemote) Naming.lookup("//localhost:1099/Gateway");
            //gatewayRemote.registerListener(updateListener);
            //System.out.println("Update Listener Registered!!!");
        } catch (Exception e){
            e.printStackTrace();
            throw new IllegalStateException("Failed to connect to the RMI service at startup.", e);
        }
    }

    @Bean
    public GatewayRemote gatewayRemote() {
        return gatewayRemote;
    }
}
