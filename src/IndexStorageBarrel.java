import java.util.UUID;

public class IndexStorageBarrel {
    private Trie trie;
    public final UUID uuid = UUID.randomUUID();

    public IndexStorageBarrel(){
        trie = new Trie();
    }

    public void insert(String word, int linkIndex){
        trie.insert(word, linkIndex);
    }

    public int[] getLinkIndices(String word){
        return trie.getLinkIndices(word);
    }
}