import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.Collections;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Document;
import java.util.Set;
import java.util.UUID;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import java.io.IOException;

public class Downloader  extends Thread{
    private final UUID uuid = UUID.randomUUID();
    private final Set<String> parsedUrls;
    private final LinkedBlockingDeque<Url> deque;
    private final int crawlingMaxDepth;
    private CrawlingStrategy crawlingStrategy;
    private final int urlTimeout = 2000;
    private final String multicastAddress;
    private final int port;
    private final int multicastServerConnectMaxRetries = 5;
    private final int retryDelay = 5;

    // constructor with all downloader parameters
    private Downloader(LinkedBlockingDeque<Url> deque, Set<String> parsedUrls, int crawlingMaxDepth, CrawlingStrategy crawlingStrategy, String multicastAddress, int port) {
        this.deque = deque;
        this.parsedUrls = Collections.synchronizedSet(parsedUrls);
        this.crawlingMaxDepth = crawlingMaxDepth;
        this.crawlingStrategy = crawlingStrategy;
        this.multicastAddress = multicastAddress;
        this.port = port;
    }


    public static class Builder {
        private LinkedBlockingDeque<Url> deque;
        private Set<String> parsedUrls;
        private int CRAWLING_MAX_DEPTH = 2; // default crawling max depth
        private CrawlingStrategy crawlingStrategy = new BFSStartegy(); // default crawling strategy
        private String multicastAddress = "224.3.2.1";
        private int port = 4321;

        public Builder deque(LinkedBlockingDeque<Url> deque) {
            this.deque = deque;
            return this;
        }

        public Builder parsedUrls(Set<String> parsedUrls) {
            this.parsedUrls = parsedUrls;
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
            return new Downloader(deque, parsedUrls, CRAWLING_MAX_DEPTH, crawlingStrategy, multicastAddress, port);
        }
    }

    private void log(String text){
        System.out.println("[DOWNLOADER THREAD " + uuid.toString().substring(0, 8) + "] " + text);
    }

    private void storeUrlToBarrels(String title, String description, String keywords, String text){
        // TODO treat arguments and store them to barrels using multicast catching all exceptions and continuing accordingly
    }

    private String[] parseUrl(Url url){
        String link = url.url;
        int depth = url.depth;

        if(parsedUrls.contains(link) || depth > crawlingMaxDepth) {// TODO later integrate this with the barrels to make sure the barrels contain the url info (it's more robust this way)
            //log(url + " already parsed! skipping...");
            return null;
        }

        Document doc;
        //String allowedUrlRegex = "^https?://.*";
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
                // add url at the start of the deque
                deque.addFirst(new Url(href, depth+1));
            }

            // get page title, description, keywords and text
            String title = doc.title();
            String description = doc.select("meta[name=description]").attr("content");
            String keywords = doc.select("meta[name=keywords]").attr("content");
            String text = doc.body().text();

            parsedUrls.add(link);
            return new String[]{title, description, keywords, text};
            //storeUrlToBarrels(title, description, keywords, text);
        } catch (IOException e) {
            // TODO trigger barrel sync
            return null;
            //log("Error parsing " + url + "! Skipping...");
        }
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

                // get url from deque
                Url url = deque.take();
                String[] parsedUrl = parseUrl(url);
                if(parsedUrl == null) continue;

                //sendParsedUrlToBarrels(socket, parsedUrl, linkIndex);
            } catch (InterruptedException e){
                log("Interrupted! Exiting...");
                interrupted = true;
                Thread.currentThread().interrupt();
            } catch ( Exception ignored){} // catches malformed url and invalid url depth exceptions
        }
    }

}
