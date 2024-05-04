package com.googol.backend.gateway;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.rmi.Naming;
import java.rmi.RMISecurityManager;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

import com.googol.backend.model.RawUrl;
import com.googol.backend.strategy.CrawlingStrategy;
import com.googol.backend.strategy.BFSStartegy;
import com.googol.backend.strategy.DFSStartegy;
import com.googol.backend.storage.IndexStorageBarrelRemote;


/**
 * Class to read properties from the gateway property file.
 */
class GatewayConfigLoader {
    private static final Properties properties = new Properties();

    /**
     * Configuration exception that extends runtime exception.
     */
    public static class ConfigurationException extends RuntimeException {
        /**
         * Instantiates a new Configuration exception.
         *
         * @param message the message
         * @param cause   the cause
         */
        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static{
        try {
            loadProperties();
        } catch (ConfigurationException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }


    /**
     * Load properties from the file, in case it exists.
     * If not or if there is an error reading it, throw a Configuration Exception
     */
    private static void loadProperties(){
        String filePath = "config/gateway.properties";
        try (InputStream input = new FileInputStream(filePath)){
            properties.load(input);
        } catch (IOException e){
            throw new ConfigurationException("Failed to load configuration properties " + e.getMessage(), e);
        }
    }


    /**
     * Gets property from property file.
     *
     * @param key the key
     * @return the property
     */
    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
}


/**
 * Class that keeps track of a barrel availability and average response time.
 */
class BarrelMetrics {
    /**
     * Average response time of this barrel, taking into account
     * the number of requests made to it
     */
    private double averageResponseTime = 0;
    /**
     * Number of requests made to this barrel
     */
    private long requestCount = 0;
    /**
     * Barrel availability
     * 0 - not available
     * 1 - 100% available
     */
    private double availability = 0;

    /**
     * Update barrel metrics.
     *
     * @param responseTime the response time
     */
    public void updateMetrics(double responseTime) {
        averageResponseTime += (averageResponseTime + responseTime) / ++requestCount;
    }

    /**
     * Gets barrel average response time.
     *
     * @return the average response time
     */
    public double getAverageResponseTime() {
        return averageResponseTime;
    }

    /**
     * Sets barrel availability.
     *
     * @param availability the availability
     */
    public void setAvailability(double availability) {
        this.availability = availability;
    }

    /**
     * Gets barrel availability.
     *
     * @return the availability
     */
    public double getAvailability() {
        return availability;
    }
}


/**
 * Gateway Class.
 */
public class Gateway extends UnicastRemoteObject implements GatewayRemote {
    /**
     * Verbosity variable.
     * If true, the configuration values are shown, otherwise not.
     */
    private static boolean verbosity = false;
    private static String host;
    /**
     * Port used in the Gateway RMI.
     * Defaults to 1099 if it's not on the properties file or if it's invalid.
     */
    private static int rmiPort = 1099; // default
    /**
     * Gateway RMI Endpoint that will be built using the provided
     * host and service name on the properties file
     */
    private static String rmiEndpoint;
    /**
     * Crawling max depth, used to limit the depth of the crawling, essentially preventing infinite crawling.
     * Defaults to 1 if it's not on the properties file or if it's invalid
     */
    public static int crawlingMaxDepth = 1; // default
    /**
     * Crawling strategy. Current supported strategies are DFS and BFS
     * Defaults to BFS if it's not on the properties file or if it's invalid
     */
    public static CrawlingStrategy crawlingStrategy = new BFSStartegy(); // default
    /**
     * Parsed urls variable. Atomic its access is synchronized.
     * Its purpose is to track the number of parsed urls (the Downloaders increment it when they parse an url)
     * and the Barrels get it when they need to assign a new and exclusive ID to the parsed url object
     */
    public static AtomicLong PARSED_URLS = new AtomicLong();
    /**
     * Multicast Address for the Downloaders to communicate with the Barrels.
     * Defaults to 224.3.2.1 if it's not on the properties file or if it's invalid
     */
    public static String multicastAddress = "224.3.2.1"; // default
    /**
     * Multicast Port for the Downloaders to communicate with the Barrels.
     * Defaults to 4322 if it's not on the properties file or if it's invalid
     */
    public static int multicastPort = 4322; // default
    /**
     * Parsing delimiter that will be used to separate words and info on the multicast communication between the Downloaders and Barrels.
     * This delimiter will be removed from all the text so there isn't any conflicts on the parsing
     */
    public static char parsingDelimiter = '|'; // default
    /**
     * Delay between system info display
     * Defaults to 5000 if it's not on the properties file or if it's invalid
     */
    private static int infoDelay = 5000; // default
    /**
     * Double ended blocking queue that will contain all urls (int this case, the RawUrl objects).
     * The reason for it to be a double ended queue is for the crawling strategy.
     * Depending on it, urls might need to be added at the front or the back of the queue
     * Also, it's blocking so the Downloaders get the urls in order, preventing duplicate parsing
     */
    private static final LinkedBlockingDeque<RawUrl> urlsDeque = new LinkedBlockingDeque<>();
    /**
     * Concurrent Hash Map to keep track of the online barrels.
     * Each entry's key and value is the barrel endpoint.
     */
    private static final ConcurrentHashMap<String, String> barrelsOnline = new ConcurrentHashMap<>();
    /**
     * HashMap that maps a barrel endpoint to its BarrelMetrics object.
     * Used to retrieve and get or update the provided barrels metrics.
     */
    private static final HashMap<String, BarrelMetrics> barrelMetricsMap = new HashMap<>();
    /**
     * Hashmap that maps a searched string to it's number of search counts.
     * This is so the top 10 searches can be tracked.
     */
    private static final HashMap<String, Integer> searchedStrings = new HashMap<>();


