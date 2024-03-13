import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;

public class Gateway {
    private static final int DOWNLOADER_THREADS_NUM = 20;
    private static final int BARRELS_NUM = 3;
    public static final int CRAWLING_MAX_DEPTH = 3;
    public static AtomicLong PARSED_URLS = new AtomicLong();
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

        LinkedBlockingDeque<RawUrl> deque = new LinkedBlockingDeque<>();
        ConcurrentHashMap<ParsedUrlIdPair, ParsedUrl> parsedUrlsMap = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, ParsedUrlIdPair> urlToUrlKeyPairMap = new ConcurrentHashMap<>();
        ConcurrentHashMap<Long, ParsedUrlIdPair> idToUrlKeyPairMap = new ConcurrentHashMap<>();

        int i;
        for(i=0; i<DOWNLOADER_THREADS_NUM; i++){
            Thread downloaderThread = new Thread(new Downloader.Builder()
                    .deque(deque)
                    .parsedUrlsMap(parsedUrlsMap)
                    .urlToUrlKeyPairMap(urlToUrlKeyPairMap)
                    .idToUrlKeyPairMap(idToUrlKeyPairMap)
                    .crawlingMaxDepth(CRAWLING_MAX_DEPTH)
                    .crawlingStrategy(new BFSStartegy()) // Breath First Search - can be changed to DFS
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
            System.out.println("PARSED URLS: " + parsedUrlsMap.size());
            Thread.sleep(1*1000);
        }
    }
}