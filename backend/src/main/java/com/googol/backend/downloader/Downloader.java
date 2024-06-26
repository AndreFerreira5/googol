package com.googol.backend.downloader;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.rmi.ConnectException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.text.Collator;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.io.IOException;

import com.googol.backend.storage.IndexStorageBarrel;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Document;

import com.googol.backend.gateway.GatewayRemote;
import com.googol.backend.model.RawUrl;


/**
 * The type Downloader config loader.
 */
class DownloaderConfigLoader{
    private static final Properties properties = new Properties();

    /**
     * The type Configuration exception.
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
        } catch (DownloaderConfigLoader.ConfigurationException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private static void loadProperties(){
        String filePath = "config/downloader.properties";
        try (InputStream input = new FileInputStream(filePath)){
            properties.load(input);
        } catch (IOException e){
            throw new ConfigurationException("Failed to load configuration properties " + e.getMessage(), e);
        }
    }

    /**
     * Gets property.
     *
     * @param key the key
     * @return the property
     */
    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
}


/**
 * The type Downloader.
 */
public class Downloader{
    private static boolean verbosity = false; // default
    private static final UUID uuid = UUID.randomUUID();
    private static final String identifier = "Downloader-" + uuid.toString();
    private static int crawlingMaxDepth;
    private static int urlTimeout = 2000;
    private static String multicastAddress;
    private static int port;
    private static MulticastSocket socket;
    private static int maxRetries = 5;
    private static int retryDelay = 1000; // 1 second
    private static char DELIMITER;
    private static GatewayRemote gatewayRemote;
    private static String gatewayEndpoint;


    private static void log(String text){
        System.out.println("[DOWNLOADER " + uuid.toString().substring(0, 8) + "] " + text);
    }


    private static ArrayList<String>[] parseRawUrl(RawUrl url){
        String link = url.url;
        if(link.contains(String.valueOf(DELIMITER))) return null;

        int depth = url.depth;
        if(depth > crawlingMaxDepth) { // if depth exceeds crawling max depth skip
            return null;
        }

        Document doc;
        try{
            doc = Jsoup.connect(link)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3")
                    .referrer("http://www.google.com")
                    .timeout(urlTimeout)
                    .ignoreHttpErrors(true)
                    .get();

            //System.out.println("Url language: " + doc.select("html").attr("lang"));


            // get all urls inside the url and put it in the queue
            Elements subUrls = doc.select("a[href]");
            ArrayList<RawUrl> rawUrls = new ArrayList<>();
            ArrayList<String> fatherUrls = new ArrayList<>();
            fatherUrls.add("FATHER_URLS"); // FATHER_URLS for the barrels to know what to do
            fatherUrls.add(link); // url for the barrel to add to as father url to all the following urls

            for(Element subUrl : subUrls){
                String href = subUrl.attr("abs:href");
                if(href.contains(String.valueOf(DELIMITER))) continue;

                fatherUrls.add(href + DELIMITER);
                try{
                    if(depth+1 > crawlingMaxDepth) continue;
                    RawUrl rawUrl = new RawUrl(href, depth+1);
                    rawUrls.add(rawUrl);
                } catch (Exception ignored){} // if url is invalid or depth exceeds the max, just ignore it and go to the next url
            }

            // add urls to deque through rmi
            boolean added = false;
            for(int i=0; i<maxRetries; i++){
                try {
                    gatewayRemote.addRawUrlsToUrlsDeque(rawUrls);
                    added = true;
                    break;
                } catch (RemoteException ignored){}
            }
            if(!added) log("Error adding urls to urls deque. Proceeding...");


            String pageLang = doc.select("html").attr("lang");
            if(pageLang.isEmpty()){
                Elements metaTags = doc.getElementsByTag("meta");
                for (Element metaTag : metaTags) {
                    String httpEquiv = metaTag.attr("http-equiv");
                    String content = metaTag.attr("content");
                    if ("content-language".equalsIgnoreCase(httpEquiv)) {
                        pageLang = content;
                        break;
                    }
                }
            }

            Locale locale = Locale.forLanguageTag(pageLang);
            Collator collator = Collator.getInstance(locale);
            collator.setStrength(Collator.PRIMARY);

            //String[] parts = pageLang.split("[-_]", 2); // Splits on hyphen or underscore
            //String languageCode = parts[0]; // The language code (mandatory)
            //String countryCode = parts.length > 1 ? parts[1] : "";

            // get page title, description, keywords and text
            String title = doc.title().replaceAll(String.valueOf(DELIMITER), "");
            System.out.println("raw title: " + doc.title() + " - processed title: " + title);
            String description = doc.select("meta[name=description]").attr("content").replaceAll(String.valueOf(DELIMITER), "");
            //String keywords = doc.select("meta[name=keywords]").attr("content");
            String text = doc.body().text().replaceAll(String.valueOf(DELIMITER), ""); // remove | from the text to prevent conflicts
            text = text.toLowerCase(locale);

            if(link == null || title == null || description == null || text == null) return null;
            ArrayList<String> parsedUrlInfo = new ArrayList<>();
            parsedUrlInfo.add(link);
            parsedUrlInfo.add(title);
            parsedUrlInfo.add(description);
            parsedUrlInfo.addAll(getUniqueWordsFromText(text));
            return new ArrayList[]{parsedUrlInfo, fatherUrls}; // return father urls and parsedUrlInfo;
        } catch (IOException e) { // TODO notify user that the url he requested is invalid
            return null;
        } catch (Exception e){
            return null;
        }
    }


