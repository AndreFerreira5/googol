import java.util.ArrayList;
import java.util.UUID;

public class IndexStorageBarrel {
    private AdaptiveRadixTree art;
    public final UUID uuid = UUID.randomUUID();

    public IndexStorageBarrel(){
        art = new AdaptiveRadixTree();
    }

    public void insert(String word, int linkIndex){
        art.insert(word, linkIndex);
    }

    public ArrayList<Long> getLinkIndices(String word){
        return art.find(word);
    }
}