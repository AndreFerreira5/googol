import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class InsertResult{
    public Node node;
    public Node upgradedNode;
    public ReadWriteLock lock;

    public InsertResult(Node node, Node upgradedNode, ReadWriteLock lock){
        this.node = node;
        this.upgradedNode = upgradedNode;
        this.lock = lock;
    }
}

abstract class Node{
    protected int count = 0; // to keep track of the number of children
    protected ArrayList<Long> linkIndices = new ArrayList<>();

    abstract InsertResult insert(byte key);
    abstract void insert(byte key, Node child);
    abstract Node find(byte key);
    boolean isFinalWord = false;
    protected ReadWriteLock lock = new ReentrantReadWriteLock();


    abstract void updateNodeReference(byte key, Node upgradedNode);
    abstract byte[] getKeys();

    abstract void setKeys(byte[] keyIndex);

    abstract Node[] getChildren();
    abstract void setChildren(Node[] children);

    protected boolean isValidLinkIndex(long linkIndex){
        return linkIndex>=0;
    }

    void addLinkIndex(long linkIndex){
        if(!isValidLinkIndex(linkIndex)) return;
        Long longObjIndex = linkIndex;
        if(longObjIndex == null) return;

        lock.writeLock().lock();
        try{
            linkIndices.add(longObjIndex);
        } finally {
            lock.writeLock().unlock();
        }
    }

    void setLinkIndices(ArrayList<Long> linkIndices){
        if(linkIndices == null) return;
        lock.writeLock().lock();
        this.linkIndices = linkIndices;
        lock.writeLock().unlock();
    }

    ArrayList<Long> getLinkIndices(){
        lock.readLock().lock();
        try{
            return new ArrayList<>(this.linkIndices);
        } finally {
            lock.readLock().unlock();
        }
    }

    void setIsFinalWord(boolean isFinalWord){
        if(isFinalWord == this.isFinalWord) return;

        lock.writeLock().lock();
        this.isFinalWord = isFinalWord;
        lock.writeLock().unlock();
    }

    boolean getIsFinalWord(){
        lock.readLock().lock();
        try{
            return this.isFinalWord;
        } finally {
            lock.readLock().unlock();
        }
    }

    void setCount(int count){
        if(count < 0 || count == this.count) return;
        lock.writeLock().lock();
        this.count = count;
        lock.writeLock().unlock();
    }

    void incrementCount(){
        lock.writeLock().lock();
        this.count++;
        lock.writeLock().unlock();
    }

    void decrementCount(){
        lock.writeLock().lock();
        this.count--;
        lock.writeLock().unlock();
    }

    int getCount(){
        lock.readLock().lock();
        try{
            return this.count;
        } finally {
            lock.readLock().unlock();
        }
    }
}


class Node4 extends Node {
    private byte[] keys = new byte[4];
    private Node[] children = new Node[4];


