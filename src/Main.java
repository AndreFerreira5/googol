import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;

public class Main {
    private static final int DOWNLOADER_THREADS_NUM = 3;
    private static final int BARRELS_NUM = 3;
    public static final int CRAWLING_MAX_DEPTH = 3;

    public static void main(String[] args) throws InterruptedException {
        AdaptiveRadixTree ART = new AdaptiveRadixTree();
        ART.insert("Java", 1);
        ART.insert("Jeva", 2);
        ART.insert("Jiva", 3);
        ART.insert("Jova", 4);
        ART.insert("Juva", 5);
        ART.insert("Xono", 6);
        ART.insert("Mino", 7);
        ART.insert("Trino", 8);
        ART.insert("Quino", 9);
        System.out.println(ART.find("Java"));
        System.out.println(ART.find("Jeva"));
        System.out.println(ART.find("Jiva"));
        System.out.println(ART.find("Jova"));
        System.out.println(ART.find("Juva"));
        System.out.println(ART.find("Xono"));
        System.out.println(ART.find("Mino"));
        System.out.println(ART.find("Trino"));
        System.out.println(ART.find("Quino"));
        ART.findNode("J");
        System.exit(0);
        LinkedBlockingDeque<Url> deque = new LinkedBlockingDeque<>();
        Set<String> parsedUrls = new HashSet<>();

        int i=0;
        for(; i<BARRELS_NUM; i++){
            IndexStorageBarrel barrel = new IndexStorageBarrel();
            System.out.println("BARREL " + (i+1) + " (" + barrel.uuid + ") CREATED");
        }

        for(i=0; i<DOWNLOADER_THREADS_NUM; i++){
            Thread downloaderThread = new Thread(new Downloader.Builder()
                                                        .deque(deque)
                                                        .parsedUrls(parsedUrls)
                                                        .crawlingMaxDepth(CRAWLING_MAX_DEPTH)
                                                        .crawlingStrategy(new BFSStartegy())
                                                        .build());
            downloaderThread.start();
        }



        deque.add(new Url("https://medium.com/pythoneers/9-different-ways-to-embedded-code-in-medium-9213cb4c0a2e"));


        while(true){
            //for(Url url : deque){
            //    System.out.println(url.url);
            //}
            //System.out.println(parsedUrls);
            System.out.println("DEQUEUE: " + deque.size());
            System.out.println("PARSED URLS: " + parsedUrls.size());
            Thread.sleep(1*1000);
        }
    }
}