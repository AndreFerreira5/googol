import java.io.*;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.ConnectException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;


class BarrelConfigLoader {
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
        String filePath = "config/barrel.properties";
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



public class IndexStorageBarrel extends UnicastRemoteObject implements IndexStorageBarrelRemote{
    private static boolean verbosity = false; // default
    private static AdaptiveRadixTree art = new AdaptiveRadixTree();
    public static final UUID uuid = UUID.randomUUID();
    private static String barrelRMIEndpoint;
    private static MulticastSocket socket;
    private static String multicastAddress;
    private static int helperThreadsNum = 16; // default 16 threads
    private static int port;
    protected static int maxRetries = 5; // default 5 retries
    private static int retryDelay = 1000; // default 1 second
    private static int exportationDelay = 60000; // default 60 seconds
    protected static char DELIMITER;
    protected static GatewayRemote gatewayRemote;
    private static String gatewayEndpoint;
    private static final ExecutorService fixedThreadPool = Executors.newFixedThreadPool(helperThreadsNum);
    private static final ThreadPoolExecutor fixedThreadPoolExecutor = (ThreadPoolExecutor) fixedThreadPool;
    protected static final AtomicInteger waitingThreadsNum = new AtomicInteger(0);
    protected static BlockingQueue<String> multicastMessagesQueue = new LinkedBlockingQueue<>();
    protected static ConcurrentHashMap<ParsedUrlIdPair, ParsedUrl> parsedUrlsMap = new ConcurrentHashMap<>();
    protected static ConcurrentHashMap<String, ParsedUrlIdPair> urlToUrlKeyPairMap = new ConcurrentHashMap<>();
    protected static ConcurrentHashMap<Long, ParsedUrlIdPair> idToUrlKeyPairMap = new ConcurrentHashMap<>();

    protected IndexStorageBarrel() throws RemoteException {}


    private static String getMostAvailableBarrelFromGateway(){
        String bestBarrel = "";
        boolean got = false;
        for (int i = 0; i < IndexStorageBarrel.maxRetries; i++) {
            try {
                bestBarrel = gatewayRemote.getMostAvailableBarrelRemote(barrelRMIEndpoint);
                got = true;
                break;
            } catch( ConnectException e){
                reconnectToGatewayRMI();
                i--;
            } catch (RemoteException ignored){}
        }
        if(!got){
            log("Couldn't get most available barrel from gateway!");
            return null;
        }

        return bestBarrel;
    }


    private static boolean syncBarrel(){
        String referenceBarrel = "";

        referenceBarrel = getMostAvailableBarrelFromGateway();
        System.out.println(referenceBarrel);
        if(referenceBarrel == null || referenceBarrel.isEmpty() || referenceBarrel.equals(barrelRMIEndpoint)) return false;

        IndexStorageBarrelRemote barrel = connectToBarrelRMI(referenceBarrel);
        if (barrel == null) return false;

        log("Syncing Barrel with " + referenceBarrel);

        boolean synced = false;
        for (int i = 0; i < IndexStorageBarrel.maxRetries; i++) {
            try {
                // get the new art and maps
                byte[] barrelARTFile = barrel.getArt();
                ConcurrentHashMap<ParsedUrlIdPair, ParsedUrl> barrelParsedUrlsMap = barrel.getParsedUrlsMap();
                ConcurrentHashMap<String, ParsedUrlIdPair> barrelUrlToUrlKeyPairMap = barrel.getUrlToUrlKeyPairMap();
                ConcurrentHashMap<Long, ParsedUrlIdPair> barrelIdToUrlKeyPairMap = barrel.getIdToUrlKeyPairMap();

                // assign the new art and maps
                art.importART(barrelARTFile);
                parsedUrlsMap = barrelParsedUrlsMap;
                urlToUrlKeyPairMap = barrelUrlToUrlKeyPairMap;
                idToUrlKeyPairMap = barrelIdToUrlKeyPairMap;

                synced = true;
                break;
            } catch (RemoteException e){
                log(e.getMessage());
            } catch (IOException e){
                log(e.getMessage());
                break;
            }
        }
        if(!synced){
            log("Error syncing Barrel!.");
            return false;
        }

        log("Barrel synced successfully!");
        return true;
    }


