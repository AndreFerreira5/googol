import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.nio.ByteOrder;

class InsertResult{
    public Node node;
    public Node upgradedNode;

    public InsertResult(Node node, Node upgradedNode){
        this.node = node;
        this.upgradedNode = upgradedNode;
    }
}

abstract class Node {
    protected int count = 0; // to keep track of the number of children
    protected ArrayList<Long> linkIndices = new ArrayList<>();

    abstract InsertResult insert(byte key);
    abstract void insert(byte key, Node child);
    abstract Node find(byte key);
    boolean isFinalWord = false;

    abstract void updateNodeReference(byte key, Node upgradedNode);
    abstract byte[] getKeys();
    abstract Node[] getChildren();
    abstract void setChildren(Node[] children);
}


class Node4 extends Node {
    private byte[] keys = new byte[4];
    private Node[] children = new Node[4];


    private Node16 upgradeToNode16(){
        Node16 newNode = new Node16();
        for(int i = 0; i < 4; i++){
            newNode.insert(keys[i], children[i]);
        }
        return newNode;
    }


    @Override
    InsertResult insert(byte key) {
        // if the node has reached its maximum number of children (4)
        if (count == 4) {
            // upgrade node to Node16 and insert the new link index
            Node16 upgradedNode = upgradeToNode16();
            Node insertedChild = upgradedNode.insert(key).node;
            //return upgradeToNode16().insert(key);
            return new InsertResult(insertedChild, upgradedNode);
        }

        /* if there is space available on the node and there isn't a child with the same key*/
        keys[count] = key; // insert the new key and child into the new node
        children[count] = new Node4(); // insert the new node into the children array
        count++;
        return new InsertResult(children[count-1], null);
    }


    void insert(byte key, Node child) {
        if (count < 4) {
            keys[count] = key;
            children[count] = child;
            count++;
        } else {
            throw new IllegalStateException("Node4 is full and cannot add more children.");
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
        // iterate through the node's existing children
        for(int i = 0; i < count; i++){
            if(keys[i] == key){ // if node found
                return children[i];
            }
        }
        return null; // if node not found
    }


    @Override
    byte[] getKeys(){
        return keys;
    }


    @Override
    Node[] getChildren(){
        return children;
    }

    @Override
    void setChildren(Node[] children){
        this.children = children;
    }
}


class Node16 extends Node {
    private byte[] keys = new byte[16];
    private Node[] children = new Node[16];


    private Node48 upgradeToNode48() {
        Node48 newNode = new Node48();
        for (int i = 0; i < 16; i++) {
            newNode.insert(keys[i], children[i]);
        }
        return newNode;
    }


    @Override
    InsertResult insert(byte key) {
        // check if the node is full and needs to be upgraded to Node48
        if (count == 16) {
            // upgrade to Node48 and insert the new child
            Node48 upgradedNode = upgradeToNode48();
            Node insertedChild = upgradedNode.insert(key).node;
            //return upgradeToNode48().insert(key);
            return new InsertResult(insertedChild, upgradedNode);
        }


        // insert the new key and child
        keys[count] = key;
        children[count] = new Node4();
        count++;
        return new InsertResult(children[count-1], null);
    }


    void insert(byte key, Node child){
        if (count < 16) {
            keys[count] = key;
            children[count] = child;
            count++;
        } else {
            throw new IllegalStateException("Node16 is full and cannot add more children.");
        }
    }


    @Override
    Node find(byte key) {
        // TODO change this linear search to binary search
        for (int i = 0; i < count; i++) {
            if (keys[i] == key) {
                return children[i];
            }
        }
        return null;
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
    byte[] getKeys(){
        return keys;
    }


    @Override
    Node[] getChildren(){
        return children;
    }

    @Override
    void setChildren(Node[] children){
        this.children = children;
    }
}


class Node48 extends Node {
    private byte[] keyIndex = new byte[256]; // Maps key bytes to child indices, initialized to a sentinel value
    private Node[] children = new Node[48];

    public Node48() {
        // Initialize index array to indicate no child for all keys
        Arrays.fill(keyIndex, (byte) -1); // Assuming -1 is used as sentinel value
    }