    private static ArrayList<String> getUniqueWordsFromText(String text){
        ArrayList<String> buffer = new ArrayList<>();

        // hashset to keep track of unique words
        HashSet<String> wordsSet = new HashSet<>();

        // append all unique words to the buffer
        for(String word : text.replaceAll("\\p{Punct}", " ").trim().split("\\s+")){
            if(wordsSet.add(word + DELIMITER))
                buffer.add(word + DELIMITER);
        }

        return buffer;
    }


    private static MulticastSocket setupMulticastServer(){
        MulticastSocket socket = null;
        while(true){
            try{
                socket = new MulticastSocket(port);
                InetAddress group = InetAddress.getByName(multicastAddress);
                socket.joinGroup(group);
                return socket;
            } catch (IOException e){
                System.out.println(multicastAddress);
                System.out.println(e.getMessage());
                log("Error setting up multicast server! Retrying in "+ retryDelay +"s...");
                try {
                    Thread.sleep(retryDelay); // wait before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log("Interrupted during retry wait! Stopping...");
                    return null;
                }
            }
        }

        /*
        // if the connection wasn't successful
        if(socket != null) socket.close();
        log("Error setting up multicast server after " + maxRetries + " attempts! Exiting...");
        return null;
         */
    }


    private static void transmitUrlInfoToBarrels(ArrayList<String> buffer, MulticastSocket socket) {
        // 65535bytes ip packet - 20bytes ip header - 8bytes udp header
        final int MAX_PACKET_SIZE = 65507;

        String url = buffer.get(0);
        String title = buffer.get(1);
        String description = buffer.get(2);

        BiConsumer<ByteBuffer, Integer> sendPacket = (byteBuffer, size) -> {
            try {
                InetAddress group = InetAddress.getByName(multicastAddress);
                DatagramPacket packet = new DatagramPacket(byteBuffer.array(), size, group, port);
                socket.send(packet);
            } catch (IOException e) {
                log("Error transmitting to barrels. Retrying in " + retryDelay + "s...");
                try {
                    Thread.sleep(retryDelay); // wait before retrying
                    InetAddress group = InetAddress.getByName(multicastAddress);
                    DatagramPacket packet = new DatagramPacket(byteBuffer.array(), size, group, port);
                    socket.send(packet);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log("Interrupted during retry wait! Interrupting...");
                    //interrupt();
                } catch (IOException ex) {
                    throw new RuntimeException(e);
                }
            }
        };

        ByteBuffer dataBuffer = ByteBuffer.allocate(MAX_PACKET_SIZE);
        int currentSize = 0;

        byte[] urlBytes = (url + DELIMITER).getBytes(StandardCharsets.UTF_8);
        byte[] titleBytes = (title + DELIMITER).getBytes(StandardCharsets.UTF_8);
        byte[] descriptionBytes = (description + DELIMITER).getBytes(StandardCharsets.UTF_8);

        dataBuffer.put(urlBytes);
        dataBuffer.put(titleBytes);
        dataBuffer.put(descriptionBytes);
        currentSize = urlBytes.length + titleBytes.length + descriptionBytes.length;
        // TODO improve this, when a a packet is larger than MAX_PACKET_SIZE and needs to be segmented, the title and description dont need to be transmitted again
        for(int i=3; i<buffer.size(); i++){
            byte[] messageBytes = buffer.get(i).getBytes(StandardCharsets.UTF_8);
            if (currentSize + messageBytes.length > MAX_PACKET_SIZE) {
                // Send current buffer and reset for next chunk
                sendPacket.accept(dataBuffer, currentSize);
                dataBuffer.clear();
                dataBuffer.put(urlBytes);
                dataBuffer.put(titleBytes);
                dataBuffer.put(descriptionBytes);
                currentSize = urlBytes.length + titleBytes.length + descriptionBytes.length;
            }
            dataBuffer.put(messageBytes);
            currentSize += messageBytes.length;
        }

        // Send any remaining data
        if (currentSize > 0) {
            sendPacket.accept(dataBuffer, currentSize);
        }

    }


