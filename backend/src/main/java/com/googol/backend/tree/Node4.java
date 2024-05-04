package com.googol.backend.tree;

/**
 * NODE4.
 */
public class Node4 extends Node {
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