    private static String getMulticastMessage(){
        byte[] dataBuffer = new byte[65507];
        DatagramPacket packet = new DatagramPacket(dataBuffer, dataBuffer.length);
        try{
            socket.receive(packet);
        } catch (SocketException e) {
            if (socket.isClosed()) {
                log("Multicast socket is closed. " + e.getMessage());
                throw new RuntimeException("Socket closed while receiving data.", e);
            } else {
                log("SocketException occurred, but socket is not closed. " + e.getMessage());
            }
        } catch (IOException e){
            log("Error receiving multicast message. " + e.getMessage());
            syncBarrel();
            return null;
        }

        return new String(packet.getData(), 0, packet.getLength());
    }


    private static MulticastSocket setupMulticastConn(){
        MulticastSocket socket = null;
        boolean isConnected = false;

        while (!isConnected) {
            try {
                socket = new MulticastSocket(port);
                InetAddress group = InetAddress.getByName(multicastAddress);
                socket.joinGroup(group);
                isConnected = true;

            } catch (IOException e) {
                log("Error connecting to multicast group. Retrying in "+ retryDelay +"s...");

                try {
                    Thread.sleep(retryDelay); // wait before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log("Interrupted during retry wait! Interrupting...");
                    return null;
                }
            }
        }

        return socket;
    }


    @Override
    public void exportBarrel(){
        exportDeserializedInfo();
    }


    private static void exportART(AdaptiveRadixTree art){
        try{
            art.exportART();
        } catch(FileNotFoundException e){
            System.out.println("TREE FILE NOT FOUND! Stopping the exportation...");
        } catch(IOException e) {
            System.out.println("ERROR OPENING FILE: " + e + "\nStopping the exportation...");
        }
    }


    private static boolean importART(){
        try{
            art.importART();
            return true;
        } catch(FileNotFoundException e){
            System.out.println("TREE FILE NOT FOUND! Skipping the importation...");
            return false;
        } catch(IOException e) {
            System.out.println("ERROR OPENING FILE: " + e + "\nSkipping the importation...");
            return false;
        }
    }


    @Override
    public byte[] getArt(){
        try {
            art.exportART();
        } catch (IOException e) {
            return null;
        }

        try{
            return Files.readAllBytes(Paths.get(art.getFilename()));
        } catch (Exception e){
            return null;
        }
    }

    @Override
    public ConcurrentHashMap<ParsedUrlIdPair, ParsedUrl> getParsedUrlsMap(){
        System.out.println(parsedUrlsMap.size());
        return parsedUrlsMap;
    }

    @Override
    public ConcurrentHashMap<String, ParsedUrlIdPair> getUrlToUrlKeyPairMap(){
        System.out.println(urlToUrlKeyPairMap.size());
        return urlToUrlKeyPairMap;
    }

    @Override
    public ConcurrentHashMap<Long, ParsedUrlIdPair> getIdToUrlKeyPairMap(){
        System.out.println(idToUrlKeyPairMap.size());
        return idToUrlKeyPairMap;
    }


    /* Import the ART and the Maps that store the info about the urls
    *  If any of these fails to import, return (clearing the successfully imported ones)
    *  so the barrel works as intended without residual information */
    private static boolean importSerializedInfo(){
        log("Importing ART...");
        if(!importART()){
            return false;
        }

        log("Importing Parsed Urls Hash Map...");
        parsedUrlsMap = (ConcurrentHashMap<ParsedUrlIdPair, ParsedUrl>) deserializeMap("parsedUrlsMap.ser");
        if (parsedUrlsMap == null){
            art.clear();
            parsedUrlsMap = new ConcurrentHashMap<>();
            return false;
        }

        log("Importing Urls to Url Key Pairs Hash Map...");
        urlToUrlKeyPairMap = (ConcurrentHashMap<String, ParsedUrlIdPair>) deserializeMap("urlToUrlKeyPairMap.ser");
        if(urlToUrlKeyPairMap == null){
            art.clear();
            parsedUrlsMap = new ConcurrentHashMap<>();
            urlToUrlKeyPairMap = new ConcurrentHashMap<>();
            return false;
        }

        log("Importing IDs to Url Key Pairs Hash Map...");
        idToUrlKeyPairMap = (ConcurrentHashMap<Long, ParsedUrlIdPair>) deserializeMap("idToUrlKeyPairMap.ser");
        if(idToUrlKeyPairMap == null){
            art.clear();
            parsedUrlsMap = new ConcurrentHashMap<>();
            urlToUrlKeyPairMap = new ConcurrentHashMap<>();
            idToUrlKeyPairMap= new ConcurrentHashMap<>();
            return false;
        }

        return true;
    }



