package com.googol.backend.tree;

/**
 * NODE16.
 */
// TODO change linear search to binary search in this type of node (the keys and node arrays should be sorted with each insertion, so the binary search works)
public class Node16 extends Node {
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