    /**
     * Instantiates a new Gateway.
     *
     * @throws RemoteException the remote exception
     */
    protected Gateway() throws RemoteException {}


    /**
     * Get RawUrl object from the front of the double ended queue.
     * @return RawUrl object, ready to be parsed
     * @throws InterruptedException Interrupted Exception
     * @throws RemoteException RMI Exception
     */
    public RawUrl getUrlFromDeque() throws InterruptedException, RemoteException {
        return urlsDeque.take();
    }


    /**
     * Add RawUrl object to the Deque, based on the crawling strategy being used
     * @param rawUrl the RawUrl object
     * @throws RemoteException RMI Exception
     */
    @Override
    public void addUrlToUrlsDeque(RawUrl rawUrl) throws RemoteException{
        crawlingStrategy.addUrl(urlsDeque, rawUrl);
    }

    /**
     * Add RawUrl object to Dequeue, based on the crawling strategy being used
     * but create the RawUrl object before, as it's only received the url string.
     * If the url is invalid, catch the exception and return false.
     * Otherwise, return true.
     * @param url the string url
     * @return true if successful, false otherwise
     * @throws RemoteException RMI Exception
     */
    @Override
    public boolean addUrlToUrlsDeque(String url) throws RemoteException{
        try {
            crawlingStrategy.addUrl(urlsDeque, new RawUrl(url));
        } catch (IllegalArgumentException e){
            return false;
        }
        return true;
    }


    /**
     * Add array list of RawUrl objects to Dequeue, based on the crawling strategy being used.
     * @param rawUrls the raw urls
     * @throws RemoteException RMI Exception
     */
    @Override
    public void addRawUrlsToUrlsDeque(ArrayList<RawUrl> rawUrls) throws RemoteException{
        for(RawUrl rawUrl: rawUrls){
            crawlingStrategy.addUrl(urlsDeque, rawUrl);
        }
    }


    /**
     * Add array list of url Strings to Dequeue, based on the crawling strategy being used,
     * creating the RawUrl objects using the provided strings.
     * If the url is invalid, append its position on the provided array list of url string to an array.
     * In the end, if the bad urls is empty, it means that there was no invalid url.
     * If it is not empty, it means there were invalid urls and that array list is returned so the Client
     * knows which urls were bad and report it to the user.
     *
     * @param rawUrls the raw urls
     * @return badUrls position in provided string array list of urls
     * @throws RemoteException RMI Exception
     */
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


    /**
     * Return the parsing delimiter via RMI.
     * @return parsing delimiter
     * @throws RemoteException RMI Exception
     */
    @Override
    public char getParsingDelimiter() throws  RemoteException{
        return parsingDelimiter;
    }


