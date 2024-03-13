import java.net.URL;
import java.net.MalformedURLException;

public class ParsedUrl {
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