    private Node16 upgradeToNode16(){
        Node16 newNode = new Node16();
        for(int i = 0; i < 4; i++){
            newNode.insert(keys[i], children[i]);
        }
        newNode.linkIndices = this.linkIndices;
        if (this.isFinalWord) newNode.isFinalWord = true;
        return newNode;
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


    // TODO maybe change these inserts to upgrade the node instead of just throwing an exception
    void insert(byte key, Node child) {
        lock.writeLock().lock();
        try {
            // if the key already exists, replace the respective child with the provided one
            if(this.find(key) != null){
                updateNodeReference(key, child);
                return;
            }

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


// TODO change linear search to binary search in this type of node (the keys and node arrays should be sorted with each insertion, so the binary search works)
class Node16 extends Node {
    private byte[] keys = new byte[16];
    private Node[] children = new Node[16];


    private Node48 upgradeToNode48() {
        Node48 newNode = new Node48();
        for (int i = 0; i < 16; i++) {
            newNode.insert(keys[i], children[i]);
        }

        newNode.linkIndices = this.linkIndices;
        if (this.isFinalWord) newNode.isFinalWord = true;
        return newNode;
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


class Node48 extends Node {
    private byte[] keyIndex = new byte[256]; // Maps key bytes to child indices, initialized to a sentinel value
    private Node[] children = new Node[48];

    public Node48() {
        // initialize index array to indicate no child for all keys
        Arrays.fill(keyIndex, (byte) -1);
    }


    private Node256 upgradeToNode256() {
        Node256 newNode = new Node256();
        // migrate children to the new Node256
        for (int i = 0; i < 256; i++) {
            if (keyIndex[i] != -1) { // if there's a child for this key
                newNode.insert((byte) i, children[keyIndex[i]]);
            }
        }

        newNode.linkIndices = this.linkIndices;
        if (this.isFinalWord) newNode.isFinalWord = true;
        return newNode;
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


class Node256 extends Node {
    private Node[] children = new Node[256];

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


public class AdaptiveRadixTree {
    private Node root;
    private String filename = "art.bin";

    private final int NODE4_TYPE = 0;
    private final int NODE16_TYPE = 1;
    private final int NODE48_TYPE = 2;
    private final int NODE256_TYPE = 3;

    public AdaptiveRadixTree(){
        this.root = new Node4();
    }


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
        //if(!currentNode.linkIndices.contains(linkIndex)) currentNode.linkIndices.add(linkIndex); // insert the new link Index only if it doesn't exist already
        currentNode.setIsFinalWord(true);
        //currentNode.isFinalWord = true; // set node as Final Word
    }


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


    // return node that contains the given word
    public Node findNode(String word){
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
        return currentNode;
    }


    /*********************************************************************************
    *                               NODE SERIALIZATION                               *
    *   x------------------------------------------------------------------------x   *
    *   | nodeType | childrenNum | isFinalWord | indicesNum | linkIndices | keys |   *
    *   x------------------------------------------------------------------------x   *
    *                                                                                *
    *   nodeType: INT (4 Bytes)                                                      *
    *   childrenNum: INT (4 Bytes)                                                   *
    *   isFinalWord: BOOLEAN (1 Byte)                                                *
    *   indicesNum: INT (4 Bytes)                                                    *
    *   linkIndices: indicesNum * LONG (indicesNum * 8 Bytes)                        *
    *   keys: childrenNum * BYTE (childrenNum * 1 Byte)                              *
    *                                                                                *
    **********************************************************************************/
    public void exportART() throws IOException{
        int totalNodes = countNodes(root);
        ProgressTracker progressTracker = new ProgressTracker(totalNodes);

        // try to open the file
        try(RandomAccessFile artFile = new RandomAccessFile(this.filename, "rw")){
            try{
                //System.out.print(artFile + "|");
                exportNode(artFile, root, progressTracker);
            }
            catch(Exception e){
                System.out.println("ERROR EXPORTING ROOT NODE: " + e + "\nStopping the exportation...");
            }
        } catch (IOException e) {
            throw new IOException(e.getMessage());
        }
        System.out.println("TREE EXPORTED SUCCESSFULLY TO FILE: " + this.filename);
    }


    private int countNodes(Node node){
        if(node == null) return 0;
        int count = 1;
        for(Node child: node.getChildren()){
            count += countNodes(child);
        }
        return count;
    }


    /* Recursive function that serializes the provided node and recursively all its children and the nodes bellow them */
    private void exportNode(RandomAccessFile artFile, Node node, ProgressTracker progressTracker) throws IOException{
        // if the node is null, return
        if(node == null) return;
        //System.out.println("node not null");
        // get node class
        int nodeType;
        if(node instanceof Node4) nodeType = NODE4_TYPE;
        else if(node instanceof Node16) nodeType = NODE16_TYPE;
        else if(node instanceof Node48) nodeType = NODE48_TYPE;
        else if(node instanceof Node256) nodeType = NODE256_TYPE;
        else throw new IllegalStateException("Unexpected node type. " + node.getClass().getName());
        //System.out.println("node type: " + nodeType);

        /*try {
            System.out.println("try lock write: " + node.lock.writeLock().tryLock(1, TimeUnit.SECONDS));
            System.out.println("try lock read: " + node.lock.readLock().tryLock(1, TimeUnit.SECONDS));
        } catch (Exception e){
            System.out.println("try lock exception + " + e);
        }*/

        // get node info
        int childrenNum = node.getCount();
        //System.out.println("children num: " + childrenNum);
        boolean isFinalWord = node.getIsFinalWord();
        //System.out.println("isFinalWord: " + isFinalWord);
        ArrayList<Long> linkIndices = node.getLinkIndices();
        int indicesNum = 0;
        for(int i=0; i<linkIndices.size(); i++){
            if(linkIndices.get(i) != null) indicesNum++;
        }
        //int indicesNum = node.linkIndices.size();
        //System.out.println("indices num: " + indicesNum);

        byte[] keys = node.getKeys();
        //System.out.println("keys: " + Arrays.toString(keys));
        Node[] children = node.getChildren();
        //System.out.println("children: " + Arrays.toString(children));

        // nodeType + childrenNum + isFinalWord + indicesNum + linkIndices + keys (only allocate for existing keys)
        byte[] bytes = new byte[Integer.BYTES + Integer.BYTES + 1 + Integer.BYTES + Long.BYTES*indicesNum + Byte.BYTES*childrenNum*(nodeType==NODE48_TYPE?2:1)];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); // little endian for x86 compatibility
        //System.out.println("buffer set up with " + bytes.length + " bytes");

        /* Put node data into the byte buffer */
        buffer.putInt(nodeType);
        //System.out.println("put nodetype");
        buffer.putInt(childrenNum);
        //System.out.println("put children num");
        buffer.put((byte) (isFinalWord ? 1 : 0));
        //System.out.println("put isfinalword");
        buffer.putInt(indicesNum);
        //System.out.println("put indices num");
        for(int i=0; i<linkIndices.size(); i++){
            if(linkIndices.get(i) == null) continue;
            buffer.putLong(linkIndices.get(i));
        }
        /*
        for(long linkIndex: node.linkIndices){
            buffer.putLong(linkIndex);
            //System.out.print(linkIndex);
        }*/
        //System.out.println(" put linkindices");

        switch(nodeType){
            case NODE4_TYPE:
            case NODE16_TYPE:
                for (int i = 0; i < childrenNum; i++) {
                    buffer.put(keys[i]);
                    //System.out.print(keys[i]);
                }
                //System.out.println(" put keys");
                break;
            case NODE48_TYPE:
                for(int i=0; i<256; i++) {
                    if (keys[i] != -1){
                        buffer.put((byte) i);
                        //System.out.print(i);
                        buffer.put(keys[i]);
                        //System.out.print(keys[i] + " ");
                    }
                }
                //System.out.println(" put keys");
                break;
            case NODE256_TYPE:
                for (byte key : keys) {
                    //System.out.println(key);
                    buffer.put(key);
                }
                //System.out.println(" put keys");
                break;
        }

        // write byte buffer to file
        artFile.write(bytes);
        //System.out.println("written");

        //System.out.print(artFile.getFilePointer() + "|");
        progressTracker.incrementProcessedNodes();

        // recursively export children
        for( Node childrenNode: children){
            if(childrenNode == null) continue; // skip if for some reason the node is null
            try{
                //System.out.println("exporting child node");
                exportNode(artFile, childrenNode, progressTracker); // recursive call to export child node
            }
            catch(Exception e){
                throw new IOException(e);
                //System.out.println("ERROR EXPORTING NODE: " + e + "\nStopping exportation...");
            }

        }
        //System.out.println("node exported!");
    }


    public void importART() throws IOException{
        try(RandomAccessFile artFile = new RandomAccessFile(this.filename, "rw")){
            long fileSize = artFile.length();
            ProgressTracker progressTracker = new ProgressTracker(fileSize);

            //System.out.print(artFile.getFilePointer() + "|");
            Node rootNode = parseNode(artFile);
            importNode(artFile, rootNode, progressTracker);

            this.root = rootNode;
        } catch (IOException e) {
            throw new IOException(e);
        }
        System.out.println("TREE IMPORTED SUCCESSFULLY FROM FILE: " + this.filename);
    }


    private Node parseNode(RandomAccessFile artFile) throws IOException{
        // int and long byte buffers to later get int and long values from the provided file
        byte[] intBuffer = new byte[Integer.BYTES];
        byte[] longBuffer = new byte[Long.BYTES];

        // get nodeType from file
        artFile.read(intBuffer);
        int nodeType = ByteBuffer.wrap(intBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();
        //System.out.println("read node type: " + nodeType);

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
        //System.out.println("read children num: " + childrenNum);

        // get isFinalWord from file
        byte isFinalWord = artFile.readByte();
        //System.out.println("read isfinalword: " + isFinalWord);

        // get indicesNum from file
        artFile.read(intBuffer);
        int indicesNum = ByteBuffer.wrap(intBuffer).order(ByteOrder.LITTLE_ENDIAN).getInt();
        //System.out.println("read indices num: " + indicesNum);

        // get linkIndices from file
        ArrayList<Long> linkIndices = new ArrayList<>();
        for(int i=0; i<indicesNum; i++){
            artFile.read(longBuffer);
            linkIndices.add(ByteBuffer.wrap(longBuffer).order(ByteOrder.LITTLE_ENDIAN).getLong());
            //System.out.print(linkIndices.get(i) + " ");
        }
        //System.out.println(" - read indices: " + linkIndices);


        switch(nodeType){
            case NODE4_TYPE:
            case NODE16_TYPE:
                // read all keys from file
                byte[] keys = new byte[childrenNum];
                artFile.read(keys);
                //System.out.println("read node4/16 keys: " + Arrays.toString(keys));
                for (byte key : keys) {
                    node.insert(key);
                }
                break;
            case NODE48_TYPE:
                byte[] keyIndex = node.getKeys();

                //System.out.println("going node 48 keys");
                for (int i = 0; i < childrenNum; i++) {
                    byte key = artFile.readByte();
                    //System.out.print(key+"|");
                    byte childIndex = artFile.readByte();
                    //System.out.print(childIndex + " ");
                    keyIndex[Byte.toUnsignedInt(key)] = childIndex;
                }
                node.setKeys(keyIndex);
                node.setCount(childrenNum);

                break;
            case NODE256_TYPE:
                //System.out.println("going node 256 keys");
                for (int i = 0; i < childrenNum; i++) {
                    byte childKey = artFile.readByte();
                    //System.out.print(childKey + " ");
                    node.insert(childKey);
                }
                break;
        }


        // assign values to the node
        node.setLinkIndices(linkIndices);
        //node.linkIndices = linkIndices;
        node.setIsFinalWord(isFinalWord != 0);
        //node.isFinalWord = isFinalWord != 0;

        //System.out.println();
        //System.out.println("node parsed!");
        //System.out.print(artFile.getFilePointer() + "|");
        return node;
    }


    /* Recursive function that de-serializes all the nodes in the provided file, building the tree recursively from the left side to the right side*/
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
        } else if(parsedNode instanceof Node256){
            Arrays.sort(parsedNodeKeys);
            for (int i=0; i<parsedNodeCount; i++){
                parsedNode.insert(parsedNodeKeys[i], parsedNodeChildren[i]);
            }
        } else {
            for (int i=0; i<parsedNodeCount; i++){
                parsedNode.insert(parsedNodeKeys[i], parsedNodeChildren[i]);
            }
        }
    }


    public void setFilename(String filename){
        this.filename = filename;
    }
}


class ProgressTracker {
    private int lastPrintedPercentage = -1;
    private long processedBytes = 0;
    private long fileSize = 0;
    private int processedNodes = 0;
    private int totalNodes = 0;

    public ProgressTracker(long fileSize) {
        this.fileSize = fileSize;
    }

    public ProgressTracker(int totalNodes) {
        this.totalNodes = totalNodes;
    }

    public void updateProcessedBytes(long processedBytes) {
        this.processedBytes = processedBytes;
        updateProgress((int) ((processedBytes / (double) fileSize) * 100));
    }

    public void incrementProcessedNodes() {
        this.processedNodes++;
        updateProgress((int) ((processedNodes / (double) totalNodes) * 100));
    }

    private void updateProgress(int currentPercentage) {
        if (currentPercentage != lastPrintedPercentage) {
            System.out.println("Progress: " + currentPercentage + "%");
            lastPrintedPercentage = currentPercentage;
        }
    }
}
