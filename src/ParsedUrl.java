import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;

/**
 * The type Parsed url.
 */
// TODO make this object concurrent
public class ParsedUrl implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The Url.
     */
    public String url;
    /**
     * The Id.
     */
    public Long id;
    /**
     * The Title.
     */
    public String title;
    /**
     * The Description.
     */
    public String description;
    /**
     * The Text.
     */
    public String text;
    private final ArrayList<Long> fatherUrls;


    /**
     * Instantiates a new Parsed url.
     *
     * @param url         the url
     * @param id          the id
     * @param title       the title
     * @param description the description
     * @param text        the text
     */
    public ParsedUrl(String url, Long id, String title, String description, String text){
        this.url = url;
        this.id = id;
        this.title = title;
        this.description = description;
        this.text = text;
        this.fatherUrls = new ArrayList<>();
    }

    /**
     * Clean text.
     */
    public void cleanText(){
        this.text = "";
    }

    /**
     * Add father url.
     *
     * @param id the id
     */
    public synchronized void addFatherUrl(long id){
        if(!fatherUrls.contains(id)) this.fatherUrls.add(id);
    }

    /**
     * Get father urls array list.
     *
     * @return the array list
     */
    public ArrayList<Long> getFatherUrls(){
        return this.fatherUrls;
    }
}
