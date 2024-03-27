import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Document;

import java.util.UUID;
import java.net.InetAddress;
import java.net.MulticastSocket;

import java.io.IOException;

public class Downloader  extends Thread{
    private final UUID uuid = UUID.randomUUID();
    private final LinkedBlockingDeque<RawUrl> deque;
    private ConcurrentHashMap<ParsedUrlIdPair, ParsedUrl> parsedUrlsMap;
    private ConcurrentHashMap<String, ParsedUrlIdPair> urlToUrlKeyPairMap;
    private ConcurrentHashMap<Long, ParsedUrlIdPair> idToUrlKeyPairMap;
    private final int crawlingMaxDepth;
    private CrawlingStrategy crawlingStrategy;
    private final int urlTimeout = 2000;
    private final String multicastAddress;
    private final int port;
    private final int multicastServerConnectMaxRetries = 5;
    private final int retryDelay = 5;
    private final char DELIMITER = Gateway.DELIMITER;

    // constructor with all downloader parameters
    private Downloader(LinkedBlockingDeque<RawUrl> deque, ConcurrentHashMap<ParsedUrlIdPair, ParsedUrl> parsedUrlsMap, ConcurrentHashMap<String, ParsedUrlIdPair> urlToUrlKeyPairMap, ConcurrentHashMap<Long, ParsedUrlIdPair> idToUrlKeyPairMap, int crawlingMaxDepth, CrawlingStrategy crawlingStrategy, String multicastAddress, int port) {
        this.deque = deque;
        this.parsedUrlsMap = parsedUrlsMap;
        this.urlToUrlKeyPairMap = urlToUrlKeyPairMap;
        this.idToUrlKeyPairMap = idToUrlKeyPairMap;
        this.crawlingMaxDepth = crawlingMaxDepth;
        this.crawlingStrategy = crawlingStrategy;
        this.multicastAddress = multicastAddress;
        this.port = port;
    }


    public static class Builder {
        private LinkedBlockingDeque<RawUrl> deque;
        private ConcurrentHashMap<ParsedUrlIdPair, ParsedUrl> parsedUrlsMap;
        private ConcurrentHashMap<String, ParsedUrlIdPair> urlToUrlKeyPairMap;
        private ConcurrentHashMap<Long, ParsedUrlIdPair> idToUrlKeyPairMap;
        private int CRAWLING_MAX_DEPTH = 2; // default crawling max depth
        private CrawlingStrategy crawlingStrategy = new BFSStartegy(); // default crawling strategy
        private String multicastAddress = "224.3.2.1";
        private int port = 4321;

        public Builder deque(LinkedBlockingDeque<RawUrl> deque) {
            this.deque = deque;
            return this;
        }

        public Builder parsedUrlsMap(ConcurrentHashMap<ParsedUrlIdPair, ParsedUrl> parsedUrlsMap) {
            this.parsedUrlsMap = parsedUrlsMap;
            return this;
        }

        public Builder urlToUrlKeyPairMap(ConcurrentHashMap<String, ParsedUrlIdPair> urlToUrlKeyPairMap) {
            this.urlToUrlKeyPairMap = urlToUrlKeyPairMap;
            return this;
        }

        public Builder idToUrlKeyPairMap(ConcurrentHashMap<Long, ParsedUrlIdPair> idToUrlKeyPairMap) {
            this.idToUrlKeyPairMap = idToUrlKeyPairMap;
            return this;
        }

        public Builder crawlingMaxDepth(int CRAWLING_MAX_DEPTH) {
            this.CRAWLING_MAX_DEPTH = CRAWLING_MAX_DEPTH;
            return this;
        }

        public Builder crawlingStrategy(CrawlingStrategy crawlingStrategy) {
            this.crawlingStrategy = crawlingStrategy;
            return this;
        }

        public Builder multicastAddress(String multicastAddress) {
            this.multicastAddress = multicastAddress;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Downloader build() {
            return new Downloader(deque, parsedUrlsMap, urlToUrlKeyPairMap, idToUrlKeyPairMap, CRAWLING_MAX_DEPTH, crawlingStrategy, multicastAddress, port);
        }
    }

    private void log(String text){
        System.out.println("[DOWNLOADER THREAD " + uuid.toString().substring(0, 8) + "] " + text);
    }

    private ParsedUrl parseRawUrl(RawUrl url){
        String link = url.url;
        int depth = url.depth;
        if(urlToUrlKeyPairMap.containsKey(link) || depth > crawlingMaxDepth) {
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
            // get all urls inside of the url and put it in the queue
            Elements subUrls = doc.select("a[href]");
            for(Element subUrl : subUrls){
                String href = subUrl.attr("abs:href");
                // try to add url to deque depending on the crawling strategy
                try{
                    crawlingStrategy.addUrl(deque, new RawUrl(href, depth+1));
                } catch (Exception ignored){} // if url is invalid or depth exceeds the max, just ignore it and go to the next url

            }

            // get page title, description, keywords and text
            String title = doc.title();
            String description = doc.select("meta[name=description]").attr("content");
            //String keywords = doc.select("meta[name=keywords]").attr("content");
            String text = doc.body().text();

            // get number of parsed urls from global variable and increment it
            long parsedUrlsNum = Gateway.PARSED_URLS.getAndIncrement();

            // return new parsed url object
            return new ParsedUrl(link, parsedUrlsNum, title, description, text);
        } catch (IOException e) { // TODO notify user that the url he requested is invalid
            // TODO trigger barrel sync
            return null;
        } catch (Exception e){
            return null;
        }
    }


