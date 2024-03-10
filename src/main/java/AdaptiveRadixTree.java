import java.util.ArrayList;
import java.util.Arrays;

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


    // this method will be used by the Node4 object when it has reached
    // the maximum number of 4 children, and it needs to add them to this new node
    // therefore there will be no duplicate child nodes check here, because it's assumed
    // the previous node didn't have any duplicate children
    // also it is assumed that the children nodes will be added sequentially
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
            }
        }
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


    // this method will be used by the Node16 object when it has reached
    // the maximum number of 16 children, and it needs to add them to this new node
    // therefore there will be no duplicate child nodes check here, because it's assumed
    // the previous node didn't have any duplicate children
    // also it is assumed that the children nodes will be added sequentially
    void insert(byte key, Node child){
        int unsignedKey = Byte.toUnsignedInt(key);
        if (count < 48) {
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
}


class Node256 extends Node {
    private Node[] children = new Node[256];

    public Node256() {
        Arrays.fill(children, null); // Initialize all children to null
    }

    @Override
    InsertResult insert(byte key) {
        int unsignedKey = Byte.toUnsignedInt(key);

        // Insert the child if it's not already present
        if (children[unsignedKey] == null) {
            children[unsignedKey] = new Node4();
            count++;
        }
        return new InsertResult(children[unsignedKey], null);
    }

    // this method will be used by the Node16 object when it has reached
    // the maximum number of 16 children, and it needs to add them to this new node
    // therefore there will be no duplicate child nodes check here, because it's assumed
    // the previous node didn't have any duplicate children
    // also it is assumed that the children nodes will be added sequentially
    void insert(byte key, Node child){
        int unsignedKey = Byte.toUnsignedInt(key);
        children[unsignedKey] = child;
        count++;
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
}


public class AdaptiveRadixTree {
    private Node root;

    public AdaptiveRadixTree(){
        this.root = new Node4();
    }


    public void insert(String word, long linkIndex) {
        byte[] wordBytes = word.getBytes(); // get bytes from word
        Node grandfatherNode = null; // track grandfather node of currentNode to update a child in case of a node upgrade
        Node previousNode = null; // track father node of currentNode to update the linkIndices with the provided linkIndex
                                  // because when inserting a new node, the children node of that node is returned
        Node currentNode = root; // track current node
        Node nextNode = root; // dispensable node used to search for a node with a certain byte key
                              // if that node exists it is returned, otherwise null is returned
                              // this way if the node doesn't exist, only nextNode will be null, instead of currentNode being null, which is critical for this algorithm

        // for each byte of the word
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
                        // update the father node of the upgraded node, swaping the old node with the upgraded one
                        grandfatherNode.updateNodeReference(wordBytes[grandfatherNode.count-1], insertResult.upgradedNode);
                    } else { // if the grandfatherNode is null, that means the root needs to be updated
                        this.root = insertResult.upgradedNode;
                    }
                }
            }
        }
        if(!previousNode.linkIndices.contains(linkIndex)) previousNode.linkIndices.add(linkIndex); // insert the new link Index only if it doesn't exist already
        previousNode.isFinalWord = true; // set node as Final Word
    }
    /*
    public void insert(String word, long linkIndex) {
        byte[] wordBytes = word.getBytes();
        int count=0;
        Node previousNode = null;
        Node currentNode = root;
        Node nextNode = root;
        for(byte b: wordBytes){
            count++;
            previousNode = currentNode;
            if((nextNode = currentNode.find(b)) != null){
                currentNode = nextNode;
            } else {
                InsertResult insertResult = currentNode.insert(b);
                if(previousNode != null && insertResult.upgradedNode != null){
                    byte[] slicedWordBytes = new byte[count];
                    System.arraycopy(wordBytes, 0, slicedWordBytes, 0, count);
                    updateNodeReference(slicedWordBytes, insertResult.upgradedNode);
                }
                //currentNode = currentNode.insert(b);
                currentNode = insertResult.node;
            }
        }
        assert previousNode != null;
        if(!previousNode.linkIndices.contains(linkIndex)) previousNode.linkIndices.add(linkIndex);
        previousNode.isFinalWord = true;
    }


    private void updateNodeReference(byte[] slicedWordBytes, Node upgradedNode){
        Node currentNode = root;
        Node previousNode = null;
        Node grandfatherNode = null;
        byte count = 0;
        for(byte b: slicedWordBytes){
            grandfatherNode = previousNode;
            previousNode = currentNode;
            currentNode = currentNode.find(b);
            count++;
        }
        if(count == 0) return;
        if(previousNode == root) this.root = upgradedNode;
        if(grandfatherNode != null) grandfatherNode.updateNodeReference(slicedWordBytes[count-2], upgradedNode);
    }*/


    public ArrayList<Long> find(String word) {
        byte[] wordBytes = word.getBytes();
        Node previousNode = null;
        Node currentNode = root;
        for(byte b: wordBytes){
            previousNode = currentNode;
            if((currentNode = currentNode.find(b)) == null){
                return null;
            }
        }
        assert previousNode != null;
        if(previousNode.isFinalWord){
            return previousNode.linkIndices;
        } else {
            return null;
        }
    }
}
