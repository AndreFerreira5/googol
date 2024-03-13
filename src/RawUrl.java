import java.net.URL;
import java.net.MalformedURLException;

public class RawUrl {
    public String url;
    public int depth;
    private static final int MIN_DEPTH = 0;
    private static final int MAX_DEPTH = Gateway.CRAWLING_MAX_DEPTH;

    public RawUrl(String url, int depth){
        validateUrl(url);
        validateDepth(depth);
        this.url = url;
        this.depth = depth;
    }

    public RawUrl(String url){
        this(url, MIN_DEPTH);
    }

    private void validateUrl(String url){
        try {
            new URL(url);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Malformed Url: " + url, e);
        }
    }

    private void validateDepth(int depth){
        if(!(depth >= MIN_DEPTH && depth <= MAX_DEPTH))
            throw new InvalidDepthException("Invalid Url depth: " + depth + ". Must be between " + MIN_DEPTH + " and " + MAX_DEPTH + ".");
    }

    static class InvalidDepthException extends RuntimeException {
        public InvalidDepthException(String message) {
            super(message);
        }
    }
}