    private static void transmitFatherUrlsToBarrels(ArrayList<String> buffer, MulticastSocket socket) {
        // 65535bytes ip packet - 20bytes ip header - 8bytes udp header
        final int MAX_PACKET_SIZE = 65507;

        String fatherUrlIdentifier = buffer.get(0);
        String fatherUrl = buffer.get(1);

        BiConsumer<ByteBuffer, Integer> sendPacket = (byteBuffer, size) -> {
            try {
                InetAddress group = InetAddress.getByName(multicastAddress);
                DatagramPacket packet = new DatagramPacket(byteBuffer.array(), size, group, port);
                socket.send(packet);
            } catch (IOException e) {
                log("Error transmitting to barrels. Retrying in " + retryDelay + "s...");
                try {
                    Thread.sleep(retryDelay); // wait before retrying
                    InetAddress group = InetAddress.getByName(multicastAddress);
                    DatagramPacket packet = new DatagramPacket(byteBuffer.array(), size, group, port);
                    socket.send(packet);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log("Interrupted during retry wait! Interrupting...");
                    //interrupt();
                } catch (IOException ex) {
                    throw new RuntimeException(e);
                }
            }
        };

        ByteBuffer dataBuffer = ByteBuffer.allocate(MAX_PACKET_SIZE);
        int currentSize = 0;

        byte[] fatherUrlIdentifierBytes = (fatherUrlIdentifier + DELIMITER).getBytes(StandardCharsets.UTF_8);
        byte[] fatherUrlBytes = (fatherUrl + DELIMITER).getBytes(StandardCharsets.UTF_8);

        dataBuffer.put(fatherUrlIdentifierBytes);
        dataBuffer.put(fatherUrlBytes);
        currentSize = fatherUrlIdentifierBytes.length + fatherUrlBytes.length;
        // TODO improve this, when a a packet is larger than MAX_PACKET_SIZE and needs to be segmented, the title and description dont need to be transmitted again
        for(int i=2; i<buffer.size(); i++){
            byte[] messageBytes = buffer.get(i).getBytes(StandardCharsets.UTF_8);
            if (currentSize + messageBytes.length > MAX_PACKET_SIZE) {
                // Send current buffer and reset for next chunk
                sendPacket.accept(dataBuffer, currentSize);
                dataBuffer.clear();
                dataBuffer.put(fatherUrlIdentifierBytes);
                dataBuffer.put(fatherUrlBytes);
                currentSize = fatherUrlIdentifierBytes.length + fatherUrlBytes.length;
            }
            dataBuffer.put(messageBytes);
            currentSize += messageBytes.length;
        }

        // Send any remaining data
        if (currentSize > 0) {
            sendPacket.accept(dataBuffer, currentSize);
        }

    }