    private static void exportDeserializedInfo(){
        log("Exporting Parsed Urls Hash Map...");
        serializeMap(parsedUrlsMap, "parsedUrlsMap.ser");
        log("Exporting Urls to Url Key Pairs Hash Map...");
        serializeMap(urlToUrlKeyPairMap, "urlToUrlKeyPairMap.ser");
        log("Exporting IDs to Url Key Pairs Hash Map...");
        serializeMap(idToUrlKeyPairMap, "idToUrlKeyPairMap.ser");
        exportART(art);
    }


    private static GatewayRemote connectToGatewayRMI(){
        try {
            System.out.println(gatewayEndpoint);
            GatewayRemote gateway = (GatewayRemote) Naming.lookup(gatewayEndpoint);
            DELIMITER = gateway.getParsingDelimiter();
            multicastAddress = gateway.getMulticastAddress();
            port = gateway.getMulticastPort();
            return gateway;
        } catch (Exception e) {
            System.out.println("GatewayClient exception: " + e.getMessage());
            return null;
        }
    }


    private static void reconnectToGatewayRMI(){
        log("Reconnecting to gateway...");
        gatewayRemote = null;
        while(gatewayRemote == null){
            gatewayRemote = connectToGatewayRMI();
            try {
                Thread.sleep(retryDelay);
            } catch (InterruptedException ignored) {}
        }
        log("Reconnected!");

        // register barrel again on the gateway
        registerBarrel();
        setupRMI();
    }


    private static IndexStorageBarrelRemote connectToBarrelRMI(String barrelEndpoint){
        try {
            return (IndexStorageBarrelRemote) Naming.lookup(barrelEndpoint);
        } catch (Exception e) {
            System.out.println("Barrel exception: " + e.getMessage());
            return null;
        }
    }


    private static void registerBarrel(){
        // register barrel in gateway
        boolean registered = false;
        for (int i = 0; i < IndexStorageBarrel.maxRetries; i++) {
            try {
                gatewayRemote.registerBarrel(barrelRMIEndpoint);
                //rmiPort = gatewayRemote.getPort();
                registered = true;
                break;
            } catch( ConnectException e){
                reconnectToGatewayRMI();
                i--;
            } catch (RemoteException ignored){}
        }
        if (!registered){
            log("Error registering barrel in Gateway! (" + maxRetries + " retries failed) Exiting...");
            System.exit(1);
        }
        log("Successfully registered barrel in Gateway! RMI Endpoint: " + barrelRMIEndpoint);
    }


    private static boolean setupRMI() {
        try {
            IndexStorageBarrel barrel = new IndexStorageBarrel();
            Naming.rebind(barrelRMIEndpoint, barrel);
            System.out.println("Barrel Service bound in registry with endpoint: " + barrelRMIEndpoint);
            return true;
        } catch (Exception e) {
            System.out.println("Barrel Service error: " + e.getMessage());
            return false;
        }
    }



