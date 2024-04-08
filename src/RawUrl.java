import java.io.Serial;
import java.io.Serializable;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * The type Raw url.
 */
public class RawUrl implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * The Url.
     */
    public String url;
    /**
     * The Depth.
     */
    public int depth;
    private static final int MIN_DEPTH = 0;
    //private static final int MAX_DEPTH = Gateway.crawlingMaxDepth; // TODO maybe change this?

    /**
     * Instantiates a new Raw url.
     *
     * @param url   the url
     * @param depth the depth
     */
    public RawUrl(String url, int depth){
        validateUrl(url);
        //validateDepth(depth);
        this.url = url;
        this.depth = depth;
    }

    /**
     * Instantiates a new Raw url.
     *
     * @param url the url
     */
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

    /*
    private void validateDepth(int depth){
        if(!(depth >= MIN_DEPTH && depth <= MAX_DEPTH))
            throw new InvalidDepthException("Invalid Url depth: " + depth + ". Must be between " + MIN_DEPTH + " and " + MAX_DEPTH + ".");
    }*/

    /**
     * The type Invalid depth exception.
     */
    static class InvalidDepthException extends RuntimeException implements Serializable{
        /**
         * Instantiates a new Invalid depth exception.
         *
         * @param message the message
         */
        public InvalidDepthException(String message) {
            super(message);
        }
    }
}