    private static GatewayRemote connectToGatewayRMI(){
        while(true){
            try {
                GatewayRemote gateway = (GatewayRemote) Naming.lookup(gatewayEndpoint);
                DELIMITER = gateway.getParsingDelimiter();
                crawlingMaxDepth = gateway.getCrawlingMaxDepth();
                multicastAddress = gateway.getMulticastAddress();
                port = gateway.getMulticastPort();
                return gateway;
            } catch (Exception e) {
                System.out.println("Failed to connect to Gateway! Retrying in " + retryDelay + " seconds...");
                System.out.println("Error: " + e.getMessage());
                try{
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ignored){
                    Thread.currentThread().interrupt();
                    return null;
                }

            }
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

        // register downloader on gateway
        registerDownloader();
    }


    /**
     * Register downloader in gateway
     */
    private static void registerDownloader(){
        // register downloader in gateway
        boolean registered = false;
        for (int i = 0; i < Downloader.maxRetries; i++) {
            try {
                gatewayRemote.registerDownloader(identifier);
                registered = true;
                break;
            } catch( ConnectException e){
                reconnectToGatewayRMI();
                i--;
            } catch (RemoteException ignored){}
        }
        if (!registered){
            log("Error registering downloader in Gateway! (" + maxRetries + " retries failed) Exiting...");
            System.exit(1);
        }
        log("Successfully registered downloader in Gateway! Downloader UUID: " + uuid.toString());
    }


    private static void loadConfig(){
        try{
            // load verbosity
            String verbosityConfig = DownloaderConfigLoader.getProperty("downloader.verbosity");
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


            String gatewayHost = DownloaderConfigLoader.getProperty("gateway.host");
            if(gatewayHost == null){
                System.err.println("Gateway Host property not found in property file! Exiting...");
                System.exit(1);
            }
            if(verbosity) System.out.println("Gateway Host: " + gatewayHost);

            String gatewayServiceName = DownloaderConfigLoader.getProperty("gateway.serviceName");
            if(gatewayServiceName == null){
                System.err.println("Gateway Service Name property not found in property file! Exiting...");
                System.exit(1);
            }
            if(verbosity) System.out.println("Gateway Service Name: " + gatewayServiceName);

            gatewayEndpoint = "//"+gatewayHost+"/"+gatewayServiceName;
            if(verbosity) System.out.println("Gateway Endpoint: " + gatewayEndpoint);

            // load url timeout
            String urlTimeoutConfig = DownloaderConfigLoader.getProperty("downloader.urlTimeout");
            if(urlTimeoutConfig == null){ // if not found, set to default (defined on top of the class)
                System.err.println("Url Timeout property not found in property file! Defaulting to " + urlTimeout + "...");
            } else { // if found, check it
                try {
                    int urlTimeoutInt = Integer.parseInt(urlTimeoutConfig);
                    if (urlTimeoutInt > 0) { // if retry delay is valid
                        urlTimeout = urlTimeoutInt;
                        if(verbosity) System.out.println("Url Timeout: " + urlTimeout);
                    } else { // if retry delay is not valid, set it to default (defined on top of the class)
                        System.out.println("Url Timeout cannot be lower or equal to 0! Defaulting to " + urlTimeout + "...");
                    }
                } catch (NumberFormatException e){
                    System.err.println("Url Timeout is not a number! Defaulting to " + urlTimeout + "...");
                }

            }

            // load max retries num
            String  maxRetriesConfig = DownloaderConfigLoader.getProperty("downloader.maxRetries");
            if(maxRetriesConfig == null){ // if not found, set to default (defined on top of the class)
                System.err.println("Barrel Max Retries property not found in property file! Defaulting to " + maxRetries + "...");
            } else { // if found, check it
                try{
                    int maxRetriesInt = Integer.parseInt(maxRetriesConfig);
                    if (maxRetriesInt > 0) { // if max number of retries is valid
                        maxRetries = maxRetriesInt;
                        if(verbosity) System.out.println("Max Retries: " + maxRetries);
                    } else { // if max number of retries is not valid, set it to default (defined on top of the class)
                        System.out.println("Max Retries cannot be lower or equal to 0! Defaulting to " + maxRetries + "...");
                    }
                } catch (NumberFormatException e){
                    System.err.println("Max Retries is not a number! Defaulting to " + maxRetries + "...");
                }

            }

            // load retry delay
            String retryDelayProperty = DownloaderConfigLoader.getProperty("downloader.retryDelay");
            if(retryDelayProperty == null){ // if not found, set to default (defined on top of the class)
                System.err.println("Retry Delay property not found in property file! Defaulting to " + retryDelay + "...");
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

        } catch (DownloaderConfigLoader.ConfigurationException e) {
            System.err.println("Failed to load configuration file: " + e.getMessage());
            System.err.println("Exiting...");
            if(verbosity) System.out.println("-------------------------\n\n");
            System.exit(1);
        }

        if(verbosity) System.out.println("-------------------------\n\n");
    }


    /**
     * Exit function to close program
     */
    private static void exit(){
        // unregister downloader in gateway
        boolean unregistered = false;
        for (int i = 0; i < Downloader.maxRetries; i++) {
            try {
                gatewayRemote.unregisterDownloader(identifier);
                unregistered = true;
                break;
            } catch( ConnectException e){
                reconnectToGatewayRMI();
                i--;
            } catch (RemoteException ignored){}
        }
        if(!unregistered) log("Error unregistering downloader in Gateway! (" + maxRetries + " retries failed) Exiting...");

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        System.exit(1);
    }


    /**
     * The entry point of application.
     *
     * @param args the input arguments
     */
    public static void main(String[] args) {
        log("UP!");

        loadConfig();

        // setup gateway RMI
        gatewayRemote = connectToGatewayRMI();
        if(gatewayRemote == null) System.exit(1);

        registerDownloader();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown Hook is running!");
            exit();
        }));


