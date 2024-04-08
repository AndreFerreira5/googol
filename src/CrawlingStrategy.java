import java.util.concurrent.LinkedBlockingDeque;

/**
 * Crawling Strategy interface.
 * Used to create different crawling strategies
 */
public interface CrawlingStrategy {
    /**
     * Add url to deque
     *
     * @param deque the deque
     * @param url   the url
     */
    void addUrl(LinkedBlockingDeque<RawUrl> deque, RawUrl url);
    String getStrategyName();
}

/**
 * BFS startegy.
 */
class BFSStartegy implements CrawlingStrategy{
    public void addUrl(LinkedBlockingDeque<RawUrl> deque, RawUrl url){
        deque.addLast(url);
    }
    public String getStrategyName(){
        return "Bread First Search";
    }
}

/**
 * DFS startegy.
 */
class DFSStartegy implements CrawlingStrategy{
    public void addUrl(LinkedBlockingDeque<RawUrl> deque, RawUrl url){
        deque.addFirst(url);
    }
    public String getStrategyName(){
        return "Depth First Search";
    }
}