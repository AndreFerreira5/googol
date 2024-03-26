import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.ArrayList;

public class Gateway {
    private static final int DOWNLOADER_THREADS_NUM = 0;
    private static final int BARRELS_NUM = 1;
    public static final int CRAWLING_MAX_DEPTH = 1;
    public static AtomicLong PARSED_URLS = new AtomicLong();
    public static final String MULTICAST_ADDRESS = "224.3.2.1";
    public static final int PORT = 4322;
    public static final char DELIMITER = '|';


    public static void main(String[] args) throws InterruptedException {

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

        ArrayList<IndexStorageBarrel> barrels = new ArrayList<>();
        for(i=0; i<BARRELS_NUM; i++){
            IndexStorageBarrel barrel = new IndexStorageBarrel.Builder()
                    .multicastAddress(MULTICAST_ADDRESS)
                    .port(PORT)
                    .build();

            Thread barrelThread = new Thread(barrel);
            barrelThread.start();
            barrels.add(barrel);
        }

        deque.add(new RawUrl("https://en.wikipedia.org/wiki/Main_Page"));

        while(!deque.isEmpty()){
            System.out.println("DEQUEUE: " + deque.size());
            System.out.println("PARSED URLS: " + parsedUrlsMap.size());
            Thread.sleep(1*1000);
        }

        // TODO wait/join all created threads when system is shutting down, or signal is caught
    }
}