    private static void exit(){
        // unregister barrel in gateway
        boolean unregistered = false;
        for (int i = 0; i < IndexStorageBarrel.maxRetries; i++) {
            try {
                gatewayRemote.unregisterBarrel(barrelRMIEndpoint);
                unregistered = true;
                break;
            } catch( ConnectException e){
                reconnectToGatewayRMI();
                i--;
            } catch (RemoteException ignored){}
        }
        if(!unregistered) log("Error unregistering barrel in Gateway! (" + maxRetries + " retries failed) Exiting...");

        multicastMessagesQueue = null;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        fixedThreadPool.shutdownNow();
        try {
            if (!fixedThreadPool.awaitTermination(500, TimeUnit.MICROSECONDS)) {
                fixedThreadPool.shutdownNow();
            }
        } catch (InterruptedException ie) {
            fixedThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.exit(1);
    }


    @Override
    public double getAvailability() {
        int totalThreads = fixedThreadPoolExecutor.getCorePoolSize();
        int busyThreads = fixedThreadPoolExecutor.getActiveCount();
        int availableThreads = totalThreads - busyThreads + waitingThreadsNum.get();
        return (double) availableThreads / totalThreads;
    }


    private static void periodicBarrelExportation(){
        while(!Thread.currentThread().isInterrupted()){
            try {
                Thread.sleep(exportationDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            log("Periodic barrel exportation starting...");
            exportDeserializedInfo();
        }
    }


    private static void loadConfig(){
        try{
            // load verbosity
            String verbosityConfig = BarrelConfigLoader.getProperty("barrel.verbosity");
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


            // load gateway rmi host
            String gatewayHost = BarrelConfigLoader.getProperty("gateway.host");
            if(gatewayHost == null){
                System.err.println("Gateway Host property not found in property file! Exiting...");
                System.exit(1);
            }
            if(verbosity) System.out.println("Gateway Host: " + gatewayHost);

                // load gateway rmi service name
            String gatewayServiceName = BarrelConfigLoader.getProperty("gateway.serviceName");
            if(gatewayServiceName == null){
                System.err.println("Gateway Service Name property not found in property file! Exiting...");
                System.exit(1);
            }
            if(verbosity) System.out.println("Gateway Service Name: " + gatewayServiceName);

            // build gateway rmi endpoint
            gatewayEndpoint = "//"+gatewayHost+"/"+gatewayServiceName;
            if(verbosity) System.out.println("Gateway Endpoint: " + gatewayEndpoint);

            // load barrel host
            String barrelHost = BarrelConfigLoader.getProperty("barrel.host");
            if(barrelHost == null){
                System.err.println("Barrel Host property not found in property file! Exiting...");
                System.exit(1);
            }
            if(verbosity) System.out.println("Barrel Host: " + barrelHost);

            // build barrel rmi endpoint with its uuid
            barrelRMIEndpoint = "//"+barrelHost+"/IndexStorageBarrel-"+uuid.toString();
            if(verbosity) System.out.println("Barrel RMI Endpoint: " + barrelRMIEndpoint);

            // load helper threads num
            String helperThreads = BarrelConfigLoader.getProperty("barrel.helperThreads");
            if(helperThreads == null){ // if not found, set to default (defined on top of the class)
                System.out.println("Barrel Helper Threads property not found in property file! Defaulting to " + helperThreadsNum +"...");
            } else { // if found, check it
                try{
                    int helperThreadsInt = Integer.parseInt(helperThreads);
                    if (helperThreadsInt > 0) { // if number of threads is valid
                        helperThreadsNum = helperThreadsInt;
                        if(verbosity) System.out.println("Helper Threads Num: " + helperThreadsNum);
                    } else { // if number of threads is not valid, set to default (defined on top of the class)
                        System.out.println("Barrel Helper Threads Num cannot be lower or equal to 0! Defaulting to " + helperThreadsNum + "...");
                    }
                } catch (NumberFormatException e){
                    System.err.println("Helper Threads Num is not a number! Defaulting to " + helperThreadsNum + "...");
                }

            }

            // load max retires num
            String  maxRetriesConfig = BarrelConfigLoader.getProperty("barrel.maxRetries");
            if(maxRetriesConfig == null){ // if not found, set to default (defined on top of the class)
                System.err.println("Barrel Max Retries property not found in property file! Defaulting to " + maxRetries + "...");
            } else { // if found, check it
                try{
                    int maxRetriesInt = Integer.parseInt(maxRetriesConfig);
                    if (maxRetriesInt > 0) { // if max number of retries is valid
                        maxRetries = maxRetriesInt;
                        if(verbosity) System.out.println("Max Retries: " + maxRetries);
                    } else { // if max number of retries is not valid, set it to default (defined on top of the class)
                        System.out.println("Barrel Max Retries cannot be lower or equal to 0! Defaulting to " + maxRetries + "...");
                    }
                } catch (NumberFormatException e){
                    System.err.println("Max Retries is not a number! Defaulting to " + maxRetries + "...");
                }

            }

            // load retry delay
            String retryDelayProperty = BarrelConfigLoader.getProperty("barrel.retryDelay");
            if(retryDelayProperty == null){ // if not found, set to default (defined on top of the class)
                System.err.println("Barrel Retry Delay property not found in property file! Defaulting to " + retryDelay + "...");
            } else { // if found, check it
                try {
                    int retryDelayInt = Integer.parseInt(retryDelayProperty);
                    if (retryDelayInt > 0) { // if retry delay is valid
                        retryDelay = retryDelayInt;
                        if(verbosity) System.out.println("Retry Delay: " + retryDelay);
                    } else { // if retry delay is not valid, set it to default (defined on top of the class)
                        System.out.println("Retry Delay cannot be lower or equal to 0! Defaulting to " + retryDelay + "...");
                    }
                } catch (NumberFormatException e){
                    System.err.println("Retry Delay is not a number! Defaulting to " + retryDelay + "...");
                }

            }

            // load exportation delay
            String exportationDelayProperty = BarrelConfigLoader.getProperty("barrel.exportationDelay");
            if(exportationDelayProperty == null){ // if not found, set to default (defined on top of the class)
                System.err.println("Barrel Exportation Delay property not found in property file! Defaulting to " + exportationDelay + "...");
            } else { // if found, check it
                try{
                    int exportationDelayInt = Integer.parseInt(exportationDelayProperty);
                    if(exportationDelayInt > 10000) { // if exportation delay is valid
                        exportationDelay = exportationDelayInt;
                        if(verbosity) System.out.println("Exportation Delay: " + exportationDelay);
                    } else { // if exportation delay is not valid, set it to default (defined on top of the class)
                        System.out.println("Barrel Exportation Delay cannot be lower or equal to 10000 (10 seconds)! Defaulting to " + exportationDelay + "...");
                    }
                } catch (NumberFormatException ignored){
                    System.err.println("Exportation Delay is not a number! Defaulting to " + exportationDelay + "...");
                }
            }
        } catch (BarrelConfigLoader.ConfigurationException e) {
            System.err.println("Failed to load configuration file: " + e.getMessage());
            System.err.println("Exiting...");
            if(verbosity) System.out.println("-------------------------\n\n");
            System.exit(1);
        }

        if(verbosity) System.out.println("-------------------------\n\n");
    }
    public static void main(String[] args){
        log("UP!");

        loadConfig();

        // setup gateway RMI
        gatewayRemote = null;
        log("Connecting to gateway...");
        while(gatewayRemote == null){
            gatewayRemote = connectToGatewayRMI();
            try {
                Thread.sleep(retryDelay);
            } catch (InterruptedException ignored) {}
        }
        log("Successfully connected to gateway!");


        // try to sync barrel with another one, if it exists
        // if not successful, try to import serialized info
        if(!syncBarrel()){
            log("Couldn't sync barrel with another one. Trying to import serialized info...");
            if(importSerializedInfo()) log("Successfully imported serialized info!");
            else log("Failed to import serialized info...");
        }

        // register barrel in gateway
        registerBarrel();

        // setup barrel RMI
        if(!setupRMI()){System.exit(1);}

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown Hook is running!");
            exit();
        }));

        // setup multicast connection
        socket = setupMulticastConn();
        if(socket == null) return;
        log("Successfully joined multicast group!");

        for(int i=0; i<helperThreadsNum; i++){
            fixedThreadPool.execute(IndexStorageBarrel::messagesParser);
        }

        new Thread(IndexStorageBarrel::periodicBarrelExportation).start();

        try{
            while(!Thread.currentThread().isInterrupted()){
                String message = getMulticastMessage();
                if (message == null) continue;

                multicastMessagesQueue.add(message);
            }
        } catch (Exception e){
            log("Error receiving message: " + e);
        } finally {
            System.exit(1);
        }

    }


