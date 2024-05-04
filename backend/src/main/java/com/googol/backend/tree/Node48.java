package com.googol.backend.tree;

import java.util.Arrays; /**
 * NODE48
 */
public class Node48 extends Node {
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