    /**
     * Return the crawling max depth via RMI.
     * @return crawling max depth
     * @throws RemoteException RMI Exception
     */
    @Override
    public int getCrawlingMaxDepth() throws  RemoteException{
        return crawlingMaxDepth;
    }


    /**
     * Return the multicast address via RMI.
     * @return multicast address
     * @throws RemoteException RMI Exception
     */
    @Override
    public String getMulticastAddress() throws  RemoteException{
        return multicastAddress;
    }


    /**
     * Return the multicast port via RMI.
     * @return multicast port
     * @throws RemoteException RMI Exception
     */
    @Override
    public int getMulticastPort() throws  RemoteException{
        return multicastPort;
    }


    /**
     * Increment parsed urls via RMI.
     * @throws RemoteException RMI Exception
     */
    @Override
    public void incrementParsedUrls() throws RemoteException {
        PARSED_URLS.incrementAndGet();
    }


    /**
     * Get parsed urls via RMI.
     * @return parsed urls
     * @throws RemoteException RMI Exception
     */
    @Override
    public long getParsedUrls() throws RemoteException {
        return PARSED_URLS.get();
    }


    /**
     * Increment and get parsed urls via RMI.
     * @return incremented parsed urls
     * @throws RemoteException RMI Exception
     */
    @Override
    public long incrementAndGetParsedUrls() throws RemoteException{
        return PARSED_URLS.incrementAndGet();
    }


    /**
     * Register a barrel in the online barrels hash map, using the provided barrel endpoint
     * as its identifier. And log the registering.
     * @param barrelEndpoint the barrel endpoint
     * @throws RemoteException EMI Exception
     */
    @Override
    public void registerBarrel(String barrelEndpoint) throws RemoteException {
        barrelsOnline.put(barrelEndpoint, barrelEndpoint);
        barrelMetricsMap.put(barrelEndpoint, new BarrelMetrics());
        System.out.println("\nBarrel registered: " + barrelEndpoint);
    }


    /**
     * Unregister a barrel from the online barrels hash map, using the provided barrel endpoint.
     * And log the unregistering.
     * @param barrelEndpoint the barrel endpoint
     * @throws RemoteException RMI Exception
     */
    @Override
    public void unregisterBarrel(String barrelEndpoint) throws RemoteException {
        barrelsOnline.remove(barrelEndpoint);
        barrelMetricsMap.remove(barrelEndpoint);
        System.out.println("\nBarrel unregistered: " + barrelEndpoint);
    }


    /**
     * Get all registered barrels.
     * @return list of registered barrels
     * @throws RemoteException RMI Exception
     */
    @Override
    public ArrayList<String> getRegisteredBarrels() throws RemoteException {
        return new ArrayList<>(barrelsOnline.values());
    }


    /**
     * Get registered barrels count
     * @return registered barrels count
     * @throws RemoteException RMI Exception
     */
    @Override
    public int getRegisteredBarrelsCount() throws RemoteException{
        return barrelsOnline.size();
    }


    /**
     * Get random barrel from online barrels.
     * @return random barrel
     * @throws RemoteException RMI Exception
     */
    @Override
    public String getRandomBarrelRemote() throws RemoteException {
        return getRandomBarrel();
    }


    /**
     * Get the most available registered barrel.
     * @param callingBarrel the calling barrel (as to not return itself)
     * @return most available barrel
     */
    @Override
    public String getMostAvailableBarrelRemote(String callingBarrel){
        return getMostAvailableBarrel(callingBarrel);
    }


    /**
     * Get the most available registered barrel.
     * @return most available barrel
     */
    @Override
    public String getMostAvailableBarrelRemote(){
        return getMostAvailableBarrel();
    }


    /**
     * Count search.
     * @param search the search
     */
    public static void countSearch(String search) {
        searchedStrings.put(search, searchedStrings.getOrDefault(search, 0) + 1);
    }