    private static ArrayList<String> parseMessage(String message){
        //System.out.println("UNPARSED MESSAGE: " + message);
        String[] splitMessage = message.split(Pattern.quote(String.valueOf(DELIMITER)));
        //System.out.println("PARSED MESSAGE: " + Arrays.toString(splitMessage));
        return new ArrayList<>(Arrays.asList(splitMessage));
    }

    private static boolean hasUrlBeenParsed(String url){
        return IndexStorageBarrel.urlToUrlKeyPairMap.containsKey(url);
    }


    private static long addParsedUrl(String url, String title, String description){
        /* try to increment and retrieve the number of parsed urls */
        long id = -1;
        for (int i = 0; i < IndexStorageBarrel.maxRetries; i++) {
            try {
                id = IndexStorageBarrel.gatewayRemote.incrementAndGetParsedUrls();
                break;
            } catch( ConnectException e){
                reconnectToGatewayRMI();
                i--;
            } catch (RemoteException ignored) {
            }
        }
        if (id == -1){
            System.out.println("Failed to increment and retrieve the number of parsed urls! (" + url + ")");
            return -1;
        }

        ParsedUrl parsedUrl = new ParsedUrl(url, id, title, description, null); // TODO maybe remove the 'text' variable from the parsed url object

        // create new url id pair
        ParsedUrlIdPair urlIdPair = new ParsedUrlIdPair(url, id);
        // associate created url id pair to link
        urlToUrlKeyPairMap.put(url, urlIdPair);
        // associate created url id pair to id
        idToUrlKeyPairMap.put(id, urlIdPair);
        // put parsed url on main hash map, associating it with the url id pair
        parsedUrlsMap.put(urlIdPair, parsedUrl);

        return id;
    }


