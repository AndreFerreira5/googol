import java.io.Serial;
import java.io.Serializable;

public class ParsedUrlIdPair implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final String url;
    private final Long id;

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

    public String getUrl(){
        return url;
    }

    public Long getId(){
        return id;
    }
}
