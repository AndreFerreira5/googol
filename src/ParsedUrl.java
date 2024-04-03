import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;

// TODO make this object concurrent
public class ParsedUrl implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public String url;
    public Long id;
    public String title;
    public String description;
    public String text;
    private final ArrayList<Long> fatherUrls;


    public ParsedUrl(String url, Long id, String title, String description, String text){
        this.url = url;
        this.id = id;
        this.title = title;
        this.description = description;
        this.text = text;
        this.fatherUrls = new ArrayList<>();
    }

    public void cleanText(){
        this.text = "";
    }

    public synchronized void addFatherUrl(long id){
        this.fatherUrls.add(id);
    }

    public ArrayList<Long> getFatherUrls(){
        return this.fatherUrls;
    }
}