    /**
     * Get top ten searches.
     * The existing searches are sorted in a priority queue and the top 10 are returned
     *
     * @return array string with the top 10 searches (or less if there are less than 10 distinct searches)
     */
    public static String[] getTopTenSearchs() {
        // create a priority queue that orders its elements according to their frequency, in descending order.
        // 'a' and 'b' are entries of the map where each entry consists of a String (barrel identifier) and an Integer (search count).
        // the comparison is made on the search counts
        PriorityQueue<Map.Entry<String, Integer>> pq = new PriorityQueue<>((a, b) -> b.getValue().compareTo(a.getValue()));

        // add all entries of the map to the priority queue
        pq.addAll(searchedStrings.entrySet());

        // return the top 10 most searched words
        return pq.stream()
                .limit(10) // take only the top 10 entries
                .map(entry -> {
                    // remove leading and trailing brackets and replace commas with spaces because the keys have brackets and commas
                    // TODO fix the root cause of this (the searched strings should be treated before being inserted in the map)
                    String phrase = entry.getKey().replaceAll("^\\[|\\]$", "").replace(", ", " ");
                    return phrase + ": " + entry.getValue(); // return the search and its count
                })
                .toArray(String[]::new); // collect the transformed stream elements into an array of strings
    }


    /**
     * Gets average response time by barrel.
     * The average response time is calculated by dividing the total response time by the number of requests
     * @return the average response time by barrel
     */
    public String getAverageResponseTimeByBarrel() {
        StringBuilder sb = new StringBuilder();
        sb.append("Average response times:");

        // build string with all barrels and their average response time
        barrelMetricsMap.forEach((barrel, metrics) -> {
            if (!sb.isEmpty()) { // if string builder is not empty, add a new line (this check is done in order to prevent a newline in the beginning of the string)
                sb.append("\n");
            }
            // append the barrel and its average response time (barrel name: average response time)
            sb.append(barrel).append(": ").append(String.format("%.4f", metrics.getAverageResponseTime() / 1_000_000_000.0)).append("s");
        });
        return sb.append("\n").toString(); // return the built string with a trailing newline
    }


    /**
     * Set availability of provided barrel
     * @param barrelEndpoint the barrel to set the availability
     * @param availability availability
     */
    private static void setBarrelAvailability(String barrelEndpoint, double availability) {
        // get barrel from barrel metrics map and set its availability
        barrelMetricsMap.get(barrelEndpoint).setAvailability(availability);
    }


    /**
     * Get random barrel
     * @return random barrel rmi endpoint if barrels online else null
     */
    private static String getRandomBarrel(){
        if(barrelsOnline.isEmpty()) return null; // if there are no barrels online, return null
        Collection<String> collection = barrelsOnline.values(); // get the collection of online barrels
        int random = (int) (Math.random() * collection.size()); // get a random index
        return collection.toArray()[random].toString(); // return the random barrel as string
    }


    /**
     * Get the most available barrel (barrel with the highest availability)
     * @return most available barrel rmi endpoint if barrels online else null
     */
    private static String getMostAvailableBarrel(){
        String mostAvailableBarrel = null;
        double highestAvailability = 0.0;
        for(String barrel: barrelsOnline.values()){ // for each online barrel
            if(barrelMetricsMap.get(barrel).getAvailability() >= highestAvailability){ // if barrel availability bigger than the highest availability up until now
                highestAvailability = barrelMetricsMap.get(barrel).getAvailability(); // update the highest availability
                mostAvailableBarrel = barrel; // update most available barrel
            }
        }
        return mostAvailableBarrel; // return the most available barrel if found, otherwise null
    }


    /**
     * Get the most available barrel (barrel with the highest availability) excluding the provided barrel
     * @param excludedBarrel barrel to exclude
     * @return most available barrel rmi endpoint if more barrels online than only the excluded barrel, else null
     */
    private static String getMostAvailableBarrel(String excludedBarrel){
        String mostAvailableBarrel = null;
        double highestAvailability = 0.0;
        for(String barrel: barrelsOnline.values()){ // for each online barrel
            if(barrel.equals(excludedBarrel)) continue; // if barrel is the same as the excluded barrel, skip it
            if(barrelMetricsMap.get(barrel).getAvailability() >= highestAvailability){ // if barrel availability bigger than the highest availability up until now
                highestAvailability = barrelMetricsMap.get(barrel).getAvailability(); // update the highest availability
                mostAvailableBarrel = barrel; // update most available barrel
            }
        }
        return mostAvailableBarrel; // return the most available barrel if found, otherwise null
    }


