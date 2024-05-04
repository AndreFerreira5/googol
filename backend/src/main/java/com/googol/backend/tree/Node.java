package com.googol.backend.tree;

import java.util.ArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock; /**
 * Node class.
 */
public abstract class Node{
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
    public void addLinkIndex(long linkIndex){
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
    public void setLinkIndices(ArrayList<Long> linkIndices){
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
    public ArrayList<Long> getLinkIndices(){
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
    public void setIsFinalWord(boolean isFinalWord){
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
    public boolean getIsFinalWord(){
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
    public void setCount(int count){
        if(count < 0 || count == this.count) return; // if count is not valid return
        lock.writeLock().lock();
        this.count = count;
        lock.writeLock().unlock();
    }

    /**
     * Increment count.
     */
    public void incrementCount(){
        lock.writeLock().lock();
        this.count++;
        lock.writeLock().unlock();
    }

    /**
     * Decrement count.
     */
    public void decrementCount(){
        lock.writeLock().lock();
        this.count--;
        lock.writeLock().unlock();
    }

    /**
     * Get count.
     *
     * @return int
     */
    public int getCount(){
        lock.readLock().lock();
        try{
            return this.count;
        } finally {
            lock.readLock().unlock();
        }
    }
}