    private static void indexUrl(ArrayList<String> parsedMessage){
        if(parsedMessage.size() < 4) return;

        String url = parsedMessage.get(0);
        if (hasUrlBeenParsed(url)) { // if url has already been parsed
            ParsedUrlIdPair pair = urlToUrlKeyPairMap.get(url);
            ParsedUrl parsedUrl = parsedUrlsMap.get(pair);
            long id = parsedUrl.id;

            // make sure the title and description are not null (it can happen when the
            // url is created whenever processing a father url and that url doesn't exist,
            // so it's object is created with these 2 fields as null, because the title and description are not known at that time)
            if(parsedUrl.title == null) parsedUrl.title = parsedMessage.get(1);
            if(parsedUrl.description == null) parsedUrl.title = parsedMessage.get(2);

            for (int i = 3; i < parsedMessage.size(); i++) {
                String word = parsedMessage.get(i);
                art.insert(word, id);
            }
            System.out.println("Parsed and updated existing url: " + url);

        } else {
            // get title, description and text
            String title = parsedMessage.get(1);
            String description = parsedMessage.get(2);

            long id = addParsedUrl(url, title, description);
            if(id == -1) return;

            for (int i = 3; i < parsedMessage.size(); i++) {
                String word = parsedMessage.get(i);
                art.insert(word, id);
            }

            System.out.println("Parsed and inserted " + url);
        }
    }


    private static void processFatherUrls(ArrayList<String> parsedMessage){
        if(parsedMessage.size() < 3) return;

        String fatherUrl = parsedMessage.get(1);
        //System.out.println("FATHER URL: " + fatherUrl);
        Long fatherUrlId;
        if(hasUrlBeenParsed(fatherUrl)){
            ParsedUrlIdPair urlIdPair = urlToUrlKeyPairMap.get(fatherUrl);
            if(urlIdPair == null) return;
            ParsedUrl parsedFatherUrl = parsedUrlsMap.get(urlIdPair);
            if(parsedFatherUrl == null) return;
            fatherUrlId = parsedFatherUrl.id;
        } else {
            fatherUrlId = addParsedUrl(fatherUrl, null, null);
            if(fatherUrlId == -1) return;
        }

        for(int i=2; i<parsedMessage.size(); i++){
            String childUrl = parsedMessage.get(i);

            if(hasUrlBeenParsed(childUrl)){
                ParsedUrlIdPair urlIdPair = urlToUrlKeyPairMap.get(childUrl);
                if(urlIdPair == null) continue;
                ParsedUrl parsedChildUrl = parsedUrlsMap.get(urlIdPair);
                if(parsedChildUrl == null) continue;
                parsedChildUrl.addFatherUrl(fatherUrlId);

                System.out.println("Added " + fatherUrl + " as a father of existing " + childUrl);
            } else {
                long id = addParsedUrl(childUrl, null, null);
                if(id == -1) return;

                ParsedUrlIdPair urlIdPair = urlToUrlKeyPairMap.get(childUrl);
                if(urlIdPair == null) continue;
                ParsedUrl parsedChildUrl = parsedUrlsMap.get(urlIdPair);
                if(parsedChildUrl == null) continue;
                parsedChildUrl.addFatherUrl(fatherUrlId);
                System.out.println("Added " + fatherUrl + " as a father of newly created " + childUrl);
            }
        }
    }


