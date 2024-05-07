package com.googol.backend.gateway;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface UpdateCallback extends Remote {
    void onUpdate(ArrayList<String> message) throws RemoteException;
}