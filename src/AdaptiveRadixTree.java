import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.nio.ByteOrder;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * InsertResult class. Has the sole purpose of returning the upgraded node in case of a node upgrade so the current node can be replaced
 * with the upgraded one. This is needed because the node can't upgrade itself, so it must be done from "outside".
 */
class InsertResult{
    /**
     * The current node, that will be replaced by the upgraded node in case of an upgrade
     */
    public Node node;

    /**
     * The upgraded node, in case the upgrade has happened, otherwise it is null
     */
    public Node upgradedNode;

    /**
     * The node write lock in case the upgrade has happened, so the node can be replaced while the write lock is held
     * so no changes are made to the node while it is being replaced
     */
    public ReadWriteLock lock;

    /**
     * Instantiates a new Insert result.
     *
     * @param node         the current node
     * @param upgradedNode the upgraded node
     * @param lock         the write lock of the current node
     */
    public InsertResult(Node node, Node upgradedNode, ReadWriteLock lock){
        this.node = node;
        this.upgradedNode = upgradedNode;
        this.lock = lock;
    }
}

/**
 * Node class.
 */
abstract class Node{
    /**
     * Count that keeps track of the number of children
     */
    protected int count = 0; // to keep track of the number of children

    /**
     * Array that stores the link indices of the url where this word appears
     */
    protected ArrayList<Long> linkIndices = new ArrayList<>();

    /**
     * Insert byte into the tree.
     *
     * @param key the key
     * @return the insert result
     */
    abstract InsertResult insert(byte key);

    /**
     * Insert a node into the tree, with the given key.
     *
     * @param key   the key
     * @param child the child
     */
    abstract void insert(byte key, Node child);

    /**
     * Find node for the given key.
     *
     * @param key the key
     * @return the node
     */
    abstract Node find(byte key);

    /**
     * Flag that indicates if the node is a final word.
     */
    boolean isFinalWord = false;
    /**
     * Reentrant Read Write Lock to allow multiple reads but only one write at a time.
     */
    protected ReadWriteLock lock = new ReentrantReadWriteLock();


    /**
     * Update node reference. Replace a node with the upgraded one.
     *
     * @param key          the key
     * @param upgradedNode the upgraded node
     */
    abstract void updateNodeReference(byte key, Node upgradedNode);

    /**
     * Get keys.
     *
     * @return byte [ ]
     */
    abstract byte[] getKeys();

    /**
     * Set keys.
     *
     * @param keyIndex the key index
     */
    abstract void setKeys(byte[] keyIndex);

    /**
     * Get children.
     *
     * @return array of node children
     */
    abstract Node[] getChildren();

    /**
     * Set children.
     *
     * @param children the children
     */
    abstract void setChildren(Node[] children);

    /**
     * Is valid link index.
     *
     * @param linkIndex the link index
     * @return true or false
     */
    protected boolean isValidLinkIndex(long linkIndex){
        return linkIndex>=0;
    }

