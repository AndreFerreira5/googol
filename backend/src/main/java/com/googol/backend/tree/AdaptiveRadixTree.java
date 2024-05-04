package com.googol.backend.tree;

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
