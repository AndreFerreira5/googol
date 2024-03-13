import java.util.concurrent.LinkedBlockingDeque;
import java.util.Collections;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Document;
import java.util.Set;
import java.util.UUID;

import java.io.IOException;

public class Downloader  extends Thread{
    private final UUID uuid = UUID.randomUUID();
    private final Set<String> parsedUrls;
    private final LinkedBlockingDeque<Url> deque;
    private final int CRAWLING_MAX_DEPTH;
    private final CrawlingStrategy crawlingStrategy;

    // constructor with all downloader parameters
    private Downloader(LinkedBlockingDeque<Url> deque, Set<String> parsedUrls, int CRAWLING_MAX_DEPTH, CrawlingStrategy crawlingStrategy) {
        this.deque = deque;
        this.parsedUrls = Collections.synchronizedSet(parsedUrls);
        this.CRAWLING_MAX_DEPTH = CRAWLING_MAX_DEPTH;
        this.crawlingStrategy = crawlingStrategy;
    }


    public static class Builder {
        private LinkedBlockingDeque<Url> deque;
        private Set<String> parsedUrls;
        private int CRAWLING_MAX_DEPTH = 2; // default crawling max depth
        private CrawlingStrategy crawlingStrategy = new BFSStartegy(); // default crawling strategy

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

        public Downloader build() {
            return new Downloader(deque, parsedUrls, CRAWLING_MAX_DEPTH, crawlingStrategy);
        }
    }

    private void log(String text){
        System.out.println("[DOWNLOADER THREAD " + uuid.toString().substring(0, 8) + "] " + text);
    }

    private void storeUrlToBarrels(String title, String description, String keywords, String text){
        // TODO treat arguments and store them to barrels using multicast catching all exceptions and continuing accordingly
    }

    private void parseUrl(Url url){
        String link = url.url;
        int depth = url.depth;

        if(parsedUrls.contains(link) || depth > CRAWLING_MAX_DEPTH) {// TODO later integrate this with the barrels to make sure the barrels contain the url info (it's more robust this way)
            //log(url + " already parsed! skipping...");
            return;
        }

        Document doc;
        String allowedUrlRegex = "^https?://.*";
        try{
            doc = Jsoup.connect(link)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3")
                    .referrer("http://www.google.com")
                    .timeout(2 * 1000) // 2 second timeout
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

            storeUrlToBarrels(title, description, keywords, text);
            parsedUrls.add(link);
        } catch (IOException e) {
            //log("Error parsing " + url + "! Skipping...");
        }
    }

    public void run(){
        log("UP!");
        boolean interrupted = false;

        while(!interrupted){
            try{
                // get url from deque
                Url url = deque.take();
                parseUrl(url);
            } catch (InterruptedException e){
                log("Interrupted! Exiting...");
                interrupted = true;
                Thread.currentThread().interrupt();
            } catch ( Exception ignored){} // catches malformed url and invalid url depth exceptions
        }
    }

}
