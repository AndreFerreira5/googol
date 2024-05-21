package com.googol.frontend.rmi;

import com.googol.backend.gateway.UpdateCallback;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;

import com.googol.frontend.handler.WebSocketHandler;

public class UpdateCallbackImpl extends UnicastRemoteObject implements UpdateCallback {
    public UpdateCallbackImpl() throws RemoteException {
        super();
    }

    @Override
    public void onUpdate(ArrayList<ArrayList<String>> message) throws RemoteException {
        WebSocketHandler.broadcast(message);
    }
}