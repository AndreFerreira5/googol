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
        } catch (IOException e) {
            log(e.getMessage());
            // TODO trigger barrel sync
            return null;
        } catch (Exception e){
            log(e.getMessage());
            return null;
        }
    }


    private void indexWordsParsedUrl(ParsedUrl parsedUrl){
        Long id = parsedUrl.id;
        String text = parsedUrl.text;

        for(String word : text.split("\\s+")){
            //TODO send via multicast word and url id to barrels
        }

        // remove text from object as it is not necessary anymore
        parsedUrl.cleanText();
    }


    private MulticastSocket setupMulticastServer(){
        MulticastSocket socket = null;
        int attempts = 0;
        while(attempts < multicastServerConnectMaxRetries){
            try{
                socket = new MulticastSocket();
                InetAddress group = InetAddress.getByName(multicastAddress);
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
                if(parsedUrl == null) {
                    continue;
                }

                // index words from parsed url
                indexWordsParsedUrl(parsedUrl);

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
