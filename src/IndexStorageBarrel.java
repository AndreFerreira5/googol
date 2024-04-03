import java.io.*;
import java.rmi.ConnectException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.concurrent.LinkedBlockingQueue;

public class IndexStorageBarrel extends UnicastRemoteObject implements IndexStorageBarrelRemote{
    private static AdaptiveRadixTree art = new AdaptiveRadixTree();
    public static final UUID uuid = UUID.randomUUID();
    private static final String host = "localhost";
    private static final String barrelRMIEndpoint = "//" + host + "/IndexStorageBarrel-" + uuid.toString();
    private static String multicastAddress;
    private static final int HELPER_THREADS_NUM = 16;
    private static int port;
    private static int rmiPort; // TODO maybe get this from gateway?
    protected static final int maxRetries = 5;
    private static final int retryDelay = 1000; // 1 second
    protected static char DELIMITER;
    protected static GatewayRemote gatewayRemote;
    private static final ExecutorService fixedThreadPool = Executors.newFixedThreadPool(HELPER_THREADS_NUM);
    protected static BlockingQueue<String> multicastMessagesQueue = new LinkedBlockingQueue<>();
    protected static ConcurrentHashMap<ParsedUrlIdPair, ParsedUrl> parsedUrlsMap = new ConcurrentHashMap<>();
    protected static ConcurrentHashMap<String, ParsedUrlIdPair> urlToUrlKeyPairMap = new ConcurrentHashMap<>();
    protected static ConcurrentHashMap<Long, ParsedUrlIdPair> idToUrlKeyPairMap = new ConcurrentHashMap<>();

    protected IndexStorageBarrel() throws RemoteException {}


    private static String getRandomBarrelFromGateway(){
        // unregister barrel in gateway
        String randomBarrel = "";
        boolean got = false;
        for (int i = 0; i < IndexStorageBarrel.maxRetries; i++) {
            try {
                gatewayRemote.getRandomBarrelRemote();
                got = true;
                break;
            } catch( ConnectException e){
                reconnectToGatewayRMI();
                i--;
            } catch (RemoteException ignored){}
        }

        return randomBarrel;
    }


    private static void syncBarrel(){
        String randomBarrel = "";
        while(randomBarrel.isEmpty() || randomBarrel.equals(barrelRMIEndpoint)){
            randomBarrel = getRandomBarrelFromGateway();
        }

        IndexStorageBarrelRemote barrel = connectToBarrelRMI(randomBarrel);
        if (barrel == null) return;

        log("Syncing Barrel with " + randomBarrel);

        // unregister barrel in gateway
        boolean synced = false;
        for (int i = 0; i < IndexStorageBarrel.maxRetries; i++) {
            try {
                AdaptiveRadixTree barrelART = barrel.getArt();
                ConcurrentHashMap<ParsedUrlIdPair, ParsedUrl> barrelParsedUrlsMap = barrel.getParsedUrlsMap();
                ConcurrentHashMap<String, ParsedUrlIdPair> barrelUrlToUrlKeyPairMap = barrel.getUrlToUrlKeyPairMap();
                ConcurrentHashMap<Long, ParsedUrlIdPair> barrelIdToUrlKeyPairMap = barrel.getIdToUrlKeyPairMap();
                art = barrelART;
                parsedUrlsMap = barrelParsedUrlsMap;
                urlToUrlKeyPairMap = barrelUrlToUrlKeyPairMap;
                idToUrlKeyPairMap = barrelIdToUrlKeyPairMap;
                synced = true;
                break;
            } catch (RemoteException ignored){}
        }
        if(!synced) log("Error syncing ART! Proceeding with current ART.");
        else log("Barrel synced successfully!");
    }