    /**
     * Update the availability of all the online barrels by going over each online barrel
     * and connecting to its RMI and pulling its availability
     */
    private static void updateBarrelsAvailability(){
        for(String barrelEndpoint: barrelsOnline.values()) { // for each online barrel
            IndexStorageBarrelRemote barrelRemote; // create a new barrel remote interface
            try {
                barrelRemote = (IndexStorageBarrelRemote) Naming.lookup(barrelEndpoint); // lookup barrel
                setBarrelAvailability(barrelEndpoint, barrelRemote.getAvailability()); // set the barrel availability, providing the current barrel rmi endpoint and its availability (pulled using RMI)
            } catch (Exception e) {
                System.out.println("Error looking up barrel: " + e.getMessage());
            }
        }
    }

/*
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
*/

    /**
     * Get system info. Namely, each registered barrel, and it's availability and average response time,
     * and the top 10 searches
     * @return array list containing the info
     */
    @Override
    public ArrayList<String> getSystemInfo(){
        updateBarrelsAvailability(); // update all registered barrels availability

        ArrayList<String> systemInfo = new ArrayList<>();
        systemInfo.add(getAverageResponseTimeByBarrel()); // add all barrels average latency
        try{
            ArrayList<String> barrels = getRegisteredBarrels(); // get all registered barrels
            StringBuilder barrelsString = new StringBuilder();
            barrelsString.append("Registered barrels: \n");
            for(String barrel: barrels){ // for each registered barrel
                barrelsString.append(barrel).append("\n"); // append it to the string
            }
            systemInfo.add(String.valueOf(barrelsString)); // add registered barrels
        } catch (RemoteException ignored) {}

        systemInfo.add("Top 10 searches: \n" + String.join("\n", getTopTenSearchs())); // top 10 searches
        return systemInfo;
    }


    /**
     * Search a single word in the most available barrel
     * @param word          the word to search
     * @param page          the page number
     * @param pageSize      the page size
     * @param isFreshSearch is this a fresh search (to prevent keep counting the search if the client is only changing the pages)
     * @return array list that contains arrays that contain the search results -> (url - title - description)
     * @throws RemoteException RMI Exception
     */
    @Override
    public ArrayList<ArrayList<String>> searchWord(String word, int page, int pageSize, boolean isFreshSearch) throws RemoteException{
        if(isFreshSearch) countSearch(word); // if the call is from a search and not from a page change, count it as a search
        updateBarrelsAvailability(); // update all registered barrels availability

        String bestBarrel = getMostAvailableBarrel(); // get most available barrel
        if(verbosity) System.out.println("CHOSEN BARREL: " + bestBarrel);
        if (bestBarrel == null) return null; // if no barrel is available, return null

        IndexStorageBarrelRemote barrel;
        try {
            barrel = (IndexStorageBarrelRemote) Naming.lookup(bestBarrel); // lookup most available barrel
        } catch (Exception e) {
            System.out.println("Error looking up barrel: " + e.getMessage());
            return null; // return null if lookup fails
        }

        /* count elapsed time */
        long start = System.nanoTime();
        ArrayList<ArrayList<String>> response = barrel.searchWord(word, page, pageSize);
        long end = System.nanoTime();
        double elapsedTime = end - start;

        if(verbosity) System.out.println("Elapsed time: " + elapsedTime / 1_000_000_000.0 + "s");

        barrelMetricsMap.get(bestBarrel).updateMetrics(elapsedTime); // update barrel average time response
        return response;
    }


