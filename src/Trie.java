class TrieNode{
    int code;
    TrieNode[] children;
    boolean isEndOfWord;
    int[] linkIndices;

    TrieNode(){
        code = -1;
        children = new TrieNode[26];
        isEndOfWord = false;
        linkIndices = new int[0];
    }
}

public class Trie {
    private final TrieNode root;

    public Trie(){
        root = new TrieNode();
    }

    public void insert(CharSequence word, int linkIndex){
        TrieNode node = root;
        int len = word.length();
        for(int i=0; i<len; i++){
            int c = compact(word.charAt(i));
            if(node.children[c]==null) node.children[c] = new TrieNode();
            node = node.children[c];
        }
        node.isEndOfWord = true;
        node.linkIndices = insertIndex(node.linkIndices, linkIndex);
    }

    public boolean search(CharSequence word){
        TrieNode node = searchPrefix(word);
        return node!=null && node.isEndOfWord;
    }

    private TrieNode searchPrefix(CharSequence word) {
        TrieNode node = root;
        int len = word.length();
        for (int i = 0; i < len; i++) {
            int c = compact(word.charAt(i));
            if (node.children[c] == null) return null;
            node = node.children[c];
        }
        return node;
    }

    public int[] getLinkIndices(CharSequence word) {
        TrieNode node = searchPrefix(word);
        return node != null && node.isEndOfWord ? node.linkIndices : new int[0];
    }

    private int compact(char c) {
        return c - 'a';
    }

    private int[] insertIndex(int[] arr, int index) {
        int[] newArr = new int[arr.length + 1];
        System.arraycopy(arr, 0, newArr, 0, arr.length);
        newArr[arr.length] = index;
        return newArr;
    }
}
