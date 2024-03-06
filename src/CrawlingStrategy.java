import java.util.concurrent.LinkedBlockingDeque;

public interface CrawlingStrategy {
    void addUrl(LinkedBlockingDeque<Url> deque, Url url);
}

class BFSStartegy implements CrawlingStrategy{
    public void addUrl(LinkedBlockingDeque<Url> deque, Url url){
        deque.addLast(url);
    }
}

class DFSStartegy implements CrawlingStrategy{
    public void addUrl(LinkedBlockingDeque<Url> deque, Url url){
        deque.addFirst(url);
    }
}