    @Override
    public ArrayList<ArrayList<String>> getFatherUrls(ArrayList<String> urls) throws RemoteException {
        ArrayList<ArrayList<String>> fatherUrls = new ArrayList<>();

        for (String url : urls) {
            if (!hasUrlBeenParsed(url)) continue;
            ArrayList<String> fatherUrlList = new ArrayList<>();

            ParsedUrlIdPair urlIdPair = urlToUrlKeyPairMap.get(url);
            if (urlIdPair == null) continue;
            ParsedUrl parsedUrl = parsedUrlsMap.get(urlIdPair);
            if (parsedUrl == null) continue;

            ArrayList<Long> fartherUrlsIds = parsedUrl.getFatherUrls();

            for (Long fatherUrlId : fartherUrlsIds) {
                ParsedUrlIdPair fatherUrlIdPair = idToUrlKeyPairMap.get(fatherUrlId);
                if (fatherUrlIdPair == null) continue;
                ParsedUrl fatherUrl = parsedUrlsMap.get(fatherUrlIdPair);
                if (fatherUrl == null) continue;

                fatherUrlList.add(fatherUrl.url);
            }

            fatherUrls.add(fatherUrlList);
        }

        return fatherUrls;
    }


    @Override
    public ArrayList<String> getFatherUrls(String url) throws RemoteException {
        if(!hasUrlBeenParsed(url)) return null;

        ParsedUrlIdPair urlIdPair = urlToUrlKeyPairMap.get(url);
        if (urlIdPair == null) return null;
        ParsedUrl parsedUrl = parsedUrlsMap.get(urlIdPair);
        if (parsedUrl == null) return null;

        ArrayList<Long> fartherUrlsIds = parsedUrl.getFatherUrls();
        ArrayList<String> fatherUrls = new ArrayList<>();

        for (Long fatherUrlId : fartherUrlsIds) {
            ParsedUrlIdPair fatherUrlIdPair = idToUrlKeyPairMap.get(fatherUrlId);
            if (fatherUrlIdPair == null) continue;
            ParsedUrl fatherUrl = parsedUrlsMap.get(fatherUrlIdPair);
            if (fatherUrl == null) continue;

            fatherUrls.add(fatherUrl.url);
        }

        return fatherUrls;
    }


