import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;

public class ParsedUrl implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public String url;
    public Long id;
    public String title;
    public String description;
    public String text;
    private final ArrayList<Long> fatherUrls = new ArrayList<>();


    public ParsedUrl(String url, Long id, String title, String description, String text){
        this.url = url;
        this.id = id;
        this.title = title;
        this.description = description;
        this.text = text;
    }

    public void cleanText(){
        this.text = "";
    }

    public void addFatherUrl(long id){
        this.fatherUrls.add(id);
    }
}
