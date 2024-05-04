package com.googol.backend.storage;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import com.googol.backend.model.ParsedUrl;
import com.googol.backend.model.ParsedUrlIdPair;

/**
 * Index Storage Barrel Remote Interface.
 */
public interface IndexStorageBarrelRemote extends Remote{
    /**
     * Search word array list.
     *
     * @param word     the word
     * @param page     the page
     * @param pageSize the page size
     * @return the array list
     * @throws RemoteException the remote exception
     */
    ArrayList<ArrayList<String>> searchWord(String word, int page, int pageSize) throws RemoteException;

    /**
     * Search word set array list.
     *
     * @param words    the words
     * @param page     the page
     * @param pageSize the page size
     * @return the array list
     * @throws RemoteException the remote exception
     */
    ArrayList<ArrayList<String>> searchWordSet(ArrayList<String> words, int page, int pageSize) throws RemoteException;

    /**
     * Export barrel.
     *
     * @throws RemoteException the remote exception
     */
    void exportBarrel() throws RemoteException;

    /**
     * Get art byte [ ].
     *
     * @return the byte [ ]
     * @throws RemoteException the remote exception
     */
    byte[] getArt() throws RemoteException;

    /**
     * Gets parsed urls map.
     *
     * @return the parsed urls map
     * @throws RemoteException the remote exception
     */
    ConcurrentHashMap<ParsedUrlIdPair, ParsedUrl> getParsedUrlsMap() throws RemoteException;

    /**
     * Gets url to url key pair map.
     *
     * @return the url to url key pair map
     * @throws RemoteException the remote exception
     */
    ConcurrentHashMap<String, ParsedUrlIdPair> getUrlToUrlKeyPairMap() throws RemoteException;

    /**
     * Gets id to url key pair map.
     *
     * @return the id to url key pair map
     * @throws RemoteException the remote exception
     */
    ConcurrentHashMap<Long, ParsedUrlIdPair> getIdToUrlKeyPairMap() throws RemoteException;

    /**
     * Gets availability.
     *
     * @return the availability
     * @throws RemoteException the remote exception
     */
    double getAvailability() throws RemoteException;

    /**
     * Gets father urls.
     *
     * @param urls the urls
     * @return the father urls
     * @throws RemoteException the remote exception
     */
    ArrayList<ArrayList<String>> getFatherUrls(ArrayList<String> urls) throws RemoteException;

    /**
     * Gets father urls.
     *
     * @param urls the urls
     * @return the father urls
     * @throws RemoteException the remote exception
     */
    ArrayList<String> getFatherUrls(String urls) throws RemoteException;
}
