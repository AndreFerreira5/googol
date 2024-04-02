import java.io.*;
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
    private static final String barrelRMIEndpoint = "//localhost/IndexStorageBarrel-" + uuid.toString();
    private static String multicastAddress;
    private static final int HELPER_THREADS_NUM = 16;
    private static int port;
    private static int rmiPort; // TODO maybe get this from gateway?
    protected static final int maxRetries = 5;
    private static final int retryDelay = 5;
    protected static char DELIMITER;
    protected static GatewayRemote gatewayRemote;
    private static ExecutorService fixedThreadPool = Executors.newFixedThreadPool(HELPER_THREADS_NUM);
    protected static BlockingQueue<String> multicastMessagesQueue = new LinkedBlockingQueue<>();
    protected static ConcurrentHashMap<ParsedUrlIdPair, ParsedUrl> parsedUrlsMap = new ConcurrentHashMap<>();
    protected static ConcurrentHashMap<String, ParsedUrlIdPair> urlToUrlKeyPairMap = new ConcurrentHashMap<>();
    protected static ConcurrentHashMap<Long, ParsedUrlIdPair> idToUrlKeyPairMap = new ConcurrentHashMap<>();

    protected IndexStorageBarrel() throws RemoteException {}


    private static String getMulticastMessage(MulticastSocket socket){
        //byte[] dataBuffer = new byte[messageSize];
        byte[] dataBuffer = new byte[65507];
        DatagramPacket packet = new DatagramPacket(dataBuffer, dataBuffer.length);
        try{
            socket.receive(packet);
        } catch (IOException e){
            log("Error receiving multicast message.");
            // TODO trigger sync between barrels
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


    public void exportART(){
        exportART(art);
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
            } catch (RemoteException ignored){}
        }
        if(!unregistered) log("Error unregistering barrel in Gateway! (" + maxRetries + " retries failed) Exiting...");

        System.exit(1);
    }


    public static void main(String[] args){
        log("UP!");

        if(importSerializedInfo()) log("Successfully imported serialized info!");
        else log("Failed to import serialized info...");

        // setup gateway RMI
        gatewayRemote = connectToGatewayRMI();
        if(gatewayRemote == null) System.exit(1);
        log("Successfully connected to gateway!");

        // register barrel in gateway
        boolean registered = false;
        for (int i = 0; i < IndexStorageBarrel.maxRetries; i++) {
            try {
                gatewayRemote.registerBarrel(barrelRMIEndpoint);
                rmiPort = gatewayRemote.getPort();
                registered = true;
                break;
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
        String[] splitMessage = message.split(Pattern.quote(String.valueOf(DELIMITER)));
        return new ArrayList<>(Arrays.asList(splitMessage));
    }

    private static boolean hasUrlBeenParsed(String url){
        return IndexStorageBarrel.urlToUrlKeyPairMap.containsKey(url);
    }


    private static void messagesParser() {
        while(true) {
            String message = null;
            try {
                message = multicastMessagesQueue.take();
            } catch (InterruptedException e){
                Thread.currentThread().interrupt();
                continue;
            }
            if(message == null) continue;

            ArrayList<String> parsedMessage = parseMessage(message);
            //long id = Long.parseLong(parsedMessage.get(0));
            String url = parsedMessage.get(0);
            if (hasUrlBeenParsed(url)) { // if url has already been parsed, do nothing
                ParsedUrlIdPair pair = IndexStorageBarrel.urlToUrlKeyPairMap.get(url);
                long id = IndexStorageBarrel.parsedUrlsMap.get(pair).id;

                for (int i = 3; i < parsedMessage.size(); i++) {
                    String word = parsedMessage.get(i);
                    art.insert(word, id);
                }
                System.out.println("Parsed and updated existing url: " + url);
            } else {
                /* try to increment and retrieve the number of parsed urls */
                long id = -1;
                for (int i = 0; i < IndexStorageBarrel.maxRetries; i++) {
                    try {
                        id = IndexStorageBarrel.gatewayRemote.incrementAndGetParsedUrls();
                        break;
                    } catch (RemoteException ignored) {
                    }
                }
                if (id == -1) return;

                // get title, description and text
                String title = parsedMessage.get(1);
                String description = parsedMessage.get(2);
                //String text = parsedMessage.get(3);

                ParsedUrl parsedUrl = new ParsedUrl(url, id, title, description, null); // TODO maybe remove the 'text' variable from the parsed url object

                // create new url id pair
                ParsedUrlIdPair urlIdPair = new ParsedUrlIdPair(url, id);
                // associate created url id pair to link
                IndexStorageBarrel.urlToUrlKeyPairMap.put(url, urlIdPair);
                // associate created url id pair to id
                IndexStorageBarrel.idToUrlKeyPairMap.put(id, urlIdPair);
                // put parsed url on main hash map, associating it with the url id pair
                IndexStorageBarrel.parsedUrlsMap.put(urlIdPair, parsedUrl);

                for (int i = 3; i < parsedMessage.size(); i++) {
                    String word = parsedMessage.get(i);
                    art.insert(word, id);
                }

                System.out.println("Parsed and inserted " + url);
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
