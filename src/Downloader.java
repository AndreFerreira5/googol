import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.rmi.ConnectException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.function.BiConsumer;
import java.util.ArrayList;
import java.util.HashSet;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Document;

import java.util.UUID;
import java.net.InetAddress;
import java.net.MulticastSocket;

import java.io.IOException;

public class Downloader{
    private static final UUID uuid = UUID.randomUUID();
    private static int crawlingMaxDepth;
    private static final int urlTimeout = 2000;
    private static String multicastAddress;
    private static int port;
    private static final String host = "localhost";
    private static final int maxRetries = 5;
    private static final int retryDelay = 1000; // 1 second
    private static char DELIMITER;
    private static GatewayRemote gatewayRemote;


    private static void log(String text){
        System.out.println("[DOWNLOADER " + uuid.toString().substring(0, 8) + "] " + text);
    }


    private static ArrayList<String>[] parseRawUrl(RawUrl url){
        String link = url.url;
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

            // get all urls inside the url and put it in the queue
            Elements subUrls = doc.select("a[href]");
            ArrayList<RawUrl> rawUrls = new ArrayList<>();
            ArrayList<String> fatherUrls = new ArrayList<>();
            fatherUrls.add("FATHER_URLS"); // FATHER_URLS for the barrels to know what to do
            fatherUrls.add(link); // url for the barrel to add to as father url to all the following urls

            for(Element subUrl : subUrls){
                String href = subUrl.attr("abs:href");
                
                fatherUrls.add(href + DELIMITER);
                try{
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

            // get page title, description, keywords and text
            String title = doc.title();
            String description = doc.select("meta[name=description]").attr("content");
            //String keywords = doc.select("meta[name=keywords]").attr("content");
            String text = doc.body().text().replaceAll(String.valueOf(DELIMITER), ""); // remove | from the text to prevent conflicts

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
            /*if (!wordsSet.contains(word)) {
                buffer.add(word + DELIMITER);
                wordsSet.add(word); // add the word to the hashset to track uniqueness
            }*/
        }

        return buffer;
    }


    private static MulticastSocket setupMulticastServer(){
        MulticastSocket socket = null;
        int attempts = 0;
        while(attempts < maxRetries){
            try{
                socket = new MulticastSocket(port);
                InetAddress group = InetAddress.getByName(multicastAddress);
                socket.joinGroup(group);
                return socket;
            } catch (IOException e){
                System.out.println(multicastAddress);
                System.out.println(e.getMessage());
                log("Error setting up multicast server! Retrying in "+ retryDelay +"s...");
                attempts++;
                try {
                    Thread.sleep(retryDelay); // wait before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log("Interrupted during retry wait! Stopping...");
                    return null;
                }
            }
        }

        // if the connection wasn't successful
        if(socket != null) socket.close();
        log("Error setting up multicast server after " + maxRetries + " attempts! Exiting...");
        return null;
    }


    private static void transmitUrlInfoToBarrels(ArrayList<String> buffer, MulticastSocket socket) {
        System.out.println(buffer);
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
        try {
            GatewayRemote gateway = (GatewayRemote) Naming.lookup("//" + host + "/GatewayService");
            DELIMITER = gateway.getDelimiter();
            crawlingMaxDepth = gateway.getCrawlingMaxDepth();
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


    /**
     * The entry point of application.
     *
     * @param args the input arguments
     */
    public static void main(String[] args) {
        log("UP!");

        // setup gateway RMI
        gatewayRemote = connectToGatewayRMI();
        if(gatewayRemote == null) System.exit(1);


        // setup multicast server
        MulticastSocket socket = setupMulticastServer();
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