    private static String getMulticastMessage(MulticastSocket socket){
        //byte[] dataBuffer = new byte[messageSize];
        byte[] dataBuffer = new byte[65507];
        DatagramPacket packet = new DatagramPacket(dataBuffer, dataBuffer.length);
        try{
            socket.receive(packet);
        } catch (IOException e){
            log("Error receiving multicast message.");
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
    public AdaptiveRadixTree getArt(){
        return art;
    }

    @Override
    public ConcurrentHashMap<ParsedUrlIdPair, ParsedUrl> getParsedUrlsMap(){
        return parsedUrlsMap;
    }

    @Override
    public ConcurrentHashMap<String, ParsedUrlIdPair> getUrlToUrlKeyPairMap(){
        return urlToUrlKeyPairMap;
    }

    @Override
    public ConcurrentHashMap<Long, ParsedUrlIdPair> getIdToUrlKeyPairMap(){
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



    private void exportDeserializedInfo(){
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
            GatewayRemote gateway = (GatewayRemote) Naming.lookup("//localhost/GatewayService");
            DELIMITER = gateway.getDelimiter();
            multicastAddress = gateway.getMulticastAddress();
            port = gateway.getPort();
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
    }


    private static IndexStorageBarrelRemote connectToBarrelRMI(String barrelEndpoint){
        try {
            return (IndexStorageBarrelRemote) Naming.lookup(barrelEndpoint);
        } catch (Exception e) {
            System.out.println("Barrel exception: " + e.getMessage());
            return null;
        }
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

        multicastMessagesQueue.clear();
        fixedThreadPool.shutdownNow();
        try {
            if (!fixedThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        System.exit(1);
    }


    public static void main(String[] args){
        log("UP!");

        //art.setFilename("barrel-" + uuid.toString() + ".art"); // give each barrel its unique art file

        if(importSerializedInfo()) log("Successfully imported serialized info!");
        else log("Failed to import serialized info...");

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

        // register barrel in gateway
        boolean registered = false;
        for (int i = 0; i < IndexStorageBarrel.maxRetries; i++) {
            try {
                gatewayRemote.registerBarrel(barrelRMIEndpoint);
                rmiPort = gatewayRemote.getPort();
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

        // setup barrel RMI
        if(!setupRMI()){System.exit(1);}

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown Hook is running!");
            exit();
        }));

        // setup multicast connection
        MulticastSocket socket = setupMulticastConn();
        if(socket == null) return;
        log("Successfully joined multicast group!");

        for(int i=0; i<HELPER_THREADS_NUM; i++){
            fixedThreadPool.execute(IndexStorageBarrel::messagesParser);
        }

        try{
            while(true){
                String message = getMulticastMessage(socket);
                if (message == null) continue;

                multicastMessagesQueue.add(message);
            }
        } catch (Exception e){
            log("Error receiving message: " + e);
        } finally {
            fixedThreadPool.shutdown();
            exportART(art);
            exit();
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


    private static void messagesParser() {
        boolean running = true;
            while (running) {
                String message = null;
                try {
                    message = multicastMessagesQueue.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    running = false;
                    continue;
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
    public ArrayList<ArrayList<String>> searchWord(String word){
        if(word == null) return null;
        ArrayList<ArrayList<String>> results = new ArrayList<>();

        ArrayList<Long> linkIndices = getLinkIndices(word);
        if(linkIndices == null || linkIndices.isEmpty()) return null;
        for(long linkIndex : linkIndices){
            ArrayList<String> result = new ArrayList<>();
            ParsedUrlIdPair pair = idToUrlKeyPairMap.get(linkIndex);
            ParsedUrl parsedUrl = parsedUrlsMap.get(pair);
            result.add(parsedUrl.url);
            result.add(parsedUrl.title);
            result.add(parsedUrl.description);

            results.add(result);
        }

        return results;
    }

    @Override
    public ArrayList<ArrayList<String>> searchWords(ArrayList<String> words){
        if(words == null) return null;
        ArrayList<ArrayList<String>> results = new ArrayList<>();

        for(String word : words){
            ArrayList<Long> linkIndices = getLinkIndices(word);
            if(linkIndices == null || linkIndices.isEmpty()) continue;
            for(long linkIndex : linkIndices){
                ArrayList<String> result = new ArrayList<>();
                ParsedUrlIdPair pair = idToUrlKeyPairMap.get(linkIndex);
                ParsedUrl parsedUrl = parsedUrlsMap.get(pair);
                result.add(parsedUrl.url);
                result.add(parsedUrl.title);
                result.add(parsedUrl.description);

                results.add(result);
            }
        }

        return results;
    }


    @Override
    public ArrayList<ArrayList<String>> searchWordSet(ArrayList<String> words){
        if(words == null || words.isEmpty()) return null;

        ArrayList<ArrayList<Long>> linkIndices = new ArrayList<>();
        for(String word : words){
            ArrayList<Long> indices = getLinkIndices(word);
            if(indices == null || indices.isEmpty()) return null;

            linkIndices.add(indices);
        }

        Set<Long> commonElements = new HashSet<>(linkIndices.get(0));
        System.out.println(commonElements);
        System.out.println(linkIndices.size());
        for (int i = 1; i < linkIndices.size(); i++) {
            Set<Long> currentSet = new HashSet<>(linkIndices.get(i));
            commonElements.retainAll(currentSet);

            if(commonElements.isEmpty()) return null;
        }

        if (commonElements.isEmpty()) return null;

        ArrayList<ArrayList<String>> results = new ArrayList<>();
        for(long linkIndex : commonElements){
            ArrayList<String> result = new ArrayList<>();
            ParsedUrlIdPair pair = idToUrlKeyPairMap.get(linkIndex);
            ParsedUrl parsedUrl = parsedUrlsMap.get(pair);
            result.add(parsedUrl.url);
            result.add(parsedUrl.title);
            result.add(parsedUrl.description);

            results.add(result);
        }

        return results;
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
