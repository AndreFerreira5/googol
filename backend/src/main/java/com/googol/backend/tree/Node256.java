package com.googol.backend.tree;

import java.util.ArrayList;
import java.util.Arrays; /**
 * NODE256
 */
public class Node256 extends Node {
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
