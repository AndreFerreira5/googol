public class ParsedUrlKeyPair {
    private final String url;
    private final Long key;

    public ParsedUrlKeyPair(String url, long key){
        this.url = url;
        this.key = key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParsedUrlKeyPair customKey = (ParsedUrlKeyPair) o;
        return url.equals(customKey.url) && key.equals(customKey.key);
    }

    @Override
    public int hashCode() {
        return 31 * url.hashCode() + Long.hashCode(key);
    }

    public String getUrl(){
        return url;
    }

    public Long getKey(){
        return key;
    }
}