    /**
     * Add link index.
     *
     * @param linkIndex the link index
     */
    void addLinkIndex(long linkIndex){
        if(!isValidLinkIndex(linkIndex)) return; // if link index is not valid return
        Long longObjIndex = linkIndex; // cast long to Long
        if(longObjIndex == null) return; // if longObjIndex is null return

        lock.writeLock().lock();
        try{
            linkIndices.add(longObjIndex);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Set link indices.
     *
     * @param linkIndices link indices
     */
    void setLinkIndices(ArrayList<Long> linkIndices){
        if(linkIndices == null) return;
        lock.writeLock().lock();
        this.linkIndices = linkIndices;
        lock.writeLock().unlock();
    }

    /**
     * Get link indices array list.
     *
     * @return array list of link indices
     */
    ArrayList<Long> getLinkIndices(){
        lock.readLock().lock();
        try{
            return new ArrayList<>(this.linkIndices);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Set is final word.
     *
     * @param isFinalWord is final word flag
     */
    void setIsFinalWord(boolean isFinalWord){
        if(isFinalWord == this.isFinalWord) return;

        lock.writeLock().lock();
        this.isFinalWord = isFinalWord;
        lock.writeLock().unlock();
    }

    /**
     * Get is final word.
     *
     * @return is final word
     */
    boolean getIsFinalWord(){
        lock.readLock().lock();
        try{
            return this.isFinalWord;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Set count.
     *
     * @param count count
     */
    void setCount(int count){
        if(count < 0 || count == this.count) return; // if count is not valid return
        lock.writeLock().lock();
        this.count = count;
        lock.writeLock().unlock();
    }

    /**
     * Increment count.
     */
    void incrementCount(){
        lock.writeLock().lock();
        this.count++;
        lock.writeLock().unlock();
    }

    /**
     * Decrement count.
     */
    void decrementCount(){
        lock.writeLock().lock();
        this.count--;
        lock.writeLock().unlock();
    }

    /**
     * Get count.
     *
     * @return int
     */
    int getCount(){
        lock.readLock().lock();
        try{
            return this.count;
        } finally {
            lock.readLock().unlock();
        }
    }
}


/**
 * NODE4.
 */
class Node4 extends Node {
    /**
     * 4 position byte array of keys
     */
    private byte[] keys = new byte[4];
    /**
     * 4 position Node array of children
     */
    private Node[] children = new Node[4];


    /**
     * Upgrade from Node4 to Node16
     * @return upgraded node (Node16)
     */
    private Node16 upgradeToNode16(){
        Node16 newNode = new Node16(); // create a new Node16
        for(int i = 0; i < 4; i++){ // insert all the keys and children
            newNode.insert(keys[i], children[i]);
        }
        newNode.setLinkIndices(this.linkIndices); // set the link indices
        if (this.isFinalWord) newNode.setIsFinalWord(true); // set the final word flag
        return newNode; // return upgraded node
    }


    @Override
    InsertResult insert(byte key) {
        lock.writeLock().lock();
        // if the node has reached its maximum number of children (4)
        if (count == 4) {
            // if the key already exists, no work is needed so just return
            if(this.find(key) != null){
                lock.writeLock().unlock();
                return new InsertResult(null, null, null);
            }

            // upgrade node to Node16 and insert the new link index
            Node16 upgradedNode = upgradeToNode16();
            Node insertedChild = upgradedNode.insert(key).node;

            // return new upgraded node with the locked write lock, so that it keeps locking so the upgraded node can replace the older one (unlocking the lock afterward)
            return new InsertResult(insertedChild, upgradedNode, lock);
        }

        /* if there is space available on the node and there isn't a child with the same key*/
        keys[count] = key; // insert the new key and child into the new node
        children[count] = new Node4(); // insert the new node into the children array
        count++;

        lock.writeLock().unlock(); // unlock write lock
        return new InsertResult(children[count - 1], null, null);
    }


    // TODO maybe change these inserts to upgrade the node instead of just throwing an exception (right now it's not a problem, but it may become one in the future)
    void insert(byte key, Node child) {
        lock.writeLock().lock();
        try {
            // if the key already exists, replace the respective child with the provided one
            if(this.find(key) != null){
                updateNodeReference(key, child);
                return;
            }

            // if there's space available on the node
            if (count < 4) {
                keys[count] = key;
                children[count] = child;
                count++;
            } else {
                throw new IllegalStateException("Node4 is full and cannot add more children.");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }


    void updateNodeReference(byte key, Node upgradedNode){
        for(int i = 0; i < count; i++){
            if(keys[i] == key){
                children[i] = upgradedNode;
                return;
            }
        }
    }


    /**
     * Find node for the given key using linear search.
     * @param key the key
     * @return node
     */
    @Override
    Node find(byte key){
        lock.readLock().lock();
        try {
            // iterate through the node's existing children
            for (int i = 0; i < count; i++) {
                if (keys[i] == key) { // if node found
                    return children[i];
                }
            }
            return null; // if node not found
        } finally {
            lock.readLock().unlock();
        }
    }


    @Override
    byte[] getKeys(){
        lock.readLock().lock();
        try{
            return keys;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    void setKeys(byte[] keys){
        lock.writeLock().lock();
        try{
            this.keys = keys;
        } finally {
            lock.writeLock().unlock();
        }
    }


    @Override
    Node[] getChildren(){
        lock.readLock().lock();
        try{
            return children;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    void setChildren(Node[] children){
        lock.writeLock().lock();
        try{
            this.children = children;
        } finally {
            lock.writeLock().unlock();
        }
    }
}


/**
 * NODE16.
 */
// TODO change linear search to binary search in this type of node (the keys and node arrays should be sorted with each insertion, so the binary search works)
class Node16 extends Node {
    /**
     * 16 position byte array of keys
     */
    private byte[] keys = new byte[16];
    /**
     * 16 position Node array of children
     */
    private Node[] children = new Node[16];


    /**
     * Upgrade from Node16 to Node48
     * @return upgraded node (Node48)
     */
    private Node48 upgradeToNode48() {
        Node48 newNode = new Node48(); // create a new Node48
        for (int i = 0; i < 16; i++) { // insert all the keys and children
            newNode.insert(keys[i], children[i]);
        }

        newNode.setLinkIndices(this.linkIndices); // set the link indices
        if (this.isFinalWord) newNode.setIsFinalWord(true); // set the final word flag
        return newNode; // return upgraded node
    }


    @Override
    InsertResult insert(byte key) {
        lock.writeLock().lock();
        // check if the node is full and needs to be upgraded to Node48
        if (count == 16) {
            // if the key already exists, no work is needed so just return
            if(this.find(key) != null){
                lock.writeLock().unlock();
                return new InsertResult(null, null, null);
            }

            // upgrade to Node48 and insert the new child
            Node48 upgradedNode = upgradeToNode48();
            Node insertedChild = upgradedNode.insert(key).node;

            // return new upgraded node with the locked write lock, so that it keeps locking so the upgraded node can replace the older one (unlocking the lock afterward)
            return new InsertResult(insertedChild, upgradedNode, lock);
        }


        // insert the new key and child
        keys[count] = key;
        children[count] = new Node4();
        count++;

        lock.writeLock().unlock();
        return new InsertResult(children[count - 1], null, null);
    }


    void insert(byte key, Node child){
        lock.writeLock().lock();
        try{
            // if the key already exists, replace the respective child with the provided one
            if(this.find(key) != null){
                updateNodeReference(key, child);
                return;
            }

            // if there's space available on the node
            if (count < 16) {
                keys[count] = key;
                children[count] = child;
                count++;
            } else {
                throw new IllegalStateException("Node16 is full and cannot add more children.");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }


    /**
     * Find node for the given key using linear search (should be binary search, but it is not yet implemented).
     * @param key the key
     * @return node
     */
    @Override
    Node find(byte key) {
        lock.readLock().lock();
        try {
            for (int i = 0; i < count; i++) {
                if (keys[i] == key) {
                    return children[i];
                }
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    void updateNodeReference(byte key, Node upgradedNode){
        for (int i = 0; i < count; i++) {
            if (keys[i] == key) {
                children[i] = upgradedNode;
                return;
            }
        }
    }


    @Override
    byte[] getKeys(){
        lock.readLock().lock();
        try{
            return keys;
        } finally {
            lock.readLock().unlock();
        }
    }


    @Override
    void setKeys(byte[] keys){
        lock.writeLock().lock();
        try{
            this.keys = keys;
        } finally {
            lock.writeLock().unlock();
        }
    }


    @Override
    Node[] getChildren(){
        lock.readLock().lock();
        try{
            return children;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    void setChildren(Node[] children) {
        lock.writeLock().lock();
        try {
            this.children = children;
        } finally {
            lock.writeLock().unlock();
        }
    }
}


/**
 * NODE48
 */
class Node48 extends Node {
    /**
     * 256 positions byte array of key indexes
     */
    private byte[] keyIndex = new byte[256]; // maps key bytes to child indices, initialized to a sentinel value
    /**
     * 48 position Node array of children
     */
    private Node[] children = new Node[48];

    /**
     * Instantiates a new Node 48.
     */
    public Node48() {
        // initialize index array to indicate no child for all keys
        Arrays.fill(keyIndex, (byte) -1);
    }


    /**
     * Upgrade from Node48 to Node256
     * @return upgraded node (Node256)
     */
    private Node256 upgradeToNode256() {
        Node256 newNode = new Node256(); // create a new Node256
        // migrate children to the new Node256
        for (int i = 0; i < 256; i++) {
            if (keyIndex[i] != -1) { // if there's a child for this key
                newNode.insert((byte) i, children[keyIndex[i]]);
            }
        }

        newNode.setLinkIndices(this.linkIndices); // set the link indices
        if (this.isFinalWord) newNode.setIsFinalWord(true); // set the final word flag
        return newNode; // return upgraded node
    }


    @Override
    InsertResult insert(byte key) {
        lock.writeLock().lock();

        // check if the node needs to be upgraded to Node256
        if (count == 48) {
            // if the key already exists, no work is needed so just return
            if(this.find(key) != null){
                lock.writeLock().unlock();
                return new InsertResult(null, null, null);
            }

            Node256 upgradedNode = upgradeToNode256();
            Node insertedChild = upgradedNode.insert(key).node;

            // return new upgraded node with the locked write lock, so that it keeps locking so the upgraded node can replace the older one (unlocking the lock afterward)
            return new InsertResult(insertedChild, upgradedNode, lock);
        }

        int unsignedKey = Byte.toUnsignedInt(key);
        keyIndex[unsignedKey] = (byte) count;
        children[count] = new Node4();
        count++;

        lock.writeLock().unlock();
        return new InsertResult(children[count - 1], null, null);

    }

    void insert(byte key, Node child){
        lock.writeLock().lock();
        try{
            // if the key already exists, replace the respective child with the provided one
            if(this.find(key) != null){
                updateNodeReference(key, child);
                return;
            }

            if (count < 48) {
                int unsignedKey = Byte.toUnsignedInt(key);
                keyIndex[unsignedKey] = (byte) count;
                children[count] = child;
                count++;
            } else {
                throw new IllegalStateException("Node48 is full and cannot add more children.");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }


    /**
     * Find node for the given key using a two-step search. First it directly accesses the keyIndex array
     * with the key byte and gets the index, and secondly it accesses the children array in the previously obtained index .
     * @param key the key
     * @return node
     */
    @Override
    Node find(byte key){
        lock.readLock().lock();

        try {
            int unsignedKey = Byte.toUnsignedInt(key);
            if (keyIndex[unsignedKey] != -1) {
                return children[keyIndex[unsignedKey]];
            }
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }


    void updateNodeReference(byte key, Node upgradedNode){
        int unsignedKey = Byte.toUnsignedInt(key);
        children[keyIndex[unsignedKey]] = upgradedNode;
    }


    @Override
    byte[] getKeys(){
        lock.readLock().lock();
        try{
            return keyIndex;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    void setKeys(byte[] keyIndex){
        lock.writeLock().lock();
        try{
            this.keyIndex = keyIndex;
        } finally {
            lock.writeLock().unlock();
        }
    }


    @Override
    Node[] getChildren(){
        lock.readLock().lock();
        try{
            return children;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    void setChildren(Node[] children) {
        lock.writeLock().lock();
        try{
            this.children = children;
        } finally {
            lock.writeLock().unlock();
        }
    }
}


/**
 * NODE256
 */
class Node256 extends Node {
    /**
     * 256 position Node array of children
     */
    private Node[] children = new Node[256];

    /**
     * Instantiates a new Node 256.
     */
    public Node256() {
        Arrays.fill(children, null); // Initialize all children to null
    }

    @Override
    InsertResult insert(byte key) {
        lock.writeLock().lock();

        try {
            if (count == 256) {
                // if the key already exists, no work is needed so just return
                if(this.find(key) != null) return new InsertResult(null, null, null);

                throw new IllegalStateException("Node256 is full and cannot add more children.");
            }

            int unsignedKey = Byte.toUnsignedInt(key);

            // Insert the child if it's not already present
            if (children[unsignedKey] == null) {
                children[unsignedKey] = new Node4();
                count++;
            }
            return new InsertResult(children[unsignedKey], null, null);
        } finally {
            lock.writeLock().unlock();
        }
    }

    void insert(byte key, Node child){
        lock.writeLock().lock();
        try{
            // if the key already exists, replace the respective child with the provided one
            if(this.find(key) != null){
                updateNodeReference(key, child);
                return;
            }

            if (count < 256) {
                int unsignedKey = Byte.toUnsignedInt(key);
                children[unsignedKey] = child;
                count++;
            } else {
                throw new IllegalStateException("Node256 is full and cannot add more children.");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Find node for the given key using that key as index to directly access the children array.
     * @param key the key
     * @return node
     */
    @Override
    Node find(byte key) {
        lock.readLock().lock();
        try{
            int unsignedKey = Byte.toUnsignedInt(key);
            return children[unsignedKey];
        } finally {
            lock.readLock().unlock();
        }
    }



    void updateNodeReference(byte key, Node upgradedNode){
        int unsignedKey = Byte.toUnsignedInt(key);
        children[unsignedKey] = upgradedNode;
    }


    @Override
    byte[] getKeys() {
        lock.readLock().lock();
        try {
            ArrayList<Byte> keyList = new ArrayList<>();
            for (int i = 0; i < children.length; i++) {
                if (children[i] != null) {
                    // since `i` is an int, we need to cast it to byte before adding to the list
                    // this cast is safe because the keys in Node256 are essentially byte values
                    keyList.add((byte) i);
                }
            }

            // convert ArrayList to byte array
            byte[] keys = new byte[keyList.size()];
            for (int i = 0; i < keyList.size(); i++) {
                keys[i] = keyList.get(i);
            }
            return keys;

        } finally {
            lock.readLock().unlock();
        }
    }


    @Override
    void setKeys(byte[] keys){
        return;
    }


    @Override
    Node[] getChildren(){
        lock.readLock().lock();
        try{
            ArrayList<Node> childList = new ArrayList<>();
            for (int i = 0; i < children.length; i++) {
                if (children[i] != null) {
                    childList.add(children[i]);
                }
            }

            Node[] trimmedChildren = new Node[childList.size()];
            for (int i = 0; i < childList.size(); i++) {
                trimmedChildren[i] = childList.get(i);
            }

            return trimmedChildren;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    void setChildren(Node[] children) {
        lock.writeLock().lock();
        try{
            this.children = children;
        } finally {
            lock.writeLock().unlock();
        }
    }
}


/**
 * <H1>Overview of the chosen binary structure of the serialized nodes</H1>
 * <pre>
 *       *********************************************************************************
 *       *                           NODE SERIALIZED STRUCTURE                           *
 *       *   x------------------------------------------------------------------------x  *
 *       *   | nodeType | childrenNum | isFinalWord | indicesNum | linkIndices | keys |  *
 *       *   x------------------------------------------------------------------------x  *
 *       *                                                                               *
 *       *   nodeType: INT (4 Bytes)                                                     *
 *       *   childrenNum: INT (4 Bytes)                                                  *
 *       *   isFinalWord: BOOLEAN (1 Byte)                                               *
 *       *   indicesNum: INT (4 Bytes)                                                   *
 *       *   linkIndices: indicesNum * LONG (indicesNum * 8 Bytes)                       *
 *       *   keys: (NODES 4, 16, 256) -> childrenNum * BYTE (childrenNum * 1 Byte)       *
 *       *         (NODE 48) -> childrenNum * (BYTE + BYTE) (childrenNum * 2 Bytes)      *
 *       *                                                                               *
 *       *********************************************************************************
 *
 * </pre>
 * <p>Each node in the ART is serialized following this structure to ensure efficient storage and retrieval</p>
 */
public class AdaptiveRadixTree {
    private Node root;
    private String filename = "art.bin"; // default filename

    private final int NODE4_TYPE = 0;
    private final int NODE16_TYPE = 1;
    private final int NODE48_TYPE = 2;
    private final int NODE256_TYPE = 3;

    /**
     * Instantiates a new Adaptive radix tree.
     */
    public AdaptiveRadixTree(){
        this.root = new Node4();
    }


    /**
     * Clear the tree.
     */
    public void clear(){
        this.root = new Node4();
    }


    /**
     * Insert a word in the tree.
     * The word is divided in bytes and each one is inserted in order.
     *
     * @param word      the word
     * @param linkIndex the link index
     */
    public void insert(String word, long linkIndex) {
        if(word == null) throw new NullPointerException("Word cannot be null.");
        if(linkIndex < 0) throw new IllegalArgumentException("Link index cannot be negative.");

        byte[] wordBytes = word.getBytes(); // get bytes from word
        Node grandfatherNode = null; // track grandfather node of currentNode to update a child in case of a node upgrade
        Node previousNode = null; // track father node of currentNode to update the linkIndices with the provided linkIndex
                                  // because when inserting a new node, the children node of that node is returned
        Node currentNode = root; // track current node
        Node nextNode = root; // dispensable node used to search for a node with a certain byte key
                              // if that node exists it is returned, otherwise null is returned
                              // this way if the node doesn't exist, only nextNode will be null, instead of currentNode being null, which is critical for this algorithm

        // for each byte of the word
        int count = 0;
        for(byte b: wordBytes){
            grandfatherNode = previousNode; // store grandfatherNode (null, if the search depth is smaller than 2)
            previousNode = currentNode; // store fatherNode (null, if the search depth is smaller than 1)

            if((nextNode = currentNode.find(b)) != null){ // if the node with the current byte key exists
                currentNode = nextNode; // assign it to currentNode
            } else { // if the node with the current byte key doesn't exist
                InsertResult insertResult = currentNode.insert(b); // insert it
                currentNode = insertResult.node; // assign it to currentNode
                if(insertResult.upgradedNode != null && insertResult.lock != null){ // if there has been a node upgrade
                    if(grandfatherNode != null){ // if the grandfatherNode exists (meaning the node we need to update is not the root)
                        // update the father node of the upgraded node, swapping the old node with the upgraded one
                        grandfatherNode.updateNodeReference(wordBytes[count-1], insertResult.upgradedNode); //wordBytes[count-1] represents the byte of the previous character in the previous node that needs to be replaced with the upgraded node
                    } else { // if the grandfatherNode is null, that means the root needs to be updated
                        this.root = insertResult.upgradedNode;
                    }

                    // update the lock after replacing the old node with the upgraded one
                    insertResult.lock.writeLock().unlock();
                }
            }
            count++;
        }
        ArrayList<Long> linkIndices = currentNode.getLinkIndices();
        if(!linkIndices.contains(linkIndex)) currentNode.addLinkIndex(linkIndex); // insert the new link Index only if it doesn't exist already
        currentNode.setIsFinalWord(true); // set node as final word
    }


    /**
     * Find a word in the tree, retrieving and returning its link indices.
     *
     * @param word the word
     * @return long array list (link indices)
     */
    public ArrayList<Long> find(String word) {
        if(word == null) throw new NullPointerException("Word cannot be null.");
        byte[] wordBytes = word.getBytes();
        Node previousNode = null;
        Node currentNode = root;
        for(byte b: wordBytes){
            previousNode = currentNode;
            if((currentNode = currentNode.find(b)) == null){
                return null;
            }
        }
        if(currentNode.isFinalWord){
            return currentNode.getLinkIndices();
        } else {
            return null;
        }
    }


    /**
     * Find the final node of the provided word and return it.
     *
     * @param word the word
     * @return the node
     */
    public Node findNode(String word){
        if(word == null) throw new NullPointerException("Word cannot be null.");
        byte[] wordBytes = word.getBytes(); // get bytes from word

        Node currentNode = root;
        for(byte b: wordBytes){
            if((currentNode = currentNode.find(b)) == null){
                return null;
            }
        }
        return currentNode;
    }


    /**
     *
     * Export the Adaptive Radix Tree from memory to disk.
     * Open the file with the set filename and call the exportNode function to recursively export
     * each node to the file
     *
     * @throws IOException thrown when failed to open the file or failed to write to it
     */
    public void exportART() throws IOException{
        int totalNodes = countNodes(root);
        ProgressTracker progressTracker = new ProgressTracker(totalNodes);

        // try to open the file
        try(RandomAccessFile artFile = new RandomAccessFile(this.filename, "rw")){
            try{
                exportNode(artFile, root, progressTracker);
            }
            catch(Exception e){
                System.out.println("ERROR EXPORTING NODE: " + e + "\nStopping the exportation...");
            }
        } catch (IOException e) {
            throw new IOException(e.getMessage());
        }
        System.out.println("TREE EXPORTED SUCCESSFULLY TO FILE: " + this.filename);
    }


    /**
     *
     * Helper function to count the total nodes from the tree.
     * Used only to keep track of the progress when exporting the tree from memory to disk.
     *
     * @param node node to start the count
     * @return number of nodes
     */
    private int countNodes(Node node){
        if(node == null) return 0;
        int count = 1;
        for(Node child: node.getChildren()){
            count += countNodes(child);
        }
        return count;
    }


    /**
     * <H1>
     * Recursive function that serializes the provided node and recursively all its children and so on.
     * </H1>
     * <p>
     * All the node attributes are get from the node, such as the type of node (4, 16, 48 or 256), the number of children,
     * if it's a final word, the number of link indices and the keys.
     * A byte buffer is created with the exact size of these node properties to later write to the file in one go.
     * <p>
     * When inserting the keys in the byte buffer, the nodes of type 4, 16 and 256 only insert the existing keys.
     * The nodes of type 48 go over the key array of size 256 and only insert when the key exists (different from -1), inserting
     * the index of the key and the key itself
     *
     * @param artFile RandomAccessFile where the tree will be exported to
     * @param node node to export
     * @param progressTracker helper class to track the progress of the exportation
     * @throws IOException thrown when failed to write to the file
     */
    private void exportNode(RandomAccessFile artFile, Node node, ProgressTracker progressTracker) throws IOException{
        // if the node is null, return
        if(node == null) return;

        // get node class
        int nodeType;
        if(node instanceof Node4) nodeType = NODE4_TYPE;
        else if(node instanceof Node16) nodeType = NODE16_TYPE;
        else if(node instanceof Node48) nodeType = NODE48_TYPE;
        else if(node instanceof Node256) nodeType = NODE256_TYPE;
        else throw new IllegalStateException("Unexpected node type. " + node.getClass().getName());

        // get node info
        int childrenNum = node.getCount();
        boolean isFinalWord = node.getIsFinalWord();
        ArrayList<Long> linkIndices = node.getLinkIndices();
        int indicesNum = 0;
        for(int i=0; i<linkIndices.size(); i++){
            if(linkIndices.get(i) != null) indicesNum++;
        }

        byte[] keys = node.getKeys();
        Node[] children = node.getChildren();

        // nodeType + childrenNum + isFinalWord + indicesNum + linkIndices + keys (only allocate for existing keys)
        byte[] bytes = new byte[Integer.BYTES + Integer.BYTES + 1 + Integer.BYTES + Long.BYTES*indicesNum + Byte.BYTES*childrenNum*(nodeType==NODE48_TYPE?2:1)];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); // little endian for x86 compatibility

        /* Put node data into the byte buffer */
        buffer.putInt(nodeType);
        buffer.putInt(childrenNum);
        buffer.put((byte) (isFinalWord ? 1 : 0));
        buffer.putInt(indicesNum);
        for(int i=0; i<linkIndices.size(); i++){
            if(linkIndices.get(i) == null) continue;
            buffer.putLong(linkIndices.get(i));
        }

        switch(nodeType){
            case NODE4_TYPE:
            case NODE16_TYPE:
                for (int i = 0; i < childrenNum; i++) {
                    buffer.put(keys[i]);
                }
                break;
            case NODE48_TYPE:
                for(int i=0; i<256; i++) {
                    if (keys[i] != -1){
                        buffer.put((byte) i);
                        buffer.put(keys[i]);
                    }
                }
                break;
            case NODE256_TYPE:
                for (byte key : keys) {
                    buffer.put(key);
                }
                break;
        }

        // write byte buffer to file
        artFile.write(bytes);

        progressTracker.incrementProcessedNodes();

        // recursively export children
        for( Node childrenNode: children){
            if(childrenNode == null) continue; // skip if for some reason the node is null
            try{
                exportNode(artFile, childrenNode, progressTracker); // recursive call to export child node
            }
            catch(Exception e){
                throw new IOException(e);
            }

        }
    }


    /**
     * <H1>Import Adaptive Radix Tree from memory (byte array)</H1>
     *
     * Used when a barrel syncs with another one and receives the exported tree file using RMI.
     *
     * @param artInMem exported tree file in memory
     * @throws IOException IO Exception in case there is a problem reading the byte array
     */
    public void importART(byte[] artInMem) throws IOException{
        try{
            DataInputStreamWithPointer artInputStream = new DataInputStreamWithPointer(artInMem);
            long fileSize = artInMem.length;
            ProgressTracker progressTracker = new ProgressTracker(fileSize);

            Node rootNode = parseNode(artInputStream);
            importNode(artInputStream, rootNode, progressTracker);

            this.root = rootNode;

            artInputStream.close();
        } catch (IOException e) {
            throw new IOException(e);
        }
        System.out.println("TREE IMPORTED SUCCESSFULLY FROM FILE: " + this.filename);
    }


    /**
     * <H1>Import Adaptive Radix Tree from a file</H1>
     *
     * Tries to open the file with the set filename and calls the recursive function importNode to recursively
     * import all the nodes in the tree.
     *
     * Used when the barrel boots up to load the tree.
     *
     * @throws IOException IO Exception in case the file is not found or reached the end unexpectedly (meaning the exportation process didn't complete or the algorithm is faulty)
     */
    public void importART() throws IOException{
        try(RandomAccessFile artFile = new RandomAccessFile(this.filename, "rw")){
            long fileSize = artFile.length();
            ProgressTracker progressTracker = new ProgressTracker(fileSize);

            Node rootNode = parseNode(artFile);
            importNode(artFile, rootNode, progressTracker);

            this.root = rootNode;
        } catch (IOException e) {
            throw new IOException(e);
        }
        System.out.println("TREE IMPORTED SUCCESSFULLY FROM FILE: " + this.filename);
    }


    /**
     *
     * <H1>Parse node from disk to memory</H1>
     * Read node properties from the file and create a new node with them.
     * Import the keys based on the node type.
     * If the node is of type 4, 16, or 256, read the keys based on the number of children
     * If the node is of type 48, read the keys indices and the keys themselves based on the number of children
     *
     * @param artFile RandomAccessFile where the node will be imported from
     * @return returns the imported node
     * @throws IOException IO Exception in case the file can't be read or reached the end unexpectedly (meaning the exportation process didn't complete or the algorithm is faulty)
     */
    private Node parseNode(RandomAccessFile artFile) throws IOException{
        // int and long byte buffers to later get int and long values from the provided file
        byte[] intBuffer = new byte[Integer.BYTES];
        byte[] longBuffer = new byte[Long.BYTES];

        // get nodeType from file
        artFile.read(intBuffer);
        int nodeType = ByteBuffer.wrap(intBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();

        // create node based on the nodeType
        Node node = switch (nodeType) {
            case NODE4_TYPE -> new Node4();
            case NODE16_TYPE -> new Node16();
            case NODE48_TYPE -> new Node48();
            case NODE256_TYPE -> new Node256();
            default -> throw new IllegalStateException("Unexpected node type: " + nodeType);
        };

        // get childrenNum from file
        artFile.read(intBuffer);
        int childrenNum = ByteBuffer.wrap(intBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();

        // get isFinalWord from file
        byte isFinalWord = artFile.readByte();

        // get indicesNum from file
        artFile.read(intBuffer);
        int indicesNum = ByteBuffer.wrap(intBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();

        // get linkIndices from file
        ArrayList<Long> linkIndices = new ArrayList<>();
        for(int i=0; i<indicesNum; i++){
            artFile.read(longBuffer);
            linkIndices.add(ByteBuffer.wrap(longBuffer).order(ByteOrder.LITTLE_ENDIAN).getLong());
        }


        switch(nodeType){
            case NODE4_TYPE:
            case NODE16_TYPE:
                // read all keys from file
                byte[] keys = new byte[childrenNum];
                artFile.read(keys);
                for (byte key : keys) {
                    node.insert(key);
                }
                break;
            case NODE48_TYPE:
                byte[] keyIndex = node.getKeys();

                for (int i = 0; i < childrenNum; i++) {
                    byte key = artFile.readByte();
                    byte childIndex = artFile.readByte();
                    keyIndex[Byte.toUnsignedInt(key)] = childIndex;
                }
                node.setKeys(keyIndex);
                node.setCount(childrenNum);

                break;
            case NODE256_TYPE:
                for (int i = 0; i < childrenNum; i++) {
                    byte childKey = artFile.readByte();
                    node.insert(childKey);
                }
                break;
        }


        // assign values to the node
        node.setLinkIndices(linkIndices);
        node.setIsFinalWord(isFinalWord != 0);

        return node;
    }


    /**
     *
     * <H1>Parse node from memory to memory</H1>
     * Read node properties from the file and create a new node with them.
     * Import the keys based on the node type.
     * If the node is of type 4, 16, or 256, read the keys based on the number of children
     * If the node is of type 48, read the keys indices and the keys themselves based on the number of children
     *
     * @param artInputStream DataInputStreamWithPointer where the node will be imported from
     * @return returns the imported node
     * @throws IOException IO Exception in case the byte array can't be read or reached the end unexpectedly (meaning the exportation process didn't complete or the algorithm is faulty)
     */
    private Node parseNode(DataInputStreamWithPointer artInputStream) throws IOException{
        // int and long byte buffers to later get int and long values from the provided file
        byte[] intBuffer = new byte[Integer.BYTES];
        byte[] longBuffer = new byte[Long.BYTES];

        // get nodeType from file
        artInputStream.readFully(intBuffer);
        int nodeType = ByteBuffer.wrap(intBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();

        // create node based on the nodeType
        Node node = switch (nodeType) {
            case NODE4_TYPE -> new Node4();
            case NODE16_TYPE -> new Node16();
            case NODE48_TYPE -> new Node48();
            case NODE256_TYPE -> new Node256();
            default -> throw new IllegalStateException("Unexpected node type: " + nodeType);
        };

        // get childrenNum from file
        artInputStream.readFully(intBuffer);
        int childrenNum = ByteBuffer.wrap(intBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();

        // get isFinalWord from file
        byte isFinalWord = artInputStream.readByte();

        // get indicesNum from file
        artInputStream.readFully(intBuffer);
        int indicesNum = ByteBuffer.wrap(intBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();

        // get linkIndices from file
        ArrayList<Long> linkIndices = new ArrayList<>();
        for(int i=0; i<indicesNum; i++){
            artInputStream.readFully(longBuffer);
            linkIndices.add(ByteBuffer.wrap(longBuffer).order(ByteOrder.LITTLE_ENDIAN).getLong());
        }


        switch(nodeType){
            case NODE4_TYPE:
            case NODE16_TYPE:
                // read all keys from file
                byte[] keys = new byte[childrenNum];
                artInputStream.readFully(keys);
                for (byte key : keys) {
                    node.insert(key);
                }
                break;
            case NODE48_TYPE:
                byte[] keyIndex = node.getKeys();

                for (int i = 0; i < childrenNum; i++) {
                    byte key = artInputStream.readByte();
                    byte childIndex = artInputStream.readByte();
                    keyIndex[Byte.toUnsignedInt(key)] = childIndex;
                }
                node.setKeys(keyIndex);
                node.setCount(childrenNum);

                break;
            case NODE256_TYPE:
                for (int i = 0; i < childrenNum; i++) {
                    byte childKey = artInputStream.readByte();
                    node.insert(childKey);
                }
                break;
        }


        // assign values to the node
        node.setLinkIndices(linkIndices);
        node.setIsFinalWord(isFinalWord != 0);

        return node;
    }


    /**
     *
     * <H1>Recursive function that de-serializes all the nodes in the provided file, building the tree recursively from the left side to the right side</H1>
     * Parse the current node and then recursively import its children, if it has any.
     *
     * @param artFile RandomAccessFile where the node will be imported from
     * @param parsedNode node to parse and import
     * @param progressTracker helper class to track the progress of the exportation
     * @throws IOException IO Exception in case the file can't be read or reached the end unexpectedly (meaning the exportation process didn't complete or the algorithm is faulty)
     */
    private void importNode(RandomAccessFile artFile, Node parsedNode, ProgressTracker progressTracker) throws IOException {
        if (parsedNode == null) return; // if for some reason the provided node is null, return

        // get the children of the parsed node to parse them
        Node[] parsedNodeChildren = parsedNode.getChildren();
        // get the keys of the parsed node
        byte[] parsedNodeKeys = parsedNode.getKeys();
        // get the number of children of the parsed node
        int parsedNodeCount = parsedNode.getCount();

        // iterate over the children of the parsed node
        for(int i=0; i<parsedNodeCount; i++){
            Node parsedChildNode = parseNode(artFile); // parse node
            parsedNodeChildren[i] = parsedChildNode; // assign the parsed child node to the children array

            progressTracker.updateProcessedBytes(artFile.getFilePointer());

            int parsedChildNodeCount = parsedChildNode.getCount();
            if(parsedChildNodeCount > 0){ // if the child node has children, import them
                importNode(artFile, parsedChildNode, progressTracker);
            }
        }

        if (parsedNode instanceof Node48){
            for(int i=0; i<parsedNodeCount; i++){
                byte key = -1;
                for(int j=0; j<parsedNodeKeys.length; j++){
                    if(parsedNodeKeys[j] == i){
                        key = (byte) j;
                        break;
                    }
                }

                if(key != -1){
                    parsedNode.insert(key, parsedNodeChildren[i]);
                }
            }
        } else {
            for (int i=0; i<parsedNodeCount; i++){
                parsedNode.insert(parsedNodeKeys[i], parsedNodeChildren[i]);
            }
        }

    }


    /**
     *
     * <H1>Recursive function that de-serializes all the nodes in the provided input stream, building the tree recursively from the left side to the right side</H1>
     * Parse the current node and then recursively import its children, if it has any.
     *
     * @param artInputStream DataInputStreamWithPointer where the node will be imported from
     * @param parsedNode node to parse and import
     * @param progressTracker helper class to track the progress of the exportation
     * @throws IOException IO Exception in case the file can't be read or reached the end unexpectedly (meaning the exportation process didn't complete or the algorithm is faulty)
     */
    private void importNode(DataInputStreamWithPointer artInputStream, Node parsedNode, ProgressTracker progressTracker) throws IOException {
        if (parsedNode == null) return; // if for some reason the provided node is null, return

        // get the children of the parsed node to parse them
        Node[] parsedNodeChildren = parsedNode.getChildren();
        // get the keys of the parsed node
        byte[] parsedNodeKeys = parsedNode.getKeys();
        // get the number of children of the parsed node
        int parsedNodeCount = parsedNode.getCount();

        // iterate over the children of the parsed node
        for(int i=0; i<parsedNodeCount; i++){
            Node parsedChildNode = parseNode(artInputStream); // parse node
            parsedNodeChildren[i] = parsedChildNode; // assign the parsed child node to the children array

            progressTracker.updateProcessedBytes(artInputStream.getBytesRead());

            int parsedChildNodeCount = parsedChildNode.getCount();
            if(parsedChildNodeCount > 0){ // if the child node has children, import them
                importNode(artInputStream, parsedChildNode, progressTracker);
            }
        }

        if (parsedNode instanceof Node48){
            for(int i=0; i<parsedNodeCount; i++){
                byte key = -1;
                for(int j=0; j<parsedNodeKeys.length; j++){
                    if(parsedNodeKeys[j] == i){
                        key = (byte) j;
                        break;
                    }
                }

                if(key != -1){
                    parsedNode.insert(key, parsedNodeChildren[i]);
                }
            }
        } else if(parsedNode instanceof Node256){
            for (int i=0; i<parsedNodeCount; i++){
                parsedNode.insert(parsedNodeKeys[i], parsedNodeChildren[i]);
            }
        } else {
            for (int i=0; i<parsedNodeCount; i++){
                parsedNode.insert(parsedNodeKeys[i], parsedNodeChildren[i]);
            }
        }
    }


    /**
     * Set tree filename.
     *
     * @param filename filename
     */
    public void setFilename(String filename){
        this.filename = filename;
    }

    /**
     * Get tree filename.
     *
     * @return filename string
     */
    public String getFilename(){
        return this.filename;
    }
}


/**
 * Helper class to help keep track of the exportation and importation process.
 */
class ProgressTracker {
    private int lastPrintedPercentage = -1;
    private long processedBytes = 0;
    private long fileSize = 0;
    private int processedNodes = 0;
    private int totalNodes = 0;
    private final int barWidth = 30;

    /**
     * Instantiates a new Progress tracker for importation.
     * When importing, the file size is used to keep track of the progress.
     *
     * @param fileSize tree file size
     */
    public ProgressTracker(long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * Instantiates a new Progress tracker for exportation.
     * When exporting, the total nodes in the tree is used to keep track of the progress
     *
     * @param totalNodes tree total nodes
     */
    public ProgressTracker(int totalNodes) {
        this.totalNodes = totalNodes;
    }

    /**
     * Update processed bytes.
     *
     * @param processedBytes the processed bytes
     */
    public void updateProcessedBytes(long processedBytes) {
        this.processedBytes = processedBytes;
        updateProgress((int) ((processedBytes / (double) fileSize) * 100));
    }

    /**
     * Increment processed nodes.
     */
    public void incrementProcessedNodes() {
        this.processedNodes++;
        updateProgress((int) ((processedNodes / (double) totalNodes) * 100));
    }


    /**
     * Function that prints the current percentage if it is different from the last percentage. (to avoid unnecessary prints)
     * @param currentPercentage current exportation/importation percentage
     */
    private void updateProgress(int currentPercentage) {
        if (currentPercentage != lastPrintedPercentage) { // if the progress has changed
            int progress = (currentPercentage * barWidth) / 100; // calculate the progress
            String filledPart = "-".repeat(progress); // build the bar fill
            String pointer = (progress < barWidth) ? ">" : ""; // add > only if there is a space
            String emptyPart = " ".repeat(Math.max(barWidth - progress - pointer.length(), 0)); // fill the rest of the bar with empty spaces
            String bar = "[" + filledPart + pointer + emptyPart + "] (" + currentPercentage + "%)"; // build complete bar

            System.out.print("\r" + bar); // print in the same line
            lastPrintedPercentage = currentPercentage; // update last printed percentage
            if(lastPrintedPercentage == 100) System.out.println(); // if the exportation/importation is complete, break the line
        }
    }

    /**
     * Gets processed bytes.
     *
     * @return the processed bytes
     */
    public long getProcessedBytes() {
        return processedBytes;
    }

    /**
     * Gets processed nodes.
     *
     * @return the processed nodes
     */
    public int getProcessedNodes() {
        return processedNodes;
    }
}


/**
 * Slightly modified DataInputStream that keeps track of a pointer.
 * This is useful to keep track of the tree importation progress, when it is being imported from memory.
 */
class DataInputStreamWithPointer {
    private final DataInputStream dataInputStream;
    private long bytesRead = 0;

    /**
     * Instantiates a new Data input stream with pointer.
     *
     * @param data the data
     */
    public DataInputStreamWithPointer(byte[] data) {
        this.dataInputStream = new DataInputStream(new ByteArrayInputStream(data));
    }

    /**
     * Read byte from input stream, while updating the read bytes.
     *
     * @return the byte
     * @throws IOException the io exception
     */
    public byte readByte() throws IOException {
        bytesRead++;
        return dataInputStream.readByte();
    }

    /**
     * Read array of bytes, while updating the read bytes.
     *
     * @param b the b
     * @throws IOException the io exception
     */
    public void readFully(byte[] b) throws IOException {
        bytesRead += b.length;
        dataInputStream.readFully(b);
    }

    /**
     * Gets bytes read.
     *
     * @return the bytes read
     */
    public long getBytesRead() {
        return bytesRead;
    }

    /**
     * Close input stream.
     *
     * @throws IOException the io exception
     */
    public void close() throws IOException {
        dataInputStream.close();
    }
}
