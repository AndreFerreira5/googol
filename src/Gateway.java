import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;


class GatewayConfigLoader {
    private static final Properties properties = new Properties();

    public static class ConfigurationException extends RuntimeException {
        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static{
        loadProperties();
    }

    private static void loadProperties(){
        String filePath = "config/gateway.properties";
        try (InputStream input = new FileInputStream(filePath)){
            properties.load(input);
        } catch (IOException e){
            throw new ConfigurationException("Failed to load configuration properties", e);
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
}



class BarrelMetrics {
    private double averageResponseTime = 0;
    private long requestCount = 0;
    private double availability = 0;

    public void updateMetrics(double responseTime) {
        averageResponseTime += (averageResponseTime + responseTime) / ++requestCount;
    }

    public double getAverageResponseTime() {
        return averageResponseTime;
    }

    public void setAvailability(double availability) {
        this.availability = availability;
    }

    public double getAvailability() {
        return availability;
    }
}


public class Gateway extends UnicastRemoteObject implements GatewayRemote {
    private static int rmiPort;
    private static String rmiEndpoint;
    public static final int CRAWLING_MAX_DEPTH = 1;
    public static final CrawlingStrategy crawlingStrategy = new BFSStartegy();
    public static AtomicLong PARSED_URLS = new AtomicLong();
    public static final String MULTICAST_ADDRESS = "224.3.2.1";
    public static final int PORT = 4322;
    public static final char DELIMITER = '|';
    private static final LinkedBlockingDeque<RawUrl> urlsDeque = new LinkedBlockingDeque<>();
    private static final ConcurrentHashMap<String, String> barrelsOnline = new ConcurrentHashMap<>();
    private static final HashMap<String, BarrelMetrics> barrelMetricsMap = new HashMap<>();
    private static final HashMap<String, Integer> searchedStrings = new HashMap<>();



    protected Gateway() throws RemoteException {}

    public RawUrl getUrlFromDeque() throws InterruptedException, RemoteException {
        return urlsDeque.take();
    }

    @Override
    public void addUrlToUrlsDeque(RawUrl rawUrl) throws RemoteException{
        crawlingStrategy.addUrl(urlsDeque, rawUrl);
    }

    @Override
    public boolean addUrlToUrlsDeque(String url) throws RemoteException{
        try {
            crawlingStrategy.addUrl(urlsDeque, new RawUrl(url));
        } catch (IllegalArgumentException e){
            return false;
        }
        return true;
    }

    @Override
    public void addRawUrlsToUrlsDeque(ArrayList<RawUrl> rawUrls) throws RemoteException{
        for(RawUrl rawUrl: rawUrls){
            crawlingStrategy.addUrl(urlsDeque, rawUrl);
        }
    }
    
    @Override
    public ArrayList<Integer> addUrlsToUrlsDeque(ArrayList<String> rawUrls) throws RemoteException{
        ArrayList<Integer> badUrls = new ArrayList<>();
        for(int i=0; i<rawUrls.size(); i++){
            try {
                crawlingStrategy.addUrl(urlsDeque, new RawUrl(rawUrls.get(i)));
            } catch (IllegalArgumentException e){
                badUrls.add(i);
            }
        }
        if(badUrls.isEmpty()) return null;
        else return badUrls;
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
        barrelMetricsMap.put(barrelEndpoint, new BarrelMetrics());
        System.out.println("\nBarrel registered: " + barrelEndpoint);
    }

    @Override
    public void unregisterBarrel(String barrelEndpoint) throws RemoteException {
        barrelsOnline.remove(barrelEndpoint);
        barrelMetricsMap.remove(barrelEndpoint);
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

    @Override
    public String getRandomBarrelRemote() throws RemoteException {
        return getRandomBarrel();
    }

    @Override
    public String getMostAvailableBarrelRemote(String callingBarrel){
        return getMostAvailableBarrel(callingBarrel);
    }

    @Override
    public String getMostAvailableBarrelRemote(){
        return getMostAvailableBarrel();
    }


    public static void countSearch(String search) {
        searchedStrings.put(search, searchedStrings.getOrDefault(search, 0) + 1);
    }

    public static String[] getTopTenSearchs() {
        PriorityQueue<Map.Entry<String, Integer>> pq = new PriorityQueue<>((a, b) -> b.getValue().compareTo(a.getValue()));
        pq.addAll(searchedStrings.entrySet());

        return pq.stream()
                .limit(10)
                .map(entry -> {
                    // remove leading and trailing brackets and replace commas with spaces
                    String phrase = entry.getKey().replaceAll("^\\[|\\]$", "").replace(", ", " ");
                    return phrase + ": " + entry.getValue();
                })
                .toArray(String[]::new);
    }


    public String getAverageResponseTimeByBarrel() {
        StringBuilder sb = new StringBuilder();
        sb.append("Average response times:");
        barrelMetricsMap.forEach((barrel, metrics) -> {
            if (!sb.isEmpty()) {
                sb.append("\n");
            }
            sb.append(barrel).append(": ").append(String.format("%.4f", metrics.getAverageResponseTime() / 1_000_000_000.0)).append("s");
        });
        return sb.append("\n").toString(); // trailing newline
    }


    private static void setBarrelAvailability(String barrelEndpoint, double availability) {
        barrelMetricsMap.get(barrelEndpoint).setAvailability(availability);
    }


    private static String getRandomBarrel(){
        if(barrelsOnline.isEmpty()) return null;
        Collection<String> collection = barrelsOnline.values();
        int random = (int) (Math.random() * collection.size());
        return collection.toArray()[random].toString();
    }

    private static String getMostAvailableBarrel(){
        String mostAvailableBarrel = null;
        double highestAvailability = 0.0;
        for(String barrel: barrelsOnline.values()){
            if(barrelMetricsMap.get(barrel).getAvailability() >= highestAvailability){
                highestAvailability = barrelMetricsMap.get(barrel).getAvailability();
                mostAvailableBarrel = barrel;
            }
        }
        return mostAvailableBarrel;
    }


    private static String getMostAvailableBarrel(String excludedBarrel){
        String mostAvailableBarrel = null;
        double highestAvailability = 0.0;
        for(String barrel: barrelsOnline.values()){
            if(barrel.equals(excludedBarrel)) continue;
            if(barrelMetricsMap.get(barrel).getAvailability() >= highestAvailability){
                highestAvailability = barrelMetricsMap.get(barrel).getAvailability();
                mostAvailableBarrel = barrel;
            }
        }
        return mostAvailableBarrel;
    }


    private static void updateBarrelsAvailability(){
        for(String barrelEndpoint: barrelsOnline.values()) {
            IndexStorageBarrelRemote barrelRemote;
            try {
                barrelRemote = (IndexStorageBarrelRemote) Naming.lookup(barrelEndpoint);
                setBarrelAvailability(barrelEndpoint, barrelRemote.getAvailability());
            } catch (Exception e) {
                System.out.println("Error looking up barrel: " + e.getMessage());
            }
        }
    }


    private static void exportAllBarrels(){
        for(String barrelEndpoint: barrelsOnline.values()){
            exportBarrel(barrelEndpoint);
        }
    }

    private static void exportBarrel(String barrelEndpoint) {
        if (barrelEndpoint == null){
            System.out.println("Invalid barrel endpoint");
            return;
        }

        IndexStorageBarrelRemote barrelRemote;
        try {
            barrelRemote = (IndexStorageBarrelRemote) Naming.lookup(barrelEndpoint);
            barrelRemote.exportBarrel();
        } catch (Exception e) {
            System.out.println("Error looking up barrel: " + e.getMessage());
        }
    }

    @Override
    public ArrayList<String> getSystemInfo(){
        updateBarrelsAvailability();

        ArrayList<String> systemInfo = new ArrayList<>();
        systemInfo.add(getAverageResponseTimeByBarrel()); // average latency
        try{
            ArrayList<String> barrels = getRegisteredBarrels();
            StringBuilder barrelsString = new StringBuilder();
            barrelsString.append("Registered barrels: \n");
            for(String barrel: barrels){
                barrelsString.append(barrel).append("\n");
            }
            systemInfo.add(String.valueOf(barrelsString)); // registered barrels
        } catch (RemoteException ignored) {}

        systemInfo.add("Top 10 searches: \n" + String.join("\n", getTopTenSearchs())); // top 10 searches
        return systemInfo;
    }

    @Override
    public ArrayList<ArrayList<String>> searchWord(String word, int page, int pageSize, boolean isFreshSearch) throws RemoteException{
        if(isFreshSearch) countSearch(word);
        updateBarrelsAvailability();

        String bestBarrel = getMostAvailableBarrel();
        System.out.println("CHOSEN BARREL: " + bestBarrel);
        if (bestBarrel == null) return null;

        IndexStorageBarrelRemote barrel;
        try {
            barrel = (IndexStorageBarrelRemote) Naming.lookup(bestBarrel);
        } catch (Exception e) {
            System.out.println("Error looking up barrel: " + e.getMessage());
            e.printStackTrace();
            return null;
        }

        long start = System.nanoTime();
        ArrayList<ArrayList<String>> response = barrel.searchWord(word, page, pageSize);
        System.out.println("RESPONSE!!!!!!!!!!! " + response);
        long end = System.nanoTime();
        double elapsedTime = end - start;

        System.out.println("Elapsed time: " + elapsedTime / 1_000_000_000.0 + "s");

        barrelMetricsMap.get(bestBarrel).updateMetrics(elapsedTime);
        return response;
    }

    /*@Override
    public ArrayList<ArrayList<String>> searchWords(ArrayList<String> words) throws RemoteException{
        countSearch(String.valueOf(words));
        updateBarrelsAvailability();

        String randomBarrel = getMostAvailableBarrel();
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

        barrelMetricsMap.get(randomBarrel).updateMetrics(elapsedTime);
        return response;
    }*/


    @Override
    public ArrayList<ArrayList<String>> searchWordSet(ArrayList<String> words, int page, int pageSize, boolean isFreshSearch) throws RemoteException{
        if(isFreshSearch) countSearch(String.valueOf(words));
        updateBarrelsAvailability();

        String bestBarrel = getMostAvailableBarrel();
        if (bestBarrel == null) return null;

        IndexStorageBarrelRemote barrel;
        try {
            barrel = (IndexStorageBarrelRemote) Naming.lookup(bestBarrel);
        } catch (Exception e) {
            System.out.println("Error looking up barrel: " + e.getMessage());
            return null;
        }

        long start = System.nanoTime();
        ArrayList<ArrayList<String>> response = barrel.searchWordSet(words, page, pageSize);
        long end = System.nanoTime();
        double elapsedTime = end - start;

        barrelMetricsMap.get(bestBarrel).updateMetrics(elapsedTime);
        return response;
    }


    @Override
    public ArrayList<ArrayList<String>> getFatherUrls(ArrayList<String> urls) throws RemoteException {
        updateBarrelsAvailability();

        String bestBarrel = getMostAvailableBarrel();
        if (bestBarrel == null) return null;

        IndexStorageBarrelRemote barrel;
        try {
            barrel = (IndexStorageBarrelRemote) Naming.lookup(bestBarrel);
        } catch (Exception e) {
            System.out.println("Error looking up barrel: " + e.getMessage());
            return null;
        }

        return barrel.getFatherUrls(urls);
    }

    @Override
    public ArrayList<String> getFatherUrls(String url) throws RemoteException {
        updateBarrelsAvailability();

        String bestBarrel = getMostAvailableBarrel();
        if (bestBarrel == null) return null;

        IndexStorageBarrelRemote barrel;
        try {
            barrel = (IndexStorageBarrelRemote) Naming.lookup(bestBarrel);
        } catch (Exception e) {
            System.out.println("Error looking up barrel: " + e.getMessage());
            return null;
        }

        return barrel.getFatherUrls(url);
    }


    private static boolean setupGatewayRMI(){
        try {
            Gateway gateway = new Gateway();
            LocateRegistry.createRegistry(rmiPort);
            Naming.rebind(rmiEndpoint, gateway);
            System.out.println("Gateway Service bound in registry");
            return true;
        } catch (Exception e) {
            System.out.println("Gateway Service error: " + e.getMessage());
            return false;
        }
    }


    public static void main(String[] args) throws InterruptedException {
        try{
            String host = GatewayConfigLoader.getProperty("gateway.host");
            if(host == null){
                System.err.println("Gateway Host property not found in property file! Exiting...");
                System.exit(1);
            }
            String serviceName = GatewayConfigLoader.getProperty("gateway.serviceName");
            if(serviceName == null){
                System.err.println("Gateway Service Name property not found in property file! Exiting...");
                System.exit(1);
            }

            rmiEndpoint = "//"+host+"/"+serviceName;

            String port = GatewayConfigLoader.getProperty("gateway.port");
            if(port == null){
                System.err.println("Gateway Port property not found in property file! Exiting...");
                System.exit(1);
            }

            rmiPort = Integer.parseInt(port);
        } catch (GatewayConfigLoader.ConfigurationException e){
            System.err.println("Failed to load configuration file: " + e.getMessage());
            System.err.println("Exiting...");
            System.exit(1);
        }

        if(!setupGatewayRMI()) System.exit(1);

        int infoInterval = 5000;
        while(true){
            int barrelsNum = barrelsOnline.size();
            System.out.print("\033[H\033[2J");
            System.out.flush();
            System.out.print("\rBarrel - Availability - Average Latency");
            for(Map.Entry<String, BarrelMetrics> barrelEntry : barrelMetricsMap.entrySet()){
                System.out.print("\n" + barrelEntry.getKey() + ": " + barrelEntry.getValue().getAvailability() + " - " + barrelEntry.getValue().getAverageResponseTime()/1_000_000 + "ms");
            }
            System.out.println("\n");
            System.out.println("Urls in queue: " + urlsDeque.size());
            Thread.sleep(infoInterval);
        }
    }
}