        // setup multicast server
        socket = setupMulticastServer();
        if(socket == null) return;
        log("Successfully connected to multicast server!");


        boolean interrupted = false;
        while(!interrupted){
            try{
                // get raw url from deque and parse it
                RawUrl rawUrl;
                try {
                    rawUrl = gatewayRemote.getUrlFromDeque();
                } catch (ConnectException e){ // if there is a connection error try to connect again until it connects
                    reconnectToGatewayRMI();
                    continue;
                } catch (Exception e){
                    log("Error getting url from deque: " + e.getMessage());
                    continue;
                }

                ArrayList<String>[] parsedInfo = parseRawUrl(rawUrl);
                if(parsedInfo == null) continue;

                ArrayList<String> parsedUrlInfo = parsedInfo[0];
                ArrayList<String> fatherUrls = parsedInfo[1];

                // transmit buffer through multicast
                try{
                    transmitUrlInfoToBarrels(parsedUrlInfo, socket);
                    transmitFatherUrlsToBarrels(fatherUrls, socket);
                } catch (Exception e){
                    log("Error transmitting to barrels. Skipping...");
                    log(e.getMessage());
                    continue;
                }

                System.out.println("Parsed and sent " + parsedUrlInfo.get(0));

            } catch ( Exception ignored){} // catches malformed url and invalid url depth exceptions
        }
    }

}
