import java.io.Serial;
import java.io.Serializable;

/**
 * The type Parsed url id pair.
 */
public class ParsedUrlIdPair implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final String url;
    private final Long id;

    /**
     * Instantiates a new Parsed url id pair.
     *
     * @param url the url
     * @param key the key
     */
    public ParsedUrlIdPair(String url, long key){
        this.url = url;
        this.id = key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParsedUrlIdPair customKey = (ParsedUrlIdPair) o;
        return url.equals(customKey.url) && id.equals(customKey.id);
    }

    @Override
    public int hashCode() {
        return 31 * url.hashCode() + Long.hashCode(id);
    }

    /**
     * Get url string.
     *
     * @return the string
     */
    public String getUrl(){
        return url;
    }

    /**
     * Get id long.
     *
     * @return the long
     */
    public Long getId(){
        return id;
    }
}
