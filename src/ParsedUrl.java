import java.io.Serial;
import java.io.Serializable;

public class ParsedUrl implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    public String url;
    public Long id;
    public String title;
    public String description;
    public String text;


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
}