    private static void messagesParser() {
        while (!Thread.currentThread().isInterrupted()) {
            String message = null;
            try {
                waitingThreadsNum.incrementAndGet();
                message = multicastMessagesQueue.take();
                waitingThreadsNum.decrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (message == null) continue;

            ArrayList<String> parsedMessage = parseMessage(message);
            //long id = Long.parseLong(parsedMessage.get(0));
            //String url = parsedMessage.get(0);
            switch (parsedMessage.get(0)) {
                case "FATHER_URLS":
                    processFatherUrls(parsedMessage);
                    break;
                default:
                    indexUrl(parsedMessage);
                    break;
            }
        }
    }


    protected static void log(String text){
        System.out.println("[BARREL " + uuid.toString().substring(0, 8) + "] " + text);
    }

    public void insert(String word, long linkIndex){
        art.insert(word, linkIndex);
    }

    public ArrayList<Long> getLinkIndices(String word){
        return art.find(word);
    }

    // TODO return the results based on the popularity instead of just all (also, group them by 10)
    @Override
    public ArrayList<ArrayList<String>> searchWord(String word, int page, int pageSize){
        if(word == null) return null;
        PriorityQueue<ArrayList<String>> results = new PriorityQueue<>(new Comparator<ArrayList<String>>() {
            @Override
            public int compare(ArrayList<String> result1, ArrayList<String> result2) {
                ParsedUrl parsedUrl1 = parsedUrlsMap.get(urlToUrlKeyPairMap.get(result1.get(0)));
                ParsedUrl parsedUrl2 = parsedUrlsMap.get(urlToUrlKeyPairMap.get(result2.get(0)));
                // sorting in descending order of father urls count
                return Integer.compare(parsedUrl2.getFatherUrls().size(), parsedUrl1.getFatherUrls().size());
            }
        });

        ArrayList<Long> linkIndices = getLinkIndices(word);
        if(linkIndices == null || linkIndices.isEmpty()) return null;

        int numResults = 0;
        for(long linkIndex : linkIndices){
            ArrayList<String> result = new ArrayList<>();
            ParsedUrlIdPair pair = idToUrlKeyPairMap.get(linkIndex);
            if(pair == null) continue;
            ParsedUrl parsedUrl = parsedUrlsMap.get(pair);
            if(parsedUrl == null) continue;

            numResults++;

            result.add(parsedUrl.url);
            result.add(parsedUrl.title);
            result.add(parsedUrl.description);

            results.offer(result);
        }
        int totalPagesNumber = numResults/pageSize;

        ArrayList<ArrayList<String>> sortedResults = new ArrayList<>();
        while (!results.isEmpty()) {
            sortedResults.add(results.poll());  // Retrieve and remove the head of this queue
        }

        if(sortedResults.size() < page*pageSize) return null;

        ArrayList<ArrayList<String>> pageResults = sortedResults.subList(page*pageSize, Math.min(sortedResults.size(), page*pageSize+pageSize))
                .stream()
                .map(ArrayList::new)
                .collect(Collectors.toCollection(ArrayList::new));
        pageResults.add(new ArrayList<>(List.of(String.valueOf(totalPagesNumber))));
        return pageResults;
    }


    @Override
    public ArrayList<ArrayList<String>> searchWordSet(ArrayList<String> words, int page, int pageSize){
        if(words == null || words.isEmpty()) return null;

        ArrayList<ArrayList<Long>> linkIndices = new ArrayList<>();
        for(String word : words){
            ArrayList<Long> indices = getLinkIndices(word);
            if(indices == null || indices.isEmpty()) return null;

            linkIndices.add(indices);
        }

        Set<Long> commonElements = new HashSet<>(linkIndices.get(0));
        for (int i = 1; i < linkIndices.size(); i++) {
            Set<Long> currentSet = new HashSet<>(linkIndices.get(i));
            commonElements.retainAll(currentSet);

            if(commonElements.isEmpty()) return null;
        }

        if (commonElements.isEmpty()) return null;

        PriorityQueue<ArrayList<String>> results = new PriorityQueue<>(new Comparator<ArrayList<String>>() {
            @Override
            public int compare(ArrayList<String> result1, ArrayList<String> result2) {
                ParsedUrl parsedUrl1 = parsedUrlsMap.get(urlToUrlKeyPairMap.get(result1.get(0)));
                ParsedUrl parsedUrl2 = parsedUrlsMap.get(urlToUrlKeyPairMap.get(result2.get(0)));
                // sorting in descending order of father urls count
                return Integer.compare(parsedUrl2.getFatherUrls().size(), parsedUrl1.getFatherUrls().size());
            }
        });

        int numResults = 0;
        for(long linkIndex : commonElements){
            ArrayList<String> result = new ArrayList<>();
            ParsedUrlIdPair pair = idToUrlKeyPairMap.get(linkIndex);
            if(pair == null) continue;
            ParsedUrl parsedUrl = parsedUrlsMap.get(pair);
            if(parsedUrl == null) continue;

            numResults++;

            result.add(parsedUrl.url);
            result.add(parsedUrl.title);
            result.add(parsedUrl.description);

            results.offer(result);
        }
        int totalPagesNumber = numResults/pageSize;

        ArrayList<ArrayList<String>> sortedResults = new ArrayList<>();
        while (!results.isEmpty()) {
            sortedResults.add(results.poll());
        }

        ArrayList<ArrayList<String>> pageResults = sortedResults.subList(page*pageSize, page*pageSize+pageSize)
                .stream()
                .map(ArrayList::new)
                .collect(Collectors.toCollection(ArrayList::new));
        pageResults.add(new ArrayList<>(List.of(String.valueOf(totalPagesNumber))));
        return pageResults;
    }


    public static void serializeMap(Object object, String filename) {
        try (FileOutputStream fileOut = new FileOutputStream(filename);
             ObjectOutputStream out = new ObjectOutputStream(fileOut)) {
            out.writeObject(object);
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    public static Object deserializeMap(String filename) {
        Object object = null;
        try (FileInputStream fileIn = new FileInputStream(filename);
             ObjectInputStream in = new ObjectInputStream(fileIn)) {
            object = in.readObject();
        } catch (IOException i) {
            i.printStackTrace();
            return null;
        } catch (ClassNotFoundException c) {
            System.out.println("Class not found");
            c.printStackTrace();
            return null;
        }
        return object;
    }
}
