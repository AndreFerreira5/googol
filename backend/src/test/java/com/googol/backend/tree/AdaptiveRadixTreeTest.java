package com.googol.backend.tree;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.engine.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import com.googol.backend.tree.*;

/**
 * The type Adaptive radix tree test.
 */
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

    /**
     * Art setup.
     */
    @BeforeEach
    public void ARTSetup(){
        art = new AdaptiveRadixTree();
    }


    /**
     * Test insert and find single word.
     */
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

    /**
     * Test find non existing word.
     */
    @Test
    public void testFindNonExistingWord() {
        String word = "nonexistent";
        // attempt to find the non existent word
        ArrayList<Long> result = art.find(word);

        // verify the result contains the correct link index
        assertNull(result, "The result should be null for a non existing word");
    }

    /**
     * Test root node 4 upgrade to node 16.
     */
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

    /**
     * Test node 4 upgrade to node 16.
     */
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

    /**
     * Test root node 16 upgrade to node 48.
     */
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

    /**
     * Test node 16 upgrade to node 48.
     */
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

    /**
     * Test root node 48 upgrade to node 256.
     */
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

    /**
     * Test node 48 upgrade to node 256.
     */
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


    /**
     * Test insert duplicate word different link index.
     */
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

    /**
     * Test insert duplicate word same link index.
     */
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

    /**
     * Test insert very long words.
     */
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

    /**
     * Test final word flag.
     */
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

        assertTrue(prefixNode.getIsFinalWord(), "The prefix node should be a final word.");
        assertTrue(fullNode.getIsFinalWord(), "The full node should be a final word.");
    }

    /**
     * Test insert null word.
     */
    /* EXCEPTION HANDLING TESTS */
    @Test
    public void testInsertNullWord(){
        assertThrows(NullPointerException.class, () -> art.insert(null, 1L));
    }

    /**
     * Test find null word.
     */
    @Test
    public void testFindNullWord(){
        assertThrows(NullPointerException.class, () -> art.find(null));
    }

    /**
     * Test find node null word.
     */
    @Test
    public void testFindNodeNullWord(){
        assertThrows(NullPointerException.class, () -> art.findNode(null));
    }

    /**
     * Test insert negative link index.
     */
    @Test
    public void testInsertNegativeLinkIndex(){
        assertThrows(IllegalArgumentException.class, () -> art.insert("test", -1L));
    }

    /**
     * Test insert bmp.
     */
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


    /**
     * Test export import empty tree.
     *
     * @throws IOException the io exception
     */
    /* IMPORT + EXPORT TESTS */
    @Test
    void testExportImportEmptyTree() throws IOException {
        AdaptiveRadixTree art = new AdaptiveRadixTree();
        art.setFilename("testExportImportEmptyTree.bin");
        art.exportART();
        art.importART();
        assertNull(art.find("anyWord"), "Imported tree should be empty and return null for any search.");
    }

    /**
     * Test export import single entry.
     *
     * @throws IOException the io exception
     */
    @Test
    void testExportImportSingleEntry() throws IOException {
        AdaptiveRadixTree art = new AdaptiveRadixTree();
        art.setFilename("testExportImportSingleEntry.bin");
        String testWord = "hello";
        long linkIndex = 12345L;
        art.insert(testWord, linkIndex);
        art.exportART();

        AdaptiveRadixTree importedArt = new AdaptiveRadixTree();
        importedArt.setFilename("testExportImportSingleEntry.bin");
        importedArt.importART();
        ArrayList<Long> linkIndices = importedArt.find(testWord);
        assertNotNull(linkIndices, "Imported tree should contain the inserted word.");
        assertTrue(linkIndices.contains(linkIndex), "Imported tree should contain the correct link index for the inserted word.");
    }

    /**
     * Test export import complex tree.
     *
     * @throws IOException the io exception
     */
    @Test
    void testExportImportComplexTree() throws IOException {
        AdaptiveRadixTree art = new AdaptiveRadixTree();
        art.setFilename("testExportImportComplexTree.bin");
        art.insert("hello", 1);
        art.insert("world", 2);
        art.insert("hell", 3);
        art.insert("word", 4);
        art.exportART();

        AdaptiveRadixTree importedArt = new AdaptiveRadixTree();
        importedArt.setFilename("testExportImportComplexTree.bin");
        importedArt.importART();
        assertNotNull(importedArt.find("hello"), "Imported tree should contain 'hello'.");
        assertNotNull(importedArt.find("world"), "Imported tree should contain 'world'.");
        assertNull(importedArt.find("notexist"), "Imported tree should not contain 'notexist'.");
    }

    /**
     * Test export import preserves is final word.
     *
     * @throws IOException the io exception
     */
    @Test
    void testExportImportPreservesIsFinalWord() throws IOException {
        AdaptiveRadixTree art = new AdaptiveRadixTree();
        art.setFilename("testExportImportPreservesIsFinalWord.bin");
        art.insert("hello", 1);
        art.exportART();

        AdaptiveRadixTree importedArt = new AdaptiveRadixTree();
        importedArt.setFilename("testExportImportPreservesIsFinalWord.bin");
        importedArt.importART();
        Node node = importedArt.findNode("hello");
        assertNotNull(node, "Imported tree should contain 'hello'.");
        assertTrue(node.getIsFinalWord(), "'hello' should be marked as a final word in the imported tree.");
    }

    /**
     * Test export import preserves link indices.
     *
     * @throws IOException the io exception
     */
    @Test
    void testExportImportPreservesLinkIndices() throws IOException {
        AdaptiveRadixTree art = new AdaptiveRadixTree();
        art.setFilename("testExportImportPreservesLinkIndices.bin");
        art.insert("hello", 1);
        art.insert("hello", 2);
        art.exportART();

        AdaptiveRadixTree importedArt = new AdaptiveRadixTree();
        importedArt.setFilename("testExportImportPreservesLinkIndices.bin");
        importedArt.importART();
        ArrayList<Long> linkIndices = importedArt.find("hello");
        assertNotNull(linkIndices, "Imported tree should contain 'hello'.");
        assertTrue(linkIndices.contains(1L) && linkIndices.contains(2L), "'hello' should have link indices 1 and 2 in the imported tree.");
    }

    /**
     * Test file not found.
     */
    @Test
    void testFileNotFound() {
        AdaptiveRadixTree art = new AdaptiveRadixTree();
        art.setFilename("nonExistentFile.bin");
        Exception exception = assertThrows(IOException.class, art::importART);
    }

    /**
     * Test export import diverse large tree.
     *
     * @throws IOException the io exception
     */
    @Test
    void testExportImportDiverseLargeTree() throws IOException {
        int totalEntries = 10000; // Total number of entries to insert into the tree
        int maxIndices = 500;
        Map<String, List<Long>> generatedKeyIndexMap = new HashMap<>();

        while (generatedKeyIndexMap.size() < totalEntries) {
            // generate a random string that's unlikely to share a prefix with others
            String key = generateRandomString(10) + generatedKeyIndexMap.size();
            List<Long> linkIndices = new ArrayList<>();

            int numberOfIndices = 1 + (int) (Math.random() * maxIndices);

            for (int i = 0; i < numberOfIndices; i++) {
                // For simplicity, just use size of map to ensure uniqueness
                long linkIndex = generatedKeyIndexMap.size() * 10L + i;

                // Optionally, share some link indices between words
                if (!generatedKeyIndexMap.isEmpty() && i == 0) { // For example, share the first index with the previous word
                    String previousKey = generateRandomString(10) + (generatedKeyIndexMap.size() - 1);
                    if (generatedKeyIndexMap.containsKey(previousKey)) { // Ensure the previous key exists in the map
                        linkIndices.add(generatedKeyIndexMap.get(previousKey).get(0));
                    } else {
                        // Previous key does not exist, handle accordingly, maybe log a warning or add a new index
                        long newLinkIndex = generatedKeyIndexMap.size() * 10L + i;
                        linkIndices.add(newLinkIndex);
                        art.insert(key, newLinkIndex);
                    }
                } else {
                    long newLinkIndex = generatedKeyIndexMap.size() * 10L + i;
                    linkIndices.add(newLinkIndex);
                    art.insert(key, newLinkIndex);
                }

                art.insert(key, linkIndex); // Insert each link index
            }

            generatedKeyIndexMap.put(key, linkIndices);
        }

        art.setFilename("diverseLargeTreeExport.bin");
        art.exportART();

        AdaptiveRadixTree importedArt = new AdaptiveRadixTree();
        importedArt.setFilename("diverseLargeTreeExport.bin");
        importedArt.importART();

        for (Map.Entry<String, List<Long>> entry : generatedKeyIndexMap.entrySet()) {
            String key = entry.getKey();
            List<Long> expectedIndices = entry.getValue();
            ArrayList<Long> actualIndices = importedArt.find(key);

            assertNotNull(actualIndices, "Imported tree should contain the key: " + key);
            for (Long expectedIndex : expectedIndices) {
                assertTrue(actualIndices.contains(expectedIndex), "Link indices for key " + key + " should contain the correct value " + expectedIndex);
            }
        }

        deleteFile("diverseLargeTreeExport.bin");
    }


    private String generateRandomString(int length) {
        String alphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + "0123456789"
                + "abcdefghijklmnopqrstuvxyz";
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            // Generate a random number between 0 to alphaNumericString variable length
            int index = (int)(alphaNumericString.length() * Math.random());

            // Append the character at the randomly generated index to the StringBuilder
            sb.append(alphaNumericString.charAt(index));
        }

        return sb.toString();
    }

    private static String generateConsistentString(int length) {
        String alphaNumericString = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                + "0123456789"
                + "abcdefghijklmnopqrstuvxyz";
        StringBuilder sb = new StringBuilder(length);

        for (int i = 0; i < length; i++) {
            // Use counter to get the next character in a cyclic manner from ALPHA_NUMERIC_STRING
            char ch = alphaNumericString.charAt(i % alphaNumericString.length());
            sb.append(ch);
        }

        return sb.toString();
    }


    /**
     * Cleanup.
     */
    @AfterAll
    public static void cleanup(){
        deleteFile("testExportImportEmptyTree.bin");
        deleteFile("testExportImportSingleEntry.bin");
        deleteFile("testExportImportComplexTree.bin");
        deleteFile("testExportImportPreservesIsFinalWord.bin");
        deleteFile("testExportImportPreservesLinkIndices.bin");
        deleteFile("nonExistentFile.bin");
    }


    private static void deleteFile(String filename) {
        try {
            Files.deleteIfExists(Paths.get(filename));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