    private ArrayList<String> indexWordsParsedUrl(ParsedUrl parsedUrl){
        Long id = parsedUrl.id;
        String text = parsedUrl.text;

        ArrayList<String> buffer = new ArrayList<>();

        // start the buffer with the url id
        buffer.add(id.toString() + DELIMITER);

        // hashset to keep track of unique words
        HashSet<String> wordsSet = new HashSet<>();

        // append all unique words to the buffer
        for(String word : text.replaceAll("\\W+", " ").trim().split("\\s+")){
            if (!wordsSet.contains(word)) {
                buffer.add(word + DELIMITER);
                wordsSet.add(word); // add the word to the hashset to track uniqueness
            }
        }

        // remove text from object as it is not necessary anymore
        parsedUrl.cleanText();

        return buffer;
    }


    private MulticastSocket setupMulticastServer(){
        MulticastSocket socket = null;
        int attempts = 0;
        while(attempts < multicastServerConnectMaxRetries){
            try{
                socket = new MulticastSocket(port);
                InetAddress group = InetAddress.getByName(multicastAddress);
                socket.joinGroup(group);
                return socket;
            } catch (IOException e){
                log("Error setting up multicast server! Retrying in "+ retryDelay +"s...");
                attempts++;
                try {
                    Thread.sleep(retryDelay); // wait before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log("Interrupted during retry wait! Stopping...");
                    interrupt();
                }
            }
        }

        // if the connection wasn't successful
        if(socket != null) socket.close();
        log("Error setting up multicast server after " + multicastServerConnectMaxRetries + " attempts! Exiting...");
        interrupt();
        return null;
    }


    private void transmitToBarrels(ArrayList<String> buffer, Long id, MulticastSocket socket) {
        // 65535bytes ip packet - 20bytes ip header - 8bytes udp header
        final int MAX_PACKET_SIZE = 65507;

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
                    interrupt();
                } catch (IOException ex) {
                    throw new RuntimeException(e);
                }
            }
        };

        ByteBuffer dataBuffer = ByteBuffer.allocate(MAX_PACKET_SIZE);
        int currentSize = 0;

        byte[] idBytes = (id.toString() + DELIMITER).getBytes(StandardCharsets.UTF_8);
        //dataBuffer.put(idBytes);
        //currentSize += idBytes.length;
        for (String s : buffer) {
            byte[] messageBytes = s.getBytes(StandardCharsets.UTF_8);
            if (currentSize + messageBytes.length > MAX_PACKET_SIZE) {
                // Send current buffer and reset for next chunk
                sendPacket.accept(dataBuffer, currentSize);
                dataBuffer.clear();
                dataBuffer.put(idBytes);
                currentSize = idBytes.length;
            }
            dataBuffer.put(messageBytes);
            currentSize += messageBytes.length;
        }

        // Send any remaining data
        if (currentSize > 0) {
            sendPacket.accept(dataBuffer, currentSize);
        }



        /*byte[] dataBuffer = new byte[buffer.size()];
        for (int i = 0; i < buffer.size(); i++) {
            dataBuffer[i] = buffer.get(i).getBytes()[0];
        }*/

        /*
        // Calculate the total size needed for all strings in bytes
        int totalSize = buffer.stream().mapToInt(String::length).sum();
        ByteBuffer dataBuffer = ByteBuffer.allocate(totalSize);

        // Add each string to the buffer
        for (String s : buffer) {
            dataBuffer.put(s.getBytes(StandardCharsets.UTF_8));
        }

        try{
            InetAddress group = InetAddress.getByName(multicastAddress);
            DatagramPacket packet = new DatagramPacket(dataBuffer.array(), dataBuffer.position(), group, port);
            socket.send(packet);
        } catch (IOException e){
            log("Error transmitting to barrels. Retrying in "+ retryDelay +"s...");
            try {
                Thread.sleep(retryDelay); // wait before retrying
                InetAddress group = InetAddress.getByName(multicastAddress);
                DatagramPacket packet = new DatagramPacket(dataBuffer.array(), dataBuffer.position(), group, port);
                socket.send(packet);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log("Interrupted during retry wait! Interrupting...");
                interrupt();
            } catch (IOException ex) {
                throw  new RuntimeException(e);
            }
        }*/

    }


    public void run(){
        log("UP!");

        // setup multicast server
        MulticastSocket socket = setupMulticastServer();
        if(socket == null) return;
        log("Successfully connected to multicast server!");

        boolean interrupted = false;
        while(!interrupted){
            try{
                // get raw url from deque and parse it
                RawUrl rawUrl = deque.take();
                ParsedUrl parsedUrl = parseRawUrl(rawUrl);
                if(parsedUrl == null || parsedUrl.id == null || parsedUrl.url == null) {
                    continue;
                }

                // index words from parsed url
                ArrayList<String> buffer = indexWordsParsedUrl(parsedUrl);

                // transmit buffer through multicast
                try{
                    transmitToBarrels(buffer, parsedUrl.id, socket);
                } catch (Exception e){
                    log("Error transmitting to barrels. Skipping...");
                    log(e.getMessage());
                    continue;
                }


                String link = parsedUrl.url;
                Long id = parsedUrl.id;
                // create new url id pair
                ParsedUrlIdPair urlIdPair = new ParsedUrlIdPair(link, id);
                // associate created url id pair to link
                urlToUrlKeyPairMap.put(link, urlIdPair);
                // associate created url id pair to id
                idToUrlKeyPairMap.put(id, urlIdPair);
                // put parsed url on main hash map, associating it with the url id pair
                parsedUrlsMap.put(urlIdPair, parsedUrl);

            } catch (InterruptedException e){
                log("Interrupted! Exiting...");
                interrupted = true;
                Thread.currentThread().interrupt();
            } catch ( Exception ignored){} // catches malformed url and invalid url depth exceptions
        }
    }

}