    private Node256 upgradeToNode256() {
        Node256 newNode = new Node256();
        // Migrate children to the new Node256
        for (int i = 0; i < 256; i++) {
            if (keyIndex[i] != -1) { // Check if there's a child for this key
                newNode.insert((byte) i, children[keyIndex[i]]);
            }
        }
        return newNode;
    }


    @Override
    InsertResult insert(byte key) {
        // check if the node needs to be upgraded to Node256
        if (count == 48) {
            Node256 upgradedNode = upgradeToNode256();
            Node insertedChild = upgradedNode.insert(key).node;
            //return upgradeToNode256().insert(key);
            return new InsertResult(insertedChild, upgradedNode);
        }

        int unsignedKey = Byte.toUnsignedInt(key);
        keyIndex[unsignedKey] = (byte) count;
        children[count] = new Node4();
        count++;
        return new InsertResult(children[count-1], null);
    }

    void insert(byte key, Node child){
        if (count < 48) {
            int unsignedKey = Byte.toUnsignedInt(key);
            keyIndex[unsignedKey] = (byte) count;
            children[count] = child;
            count++;
        } else {
            throw new IllegalStateException("Node48 is full and cannot add more children.");
        }
    }


    @Override
    Node find(byte key){
        int unsignedKey = Byte.toUnsignedInt(key);
        if (keyIndex[unsignedKey] != -1) {
            return children[keyIndex[unsignedKey]];
        }
        return null;
    }


    void updateNodeReference(byte key, Node upgradedNode){
        int unsignedKey = Byte.toUnsignedInt(key);
        children[keyIndex[unsignedKey]] = upgradedNode;
    }


    @Override
    byte[] getKeys(){
        return keyIndex;
    }


    @Override
    Node[] getChildren(){
        return children;
    }

    @Override
    void setChildren(Node[] children){
        this.children = children;
    }
}


class Node256 extends Node {
    private Node[] children = new Node[256];

    public Node256() {
        Arrays.fill(children, null); // Initialize all children to null
    }

    @Override
    InsertResult insert(byte key) {
        if(count == 256){
            throw new IllegalStateException("Node256 is full and cannot add more children.");
        }

        int unsignedKey = Byte.toUnsignedInt(key);

        // Insert the child if it's not already present
        if (children[unsignedKey] == null) {
            children[unsignedKey] = new Node4();
            count++;
        }
        return new InsertResult(children[unsignedKey], null);
    }

    void insert(byte key, Node child){
        if(count < 256){
            int unsignedKey = Byte.toUnsignedInt(key);
            children[unsignedKey] = child;
            count++;
        } else {
            throw new IllegalStateException("Node256 is full and cannot add more children.");
        }
    }


    @Override
    Node find(byte key) {
        int unsignedKey = Byte.toUnsignedInt(key);
        return children[unsignedKey];
    }


    void updateNodeReference(byte key, Node upgradedNode){
        int unsignedKey = Byte.toUnsignedInt(key);
        children[unsignedKey] = upgradedNode;
    }


    @Override
    byte[] getKeys() {
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
    }


    @Override
    Node[] getChildren(){
        return children;
    }

    @Override
    void setChildren(Node[] children){
        this.children = children;
    }
}


public class AdaptiveRadixTree {
    private Node root;
    private String filename = "art.bin";

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
                if(insertResult.upgradedNode != null){ // if there has been a node upgrade
                    if(grandfatherNode != null){ // if the grandfatherNode exists (meaning the node we need to update is not the root)
                        // update the father node of the upgraded node, swapping the old node with the upgraded one
                        grandfatherNode.updateNodeReference(wordBytes[count-1], insertResult.upgradedNode); //wordBytes[count-1] represents the byte of the previous character in the previous node that needs to be replaced with the upgraded node
                    } else { // if the grandfatherNode is null, that means the root needs to be updated
                        this.root = insertResult.upgradedNode;
                    }
                }
            }
            count++;
        }
        if(!currentNode.linkIndices.contains(linkIndex)) currentNode.linkIndices.add(linkIndex); // insert the new link Index only if it doesn't exist already
        currentNode.isFinalWord = true; // set node as Final Word
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
            return currentNode.linkIndices;
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
        return previousNode;
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
    public void exportART() {
        // try to open the file
        try(RandomAccessFile artFile = new RandomAccessFile(this.filename, "rw");){
            try{
                exportNode(artFile, root);
            }
            catch(Exception e){
                System.out.println("ERROR EXPORTING ROOT NODE: " + e + "\nStopping the exportation...");
            }
        } catch (FileNotFoundException e) {
            System.out.println("TREE FILE NOT FOUND! Stopping the exportation...");
        } catch (IOException e) {
            System.out.println("ERROR OPENING FILE: " + e + "\nStopping the exportation...");
        }

        System.out.println("TREE EXPORTED SUCCESSFULLY TO FILE: " + this.filename);
    }


