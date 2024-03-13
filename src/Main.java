import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

public class Main {
    private static final int DOWNLOADER_THREADS_NUM = 3;
    private static final int BARRELS_NUM = 3;
    public static final int CRAWLING_MAX_DEPTH = 3;
    public static final Long PARSED_URLS = 0L;
    public static final String MULTICAST_ADDRESS = "224.3.2.1";
    public static final int PORT = 4322;

    private static void exportART(AdaptiveRadixTree art){
        try{
            art.exportART();
        } catch(FileNotFoundException e){
            System.out.println("TREE FILE NOT FOUND! Stopping the exportation...");
        } catch(IOException e) {
            System.out.println("ERROR OPENING FILE: " + e + "\nStopping the exportation...");
        }
    }


    private static void importART(AdaptiveRadixTree art){
        try{
            art.importART();
        } catch(FileNotFoundException e){
            System.out.println("TREE FILE NOT FOUND! Skipping the importation...");
        } catch(IOException e) {
            System.out.println("ERROR OPENING FILE: " + e + "\nSkipping the importation...");
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AdaptiveRadixTree art = new AdaptiveRadixTree();
        importART(art);

        LinkedBlockingDeque<Url> deque = new LinkedBlockingDeque<>();
        ConcurrentHashMap<ParsedUrlKeyPair, Url> parsedUrlMap = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, ParsedUrlKeyPair> urlParsedUrlKeyPairMap = new ConcurrentHashMap<>();
        ConcurrentHashMap<Long, ParsedUrlKeyPair> longParsedUrlKeyPairMap = new ConcurrentHashMap<>();

        Set<String> parsedUrls = new HashSet<>();

        int i;
        for(i=0; i<DOWNLOADER_THREADS_NUM; i++){
            Thread downloaderThread = new Thread(new Downloader.Builder()
                    .deque(deque)
                    .parsedUrls(parsedUrls)
                    .crawlingMaxDepth(CRAWLING_MAX_DEPTH)
                    .crawlingStrategy(new BFSStartegy())
                    .multicastAddress(MULTICAST_ADDRESS)
                    .port(PORT)
                    .build());
            downloaderThread.start();
        }

        for(i=0; i<BARRELS_NUM; i++){
            Thread barrelThread = new Thread(new IndexStorageBarrel.Builder()
                                                        .multicastAddress(MULTICAST_ADDRESS)
                                                        .port(PORT)
                                                        .build());
            barrelThread.start();
        }

        while(true){
            System.out.println("DEQUEUE: " + deque.size());
            System.out.println("PARSED URLS: " + parsedUrls.size());
            Thread.sleep(1*1000);
        }
    }
}