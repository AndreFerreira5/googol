import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.engine.*;
import java.util.*;

public class AdaptiveRadixTreeTest {

    private AdaptiveRadixTree art;

    private String[] generateWordsWithSharedPrefix(String prefix, int numWords){
        String[] words = new String[numWords];
        for(int i = 0; i<numWords; i++){
            words[i] = prefix + (char)i;
        }
        return words;
    }

    private long[] generateLinkIndices(int numIndices){
        long[] linkIndices = new long[numIndices];
        for(int i = 0; i<numIndices; i++){
            linkIndices[i] = i;
        }
        return linkIndices;
    }

    @BeforeEach
    public void ARTSetup(){
        art = new AdaptiveRadixTree();
    }


    /* BASIC INSERTION AND SEARCH TESTS */
    @Test
    public void testInsertAndFindSingleWord() {
        String word = "hello";
        long linkIndex = 12345L;

        // insert a word with a link index
        art.insert(word, linkIndex);

        // attempt to find the inserted word
        ArrayList<Long> result = art.find(word);

        // verify the result contains the correct link index
        assertNotNull(result, "The result should not be null for an existing word");
        assertTrue(result.contains(linkIndex), "The result should contain the inserted link index");
    }

    @Test
    public void testFindNonExistingWord() {
        String word = "nonexistent";
        // attempt to find the non existent word
        ArrayList<Long> result = art.find(word);

        // verify the result contains the correct link index
        assertNull(result, "The result should be null for a non existing word");
    }

    /* NODE UPGRADE TESTS */
    @Test
    public void testRootNode4UpgradeToNode16(){
        final int childrenNum = 5; // 5 to exceed the children size of Node4 and make it upgrade to Node16
        String prefix = "";
        String[] words = generateWordsWithSharedPrefix(prefix, childrenNum); // generate single letter words, so they stay on the same node (root)
        long[] linkIndices = generateLinkIndices(childrenNum); // generate linear link indices for each word

        // insert each word in the tree
        for(int i=0; i<childrenNum; i++){
            art.insert(words[i], linkIndices[i]);
        }

        // verify that the root node is a Node16 (searching for the node that contains any of the previously inserted words)
        assertInstanceOf(Node16.class, art.findNode(prefix));
    }

    @Test
    public void testNode4UpgradeToNode16(){
        final int childrenNum = 5; // 5 to exceed the children size of Node4 and make it upgrade to Node16
        String prefix = "a";
        String[] word = generateWordsWithSharedPrefix(prefix, childrenNum); // generate words bigger that have more than one letter, so they don't stay on the root (in this case 2 letter words)
        long[] linkIndices = generateLinkIndices(childrenNum); // generate linear link indices for each word

        // insert each word in the tree
        for(int i=0; i<childrenNum; i++){
            art.insert(word[i], linkIndices[i]);
        }

        // verify that the node is a Node16 (searching for the node that contains any of the previously inserted words)
        assertInstanceOf(Node16.class, art.findNode(prefix));
    }

    @Test
    public void testRootNode16UpgradeToNode48(){
        final int childrenNum = 17; // 17 to exceed the children size of Node16 and make it upgrade to Node48
        String prefix = "";
        String[] words = generateWordsWithSharedPrefix(prefix, childrenNum); // generate single letter words, so they stay on the same node (root)
        long[] linkIndices = generateLinkIndices(childrenNum); // generate linear link indices for each word

        // insert each word in the tree
        for(int i=0; i<childrenNum; i++){
            art.insert(words[i], linkIndices[i]);
        }

        // verify that the root node is a Node48 (searching for the node that contains any of the previously inserted words)
        assertInstanceOf(Node48.class, art.findNode(prefix));
    }

    @Test
    public void testNode16UpgradeToNode48(){
        final int childrenNum = 17; // 17 to exceed the children size of Node16 and make it upgrade to Node48
        String prefix = "a";
        String[] word = generateWordsWithSharedPrefix(prefix, childrenNum); // generate words bigger that have more than one letter, so they don't stay on the root (in this case 2 letter words)
        long[] linkIndices = generateLinkIndices(childrenNum); // generate linear link indices for each word

        // insert each word in the tree
        for(int i=0; i<childrenNum; i++){
            art.insert(word[i], linkIndices[i]);
        }

        // verify that the node is a Node48 (searching for the node that contains any of the previously inserted words)
        assertInstanceOf(Node48.class, art.findNode(prefix));
    }

    @Test
    public void testRootNode48UpgradeToNode256(){
        final int childrenNum = 49; // 49 to exceed the children size of Node48 and make it upgrade to Node256
        String prefix = "";
        String[] words = generateWordsWithSharedPrefix(prefix, childrenNum); // generate single letter words, so they stay on the same node (root)
        long[] linkIndices = generateLinkIndices(childrenNum); // generate linear link indices for each word

        // insert each word in the tree
        for(int i=0; i<childrenNum; i++){
            art.insert(words[i], linkIndices[i]);
        }

        // verify that the root node is a Node256 (searching for the node that contains any of the previously inserted words)
        assertInstanceOf(Node256.class, art.findNode(prefix));
    }