    /* Recursive function that serializes the provided node and recursively all its children and the nodes bellow them */
    private void exportNode(RandomAccessFile artFile, Node node) throws IOException{
        // if the node is null, return
        if(node == null) return;

        // get node class
        int nodeType;
        if(node instanceof Node4) nodeType = 0;
        else if(node instanceof Node16) nodeType = 1;
        else if(node instanceof Node48) nodeType = 2;
        else if(node instanceof Node256) nodeType = 3;
        else throw new IllegalStateException("Unexpected node type.");

        // get node info
        int childrenNum = node.count;
        boolean isFinalWord = node.isFinalWord;
        int indicesNum = node.linkIndices.size();
        byte[] keys = node.getKeys();
        Node[] children = node.getChildren();

        // nodeType + childrenNum + isFinalWord + indicesNum + linkIndices + keys (only allocate for existing keys)
        byte[] bytes = new byte[Integer.BYTES + Integer.BYTES + 1 + Integer.BYTES + Long.BYTES*indicesNum + Byte.BYTES*childrenNum];
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); // little endian for x86 compatibility

        /* Put node data into the byte buffer */
        buffer.putInt(nodeType);
        buffer.putInt(childrenNum);
        buffer.put((byte) (isFinalWord ? 1 : 0));
        buffer.putInt(indicesNum);
        for(long linkIndex: node.linkIndices){
            buffer.putLong(linkIndex);
        }
        for(int i=0; i<childrenNum; i++){
            buffer.put(keys[i]);
        }

        // write byte buffer to file
        artFile.write(bytes);

        // recursively export children
        for( Node childrenNode: children){
            if(childrenNode == null) continue; // skip if for some reason the node is null
            try{
                exportNode(artFile, childrenNode); // recursive call to export child node
            }
            catch(Exception e){
                System.out.println("ERROR EXPORTING NODE: " + e + "\nIgnoring node and continuing the exportation...");
            }

        }
    }


    public void importART(){
        try(RandomAccessFile artFile = new RandomAccessFile(this.filename, "rw");){
            Node rootNode = parseNode(artFile);
            importNode(artFile, rootNode);
            this.root = rootNode;
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
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

        // create node based on the nodeType
        Node node;
        switch (nodeType){
            case 0:
                node = new Node4();
                break;
            case 1:
                node = new Node16();
                break;
            case 2:
                node = new Node48();
                break;
            case 3:
                node = new Node256();
                break;
            default:
                throw new IllegalStateException("Unexpected node type.");
        }

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

        // read all keys from file
        byte[] keys = new byte[childrenNum];
        artFile.read(keys);

        // assign values to the node
        node.linkIndices = linkIndices;
        node.isFinalWord = isFinalWord != 0;
        for (byte key : keys) {
            node.insert(key);
        }

        return node;
    }


    /* Recursive function that de-serializes all the nodes in the provided file, building the tree recursively from the left side to the right side*/
    private void importNode(RandomAccessFile artFile, Node parsedNode) throws IOException {
        if (parsedNode == null) return; // if for some reason the provided node is null, return

        // get the children of the parsed node to parse them
        Node[] parsedNodeChildren = parsedNode.getChildren();
        // iterate over the children of the parsed node
        for(int i=0; i<parsedNode.count; i++){
            Node parsedChildNode = parseNode(artFile); // parse node
            parsedNodeChildren[i] = parsedChildNode; // assign the parsed child node to the children array
            if(parsedChildNode.count > 0){ // if the child node has children, import them
                importNode(artFile, parsedChildNode);
            }
        }
        parsedNode.setChildren(parsedNodeChildren); // update parsed node children with parsed children
    }
}
