import java.io.*;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;

public class Gateway extends UnicastRemoteObject implements GatewayRemote {
    private static final int rmiPort = 1099;
    public static final int CRAWLING_MAX_DEPTH = 2;
    public static final CrawlingStrategy crawlingStrategy = new BFSStartegy();
    public static AtomicLong PARSED_URLS = new AtomicLong();
    public static final String MULTICAST_ADDRESS = "224.3.2.1";
    public static final int PORT = 4322;
    public static final char DELIMITER = '|';
    private static LinkedBlockingDeque<RawUrl> urlsDeque = new LinkedBlockingDeque<>();
    private static ConcurrentHashMap<String, String> barrelsOnline = new ConcurrentHashMap<>();


    protected Gateway() throws RemoteException {}

    public RawUrl getUrlFromDeque() throws InterruptedException, RemoteException {
        return urlsDeque.take();
    }

    @Override
    public void addUrlToDeque(RawUrl rawUrl) throws RemoteException{
        crawlingStrategy.addUrl(urlsDeque, rawUrl);
    }

    @Override
    public void addUrlsToDeque(ArrayList<RawUrl> rawUrls) throws RemoteException{
        for(RawUrl rawUrl: rawUrls){
            crawlingStrategy.addUrl(urlsDeque, rawUrl);
        }
    }

    @Override
    public char getDelimiter() throws  RemoteException{
        return DELIMITER;
    }

    @Override
    public int getCrawlingMaxDepth() throws  RemoteException{
        return CRAWLING_MAX_DEPTH;
    }

    @Override
    public String getMulticastAddress() throws  RemoteException{
        return MULTICAST_ADDRESS;
    }

    @Override
    public int getPort() throws  RemoteException{
        return PORT;
    }

    @Override
    public void incrementParsedUrls() throws RemoteException {
        PARSED_URLS.incrementAndGet();
    }

    @Override
    public long getParsedUrls() throws RemoteException {
        return PARSED_URLS.get();
    }

    @Override
    public long incrementAndGetParsedUrls() throws RemoteException{
        return PARSED_URLS.incrementAndGet();
    }

    @Override
    public void registerBarrel(String barrelEndpoint) throws RemoteException {
        barrelsOnline.put(barrelEndpoint, barrelEndpoint);
        System.out.println("Barrel registered: " + barrelEndpoint);
    }

    @Override
    public void unregisterBarrel(String barrelEndpoint) throws RemoteException {
        barrelsOnline.remove(barrelEndpoint);
        System.out.println("Barrel unregistered: " + barrelEndpoint);
    }

    @Override
    public ArrayList<String> getRegisteredBarrels() throws RemoteException {
        return new ArrayList<>(barrelsOnline.values());
    }
    //TODO make more functions so the barrels send their load info to the gateway so it can choose dynamically which one to get info from


    private static boolean setupGatewayRMI(){
        try {
            Gateway gateway = new Gateway();
            LocateRegistry.createRegistry(rmiPort);
            Naming.rebind("//localhost/GatewayService", gateway);
            System.out.println("Gateway Service bound in registry");
            return true;
        } catch (Exception e) {
            System.out.println("Gateway Service error: " + e.getMessage());
            return false;
        }
    }


    public static void main(String[] args) throws InterruptedException {
        if(!setupGatewayRMI()) System.exit(1);

        while(true) Thread.sleep(30000);
    }
}