    @Test
    public void testNode48UpgradeToNode256(){
        final int childrenNum = 49; // 49 to exceed the children size of Node48 and make it upgrade to Node256
        String prefix = "a";
        String[] word = generateWordsWithSharedPrefix(prefix, childrenNum); // generate words bigger that have more than one letter, so they don't stay on the root (in this case 2 letter words)
        long[] linkIndices = generateLinkIndices(childrenNum); // generate linear link indices for each word

        // insert each word in the tree
        for(int i=0; i<childrenNum; i++){
            art.insert(word[i], linkIndices[i]);
        }

        // verify that the node is a Node256 (searching for the node that contains any of the previously inserted words)
        assertInstanceOf(Node256.class, art.findNode(prefix));
    }


    /* EDGE CASES TESTS */
    @Test
    public void testInsertDuplicateWordDifferentLinkIndex(){
        String word = "duplicate";
        long firstLinkIndex = 1L;
        long secondLinkIndex = 2L;

        // insert word once
        art.insert(word, firstLinkIndex);
        // get it's link indices
        ArrayList<Long> firstInsertionLinkIndices = new ArrayList<>(art.find(word)); // shallow copy as to not point to the object

        // insert word again but with a different link index
        art.insert(word, secondLinkIndex);
        // get the updated link indices
        ArrayList<Long> secondInsertionLinkIndices = art.find(word);

        assertNotNull(firstInsertionLinkIndices, "First insertion link indices should not be null.");
        assertNotNull(secondInsertionLinkIndices, "Second insertion link indices should not be null.");
        assertEquals(1, firstInsertionLinkIndices.size(), "After first insertion, link indices array should contain one element.");
        assertEquals(2, secondInsertionLinkIndices.size(), "After second insertion, link indices array should contain two elements.");
        assertTrue(secondInsertionLinkIndices.containsAll(firstInsertionLinkIndices), "Second insertion link indices should include all elements of the first.");
        assertTrue(secondInsertionLinkIndices.contains(secondLinkIndex), "Second insertion link indices should contain the new link index.");
    }

    @Test
    public void testInsertDuplicateWordSameLinkIndex(){
        String word = "duplicate";
        long linkIndex = 1L;

        // insert word once
        art.insert(word, linkIndex);
        // get it's link indices
        ArrayList<Long> firstInsertionLinkIndices = new ArrayList<>(art.find(word)); // shallow copy as to not point to the object

        // insert word again but with a different link index
        art.insert(word, linkIndex);
        // get the updated link indices
        ArrayList<Long> secondInsertionLinkIndices = art.find(word);

        assertNotNull(firstInsertionLinkIndices, "First insertion link indices should not be null.");
        assertNotNull(secondInsertionLinkIndices, "Second insertion link indices should not be null.");
        assertEquals(1, firstInsertionLinkIndices.size(), "After first insertion, link indices array should contain one element.");
        assertEquals(1, secondInsertionLinkIndices.size(), "After second insertion, link indices array should contain one element.");
        assertTrue(secondInsertionLinkIndices.containsAll(firstInsertionLinkIndices), "Second insertion link indices should include all elements of the first.");
        assertTrue(secondInsertionLinkIndices.contains(linkIndex), "Second insertion link indices should contain the same link index.");
    }

    @Test
    public void testInsertVeryLongWords(){
        // insert 10 very long words, each one bigger than the one before
        for(int i=0; i<10; i++){
            StringBuilder word = new StringBuilder();;
            int wordSize = (i+1)*128;
            long linkIndex = (long)wordSize; // link index is the size of the word
            for(int j=0; j<wordSize; j++){ // build the word
                word.append((char) ('a' + j % ('z' - 'a')));
            }
            art.insert(String.valueOf(word), linkIndex); // insert the word
            ArrayList<Long> result =art.find(String.valueOf(word)); // get the link index of the inserted word

            assertNotNull(result, "Link index should not be null.");
            assertEquals(1, result.size(), "Link index should contain one element.");
            assertTrue(result.contains(linkIndex), "Link indices should contain the inserted link index.");
        }
    }

    /* FINAL WORD TESTS */
    @Test
    public void testFinalWordFlag(){
        String prefixWord = "t1";
        long prefixLinkIndex = 1L;
        String fullWord = "t2";
        long fullLinkIndex = 2L;

        // insert prefix and full word
        art.insert(prefixWord, prefixLinkIndex);
        art.insert(fullWord, fullLinkIndex);

        // get both nodes
        Node prefixNode = art.findNode(prefixWord);
        Node fullNode = art.findNode(fullWord);

        assertTrue(prefixNode.isFinalWord, "The prefix node should be a final word.");
        assertTrue(fullNode.isFinalWord, "The full node should be a final word.");
    }

    /* EXCEPTION HANDLING TESTS */
    @Test
    public void testInsertNullWord(){
        assertThrows(NullPointerException.class, () -> art.insert(null, 1L));
    }

    @Test
    public void testFindNullWord(){
        assertThrows(NullPointerException.class, () -> art.find(null));
    }

    @Test
    public void testFindNodeNullWord(){
        assertThrows(NullPointerException.class, () -> art.findNode(null));
    }

    @Test
    public void testInsertNegativeLinkIndex(){
        assertThrows(IllegalArgumentException.class, () -> art.insert("test", -1L));
    }

    @Test
    public void testInsertBMP() {
        // define the start and end of the BMP Unicode range
        int start = 0x0000;
        int end = 0xFFFF; // last code point in the BMP

        for (int codePoint = start; codePoint <= end; codePoint++) {
            try {
                // convert to unicode character
                String key = new String(Character.toChars(codePoint));

                art.insert(key, (long) codePoint);
            } catch (Exception e) {
                // fail test if insertion fails
                fail("Exception thrown at code point " + codePoint + ": " + e.getMessage());
            }
        }
    }

}
