import java.io.*;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.Collections;
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
    private static double averageLatency = 0;
    private static double totalRequests = 0;
    private static LinkedBlockingDeque<RawUrl> urlsDeque = new LinkedBlockingDeque<>();
    private static ConcurrentHashMap<String, String> barrelsOnline = new ConcurrentHashMap<>();


    protected Gateway() throws RemoteException {}

    public RawUrl getUrlFromDeque() throws InterruptedException, RemoteException {
        return urlsDeque.take();
    }

    @Override
    public void addUrlToUrlsDeque(RawUrl rawUrl) throws RemoteException{
        crawlingStrategy.addUrl(urlsDeque, rawUrl);
    }

    @Override
    public void addUrlToUrlsDeque(String url) throws RemoteException{
        crawlingStrategy.addUrl(urlsDeque, new RawUrl(url));
    }

    @Override
    public void addRawUrlsToUrlsDeque(ArrayList<RawUrl> rawUrls) throws RemoteException{
        for(RawUrl rawUrl: rawUrls){
            crawlingStrategy.addUrl(urlsDeque, rawUrl);
        }
    }
    
    @Override
    public void addUrlsToUrlsDeque(ArrayList<String> rawUrls) throws RemoteException{
        for(String rawUrl: rawUrls){
            crawlingStrategy.addUrl(urlsDeque, new RawUrl(rawUrl));
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
        System.out.println("\nBarrel registered: " + barrelEndpoint);
    }

    @Override
    public void unregisterBarrel(String barrelEndpoint) throws RemoteException {
        barrelsOnline.remove(barrelEndpoint);
        System.out.println("\nBarrel unregistered: " + barrelEndpoint);
    }

    @Override
    public ArrayList<String> getRegisteredBarrels() throws RemoteException {
        return new ArrayList<>(barrelsOnline.values());
    }

    @Override
    public int getRegisteredBarrelsCount() throws RemoteException{
        return barrelsOnline.size();
    }
    //TODO make more functions so the barrels send their load info to the gateway so it can choose dynamically which one to get info from

    private String getRandomBarrel(){
        if(barrelsOnline.isEmpty()) return null;
        Collection<String> collection = barrelsOnline.values();
        int random = (int) (Math.random() * collection.size());
        return collection.toArray()[random].toString();
    }


    private void updateAverageLatency(double latency) {
        averageLatency = (averageLatency + latency) / ++totalRequests;
    }

    private double getAverageLatencySeconds() {
        return averageLatency/1_000_000_000.0;
    }
    private double getAverageLatencyMilliSeconds() {
        return averageLatency/1_000_000.0;
    }
    private double getAverageLatencyMicroSeconds() {
        return averageLatency/1_000.0;
    }
    private double getAverageLatencyNanoSeconds() {
        return averageLatency;
    }

    @Override
    public ArrayList<String> getSystemInfo(){
        ArrayList<String> systemInfo = new ArrayList<>();
        systemInfo.add("Average latency: " + getAverageLatencySeconds() + "s"); // average latency
        try{
            ArrayList<String> barrels = getRegisteredBarrels();
            StringBuilder barrelsString = new StringBuilder();
            barrelsString.append("Registered barrels: \n");
            for(String barrel: barrels){
                barrelsString.append(barrel).append("\n");
            }
            systemInfo.add(String.valueOf(barrelsString)); // registered barrels
        } catch (RemoteException ignored) {}

        // TODO also return top 10 visited urls
        return systemInfo;
    }

    @Override
    public ArrayList<ArrayList<String>> searchWord(String word) throws RemoteException{
        String randomBarrel = getRandomBarrel();
        if (randomBarrel == null) return null;

        IndexStorageBarrelRemote barrel;
        try {
            barrel = (IndexStorageBarrelRemote) Naming.lookup(randomBarrel);
        } catch (Exception e) {
            System.out.println("Error looking up barrel: " + e.getMessage());
            return null;
        }

        long start = System.nanoTime();
        ArrayList<ArrayList<String>> response = barrel.searchWord(word);
        long end = System.nanoTime();
        double elapsedTime = end - start;
        //response.add(new ArrayList<>(Collections.singletonList("Elapsed time: " + elapsedTime + "ms")));
        updateAverageLatency(elapsedTime);
        return response;
    }

    @Override
    public ArrayList<ArrayList<String>> searchWords(ArrayList<String> words) throws RemoteException{
        String randomBarrel = getRandomBarrel();
        if (randomBarrel == null) return null;

        IndexStorageBarrelRemote barrel;
        try {
            barrel = (IndexStorageBarrelRemote) Naming.lookup(randomBarrel);
        } catch (Exception e) {
            System.out.println("Error looking up barrel: " + e.getMessage());
            return null;
        }

        long start = System.nanoTime();
        ArrayList<ArrayList<String>> response = barrel.searchWords(words);
        long end = System.nanoTime();
        double elapsedTime = end - start;
        //response.add(new ArrayList<>(Collections.singletonList("Elapsed time: " + elapsedTime + "ms")));
        updateAverageLatency(elapsedTime);
        return response;
    }


    @Override
    public ArrayList<ArrayList<String>> searchWordSet(ArrayList<String> words) throws RemoteException{
        String randomBarrel = getRandomBarrel();
        if (randomBarrel == null) return null;

        IndexStorageBarrelRemote barrel;
        try {
            barrel = (IndexStorageBarrelRemote) Naming.lookup(randomBarrel);
        } catch (Exception e) {
            System.out.println("Error looking up barrel: " + e.getMessage());
            return null;
        }

        long start = System.nanoTime();
        ArrayList<ArrayList<String>> response = barrel.searchWordSet(words);
        long end = System.nanoTime();
        double elapsedTime = end - start;
        //response.add(new ArrayList<>(Collections.singletonList("Elapsed time: " + elapsedTime + "ms")));
        updateAverageLatency(elapsedTime);
        return response;
    }


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

        while(true){
            int barrelsNum = barrelsOnline.size();
            System.out.print("\rBarrels online: " + barrelsNum + " - Urls in queue: " + urlsDeque.size());
            Thread.sleep(5000);
        }
    }
}