import java.util.concurrent.LinkedBlockingDeque;

public interface CrawlingStrategy {
    void addUrl(LinkedBlockingDeque<RawUrl> deque, RawUrl url);
}

class BFSStartegy implements CrawlingStrategy{
    public void addUrl(LinkedBlockingDeque<RawUrl> deque, RawUrl url){
        deque.addLast(url);
    }
}

class DFSStartegy implements CrawlingStrategy{
    public void addUrl(LinkedBlockingDeque<RawUrl> deque, RawUrl url){
        deque.addFirst(url);
    }
}