    /**
     * Search a set of words in the most available barrel
     * @param words         the words to search
     * @param page          the page number
     * @param pageSize      the page size
     * @param isFreshSearch is this a fresh search (to prevent keep counting the search if the client is only changing the pages)
     * @return array list that contains arrays that contain the search results -> (url - title - description), or null if error
     * @throws RemoteException RMI Exception
     */
    @Override
    public ArrayList<ArrayList<String>> searchWordSet(ArrayList<String> words, int page, int pageSize, boolean isFreshSearch) throws RemoteException{
        if(isFreshSearch) countSearch(String.valueOf(words)); // if the call is from a search and not from a page change, count it as a search
        updateBarrelsAvailability(); // update all registered barrels availability

        String bestBarrel = getMostAvailableBarrel(); // get most available barrel
        if(verbosity) System.out.println("CHOSEN BARREL: " + bestBarrel);
        if (bestBarrel == null) return null; // if no barrel is available, return null

        IndexStorageBarrelRemote barrel;
        try {
            barrel = (IndexStorageBarrelRemote) Naming.lookup(bestBarrel); // lookup most available barrel
        } catch (Exception e) {
            System.out.println("Error looking up barrel: " + e.getMessage());
            return null; // return null if lookup fails
        }

        /* count elapsed time */
        long start = System.nanoTime();
        ArrayList<ArrayList<String>> response = barrel.searchWordSet(words, page, pageSize);
        long end = System.nanoTime();
        double elapsedTime = end - start;

        barrelMetricsMap.get(bestBarrel).updateMetrics(elapsedTime); // update barrel average time response
        return response;
    }


    /**
     * Get father urls of the provided urls list from the most available barrel
     * @param urls the urls
     * @return array list that contains arrays that contains all the father urls of the urls (i.e. [[fatherurl1, fatherurl2], [fatherurl24, fatherurl4], ...])
     * @throws RemoteException RMI Exception
     */
    @Override
    public ArrayList<ArrayList<String>> getFatherUrls(ArrayList<String> urls) throws RemoteException {
        updateBarrelsAvailability(); // update all registered barrels availability

        String bestBarrel = getMostAvailableBarrel(); // get most available barrel
        if(verbosity) System.out.println("CHOSEN BARREL: " + bestBarrel);
        if (bestBarrel == null) return null; // if no barrel is available, return null

        IndexStorageBarrelRemote barrel;
        try {
            barrel = (IndexStorageBarrelRemote) Naming.lookup(bestBarrel); // lookup most available barrel
        } catch (Exception e) {
            System.out.println("Error looking up barrel: " + e.getMessage());
            return null; // return null if lookup fails
        }

        return barrel.getFatherUrls(urls); // return the result of the getFatherUrls call
    }


    /**
     * Get father urls of the provided url from the most available barrel
     * @param url the url
     * @return array list that contains the father urls of the url (i.e. [fatherurl1, fatherurl2, ...])
     * @throws RemoteException RMI Exception
     */
    @Override
    public ArrayList<String> getFatherUrls(String url) throws RemoteException {
        updateBarrelsAvailability(); // update all registered barrels availability

        String bestBarrel = getMostAvailableBarrel(); // get most available barrel
        if(verbosity) System.out.println("CHOSEN BARREL: " + bestBarrel);
        if (bestBarrel == null) return null; // if no barrel is available, return null

        IndexStorageBarrelRemote barrel;
        try {
            barrel = (IndexStorageBarrelRemote) Naming.lookup(bestBarrel); // lookup most available barrel
        } catch (Exception e) {
            System.out.println("Error looking up barrel: " + e.getMessage());
            return null; // return null if lookup fails
        }

        return barrel.getFatherUrls(url); // return the result of the getFatherUrls call
    }


    /**
     * Setup gateway RMI
     * @return true if successful, false otherwise
     */
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


    /**
     * Helper function to validate if a multicast address is valid
     * @param address the address to be validated
     * @return true if valid, false otherwise
     */
    private static boolean isValidMulticastAddress(String address) {
        try {
            InetAddress inetAddress = InetAddress.getByName(address);
            return inetAddress.isMulticastAddress();
        } catch (Exception e) {
            System.out.println("Invalid address: " + e.getMessage());
            return false;
        }
    }


    /**
     * Helper function to validate if a port is valid
     *
     * @param port the port to be validated
     * @return true if valid, false otherwise
     */
    public static boolean isValidPort(int port) {
        // ports bellow 1024 are not recommended, so they are not considered valid
        return port > 1024 && port <= 65535;
    }


    /**
     * Function that loads all the properties from the properties file
     */
    private static void loadConfig(){
        try{
            // load gateway host
            String verbosityConfig = GatewayConfigLoader.getProperty("gateway.verbosity");
            if(verbosityConfig == null){
                System.err.println("Verbosity not found in property file! Defaulting to " + verbosity + "...");
            } else {
                try{
                    verbosity = Integer.parseInt(verbosityConfig) == 1;
                    System.out.println("Verbosity: " + verbosity);
                } catch (NumberFormatException e){
                    System.err.println("Verbosity is not a number! Defaulting to " + verbosity + "...");
                }
            }

            if(verbosity) System.out.println("----------CONFIG----------");

            // load gateway host
            String hostConfig = GatewayConfigLoader.getProperty("gateway.host");
            if(hostConfig == null){
                System.err.println("Gateway Host property not found in property file! Exiting...");
                System.exit(1);
            }
            host = hostConfig;
            if(verbosity) System.out.println("Host: " + host);

            // load gateway rmi service name
            String serviceName = GatewayConfigLoader.getProperty("gateway.serviceName");
            if(serviceName == null){
                System.err.println("Gateway Service Name property not found in property file! Exiting...");
                System.exit(1);
            }
            if(verbosity) System.out.println("Service Name: " + serviceName);

            // build rmi endpoint
            rmiEndpoint = "//"+host+"/"+serviceName;
            if(verbosity) System.out.println("RMI Enpoint: " + rmiEndpoint);

            // load rmi port
            String rmiPortConfig = GatewayConfigLoader.getProperty("gateway.rmiPort");
            if(rmiPortConfig == null){
                System.out.println("Gateway RMI Port property not found in property file! Defaulting to " + rmiPort + "...");
            } else {
                try{
                    rmiPort = Integer.parseInt(rmiPortConfig);
                    if(verbosity) System.out.println("RMI Port: " + rmiPort);
                } catch (NumberFormatException e){
                    System.out.println("Gateway RMI Port property must be a number! Defaulting to " + rmiPort + "...");
                }
            }

            // load multicast address
            String multicastAddressConfig = GatewayConfigLoader.getProperty("gateway.multicastAddress");
            if(multicastAddressConfig == null){
                System.out.println("Gateway Multicast Address property not found in property file! Defaulting to "+ multicastAddress + "...");
            } else if(!isValidMulticastAddress(multicastAddressConfig)){
                System.out.println("Gateway Multicast Address is not valid! Defaulting to "+ multicastAddress + "...");
            } else {
                multicastAddress = multicastAddressConfig;
                if(verbosity) System.out.println("Multicast Address: " + multicastAddress);
            }

            // load multicast port
            String multicastPortConfig = GatewayConfigLoader.getProperty("gateway.multicastPort");
            if(multicastPortConfig == null){
                System.out.println("Gateway Multicast Port property not found in property file! Defaulting to "+ multicastPort + "...");
            } else{
                try {
                    int multicastPortInt = Integer.parseInt(multicastPortConfig);
                    if(!isValidPort(multicastPortInt)){
                        System.out.println("Gateway Multicast Address is not valid! Defaulting to "+ multicastPort + "...");
                    } else {
                        multicastPort = multicastPortInt;
                        if(verbosity) System.out.println("Multicast Port: " + multicastPort);
                    }
                } catch (NumberFormatException ignored){
                    System.out.println("Gateway Multicast Port is not valid! Defaulting to " + multicastPort + "...");
                }
            }

            // load parsing delimiter
            String parsingDelimiterConfig = GatewayConfigLoader.getProperty("gateway.parsingDelimiter");
            if(parsingDelimiterConfig == null){
                System.out.println("Parsing Delimiter property not found in property file! Defaulting to "+ parsingDelimiter + "...");
            } else {
                if (parsingDelimiterConfig.length() == 1) {
                    parsingDelimiter = parsingDelimiterConfig.charAt(0);
                    if(verbosity) System.out.println("Parsing Delimiter: " + parsingDelimiter);
                } else {
                    System.out.println("Parsing Delimiter in the property file is not a single character. Defaulting to " + parsingDelimiter + "...");
                }
            }

            // load crawling max depth
            String crawlingMaxDepthConfig = GatewayConfigLoader.getProperty("gateway.crawlingMaxDepth");
            if(crawlingMaxDepthConfig == null){
                System.out.println("Crawler Max Depth property not found in property file! Defaulting to "+ crawlingMaxDepth + "...");
            } else {
                try{
                    int crawlingMaxDepthInt = Integer.parseInt(crawlingMaxDepthConfig);
                    if (crawlingMaxDepthInt >= 0) {
                        crawlingMaxDepth = crawlingMaxDepthInt;
                        if(verbosity) System.out.println("Crawling Max Depth: " + crawlingMaxDepth);
                    } else {
                        System.out.println("Crawling Max Depth must be greater or equal to 0. Defaulting to " + crawlingMaxDepth + "...");
                    }
                } catch (NumberFormatException ignored){
                    System.err.println("Crawling Max Depth must be a number! Defaulting to " + crawlingMaxDepth + "...");
                }
            }

            // load crawling strategy
            String crawlingStrategyConfig = GatewayConfigLoader.getProperty("gateway.crawlingMaxDepth");
            if(crawlingMaxDepthConfig == null){
                System.out.println("Crawler Strategy property not found in property file! Defaulting to "+ crawlingStrategy.getStrategyName() + "...");
            } else {
                switch(crawlingStrategyConfig){
                    case "dfs":
                        crawlingStrategy = new DFSStartegy();
                        if(verbosity) System.out.println("Crawling Strategy: " + crawlingStrategy.getStrategyName());
                        break;
                    case "bfs":
                        // do nothing as the strategy is already init to BFS as default
                        if(verbosity) System.out.println("Crawling Strategy: " + crawlingStrategy.getStrategyName());
                        break;
                    default:
                        System.out.println("Crawler Strategy property not supported or recognized! Defaulting to "+ crawlingStrategy.getStrategyName() + "...");
                        break;
                }
            }

            // load info delay
            String infoDelayConfig = GatewayConfigLoader.getProperty("gateway.infoDelay");
            if(infoDelayConfig == null){
                System.out.println("Info Delay property not found in property file! Defaulting to "+ infoDelay + "...");
            } else {
                try{
                    int infoDelayInt = Integer.parseInt(infoDelayConfig);
                    if (infoDelayInt >= 500) { // prevent busy waiting
                        infoDelay = infoDelayInt;
                        if(verbosity) System.out.println("Info Delay: " + infoDelay);
                    } else {
                        System.out.println("Info Delay must be greater or equal to 500. Defaulting to " + infoDelay + "...");
                    }
                } catch (NumberFormatException ignored){
                    System.err.println("Info Delay must be a number! Defaulting to " + infoDelay + "...");
                }
            }
        } catch (GatewayConfigLoader.ConfigurationException e){
            System.err.println("Failed to load configuration file: " + e.getMessage());
            System.err.println("Exiting...");
            if(verbosity) System.out.println("-------------------------\n\n");
            System.exit(1);
        }

        if(verbosity) System.out.println("-------------------------\n\n");
    }


    /**
     * Entry point of the Gateway.
     *
     * @param args args
     * @throws InterruptedException Interrupted exception
     */
    public static void main(String[] args) throws InterruptedException {
        loadConfig(); // load properties from properties file

        // set security policies for RMI
        System.getProperties().put("java.security.policy", "server.policy");
        System.getProperties().put("java.rmi.server.hostname", host);
        System.out.println(System.getProperties().toString());
        System.setSecurityManager(new RMISecurityManager());

        if(!setupGatewayRMI()) System.exit(1); // setup gateway RMI, exit if failed

        // info loop
        while(true){
            Thread.sleep(infoDelay);

            // clear screen (might not work for all environments)
            System.out.print("\033[H\033[2J");
            System.out.flush();

            // print info
            System.out.print("\rBarrel - Availability - Average Latency");
            for(Map.Entry<String, BarrelMetrics> barrelEntry : barrelMetricsMap.entrySet()){
                System.out.print("\n" + barrelEntry.getKey() + ": " + barrelEntry.getValue().getAvailability() + " - " + barrelEntry.getValue().getAverageResponseTime()/1_000_000 + "ms");
            }
            System.out.println("\n");
            System.out.println("Urls in queue: " + urlsDeque.size());
        }
    }
}