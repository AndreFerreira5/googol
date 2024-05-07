package com.googol.backend.gateway;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import com.googol.backend.model.RawUrl;

/**
 * The interface Gateway remote.
 */
public interface GatewayRemote extends Remote{
    /**
     * Return the parsing delimiter via RMI.
     * @return parsing delimiter
     * @throws RemoteException RMI Exception
     */
    char getParsingDelimiter() throws RemoteException;

    /**
     * Return the crawling max depth via RMI.
     * @return crawling max depth
     * @throws RemoteException RMI Exception
     */
    int getCrawlingMaxDepth() throws RemoteException;

    /**
     * Return the multicast address via RMI.
     * @return multicast address
     * @throws RemoteException RMI Exception
     */
    String getMulticastAddress() throws RemoteException;

    /**
     * Return the multicast port via RMI.
     * @return multicast port
     * @throws RemoteException RMI Exception
     */
    int getMulticastPort() throws RemoteException;

    /**
     * Get RawUrl object from the front of the double ended queue.
     * @return RawUrl object, ready to be parsed
     * @throws InterruptedException Interrupted Exception
     * @throws RemoteException RMI Exception
     */
    RawUrl getUrlFromDeque() throws InterruptedException, RemoteException;

    /**
     * Add RawUrl object to the Deque, based on the crawling strategy being used
     * @param rawUrl the RawUrl object
     * @throws RemoteException RMI Exception
     */
    void addUrlToUrlsDeque(RawUrl rawUrl) throws RemoteException;

    /**
     * Add RawUrl object to Dequeue, based on the crawling strategy being used
     * but create the RawUrl object before, as it's only received the url string.
     * If the url is invalid, catch the exception and return false.
     * Otherwise, return true.
     * @param url the string url
     * @return true if successful, false otherwise
     * @throws RemoteException RMI Exception
     */
    boolean addUrlToUrlsDeque(String url) throws RemoteException;

    /**
     * Add array list of RawUrl objects to Dequeue, based on the crawling strategy being used.
     * @param rawUrls the raw urls
     * @throws RemoteException RMI Exception
     */
    void addRawUrlsToUrlsDeque(ArrayList<RawUrl> rawUrls) throws RemoteException;

    /**
     * Add array list of url Strings to Dequeue, based on the crawling strategy being used,
     * creating the RawUrl objects using the provided strings.
     * If the url is invalid, append its position on the provided array list of url string to an array.
     * In the end, if the bad urls is empty, it means that there was no invalid url.
     * If it is not empty, it means there were invalid urls and that array list is returned so the Client
     * knows which urls were bad and report it to the user.
     *
     * @param rawUrls the raw urls
     * @return badUrls position in provided string array list of urls
     * @throws RemoteException RMI Exception
     */
    ArrayList<Integer> addUrlsToUrlsDeque(ArrayList<String> rawUrls) throws RemoteException;

    /**
     * Increment parsed urls via RMI.
     * @throws RemoteException RMI Exception
     */
    void incrementParsedUrls() throws RemoteException;

    /**
     * Get parsed urls via RMI.
     * @return parsed urls
     * @throws RemoteException RMI Exception
     */
    long getParsedUrls() throws RemoteException;

    /**
     * Increment and get parsed urls via RMI.
     * @return incremented parsed urls
     * @throws RemoteException RMI Exception
     */
    long incrementAndGetParsedUrls() throws RemoteException;

    /**
     * Register a barrel in the online barrels hash map, using the provided barrel endpoint
     * as its identifier. And log the registering.
     * @param barrelEndpoint the barrel endpoint
     * @throws RemoteException EMI Exception
     */
    void registerBarrel(String barrelEndpoint) throws RemoteException;

    /**
     * Unregister a barrel from the online barrels hash map, using the provided barrel endpoint.
     * And log the unregistering.
     * @param barrelEndpoint the barrel endpoint
     * @throws RemoteException RMI Exception
     */
    void unregisterBarrel(String barrelEndpoint) throws RemoteException;

    /**
     * Get all registered barrels.
     * @return list of registered barrels
     * @throws RemoteException RMI Exception
     */
    ArrayList<String> getRegisteredBarrels() throws RemoteException;

    /**
     * Get registered barrels count
     * @return registered barrels count
     * @throws RemoteException RMI Exception
     */
    int getRegisteredBarrelsCount() throws RemoteException;

    /**
     * Get random barrel from online barrels.
     * @return random barrel
     * @throws RemoteException RMI Exception
     */
    String getRandomBarrelRemote() throws RemoteException;

    /**
     * Get the most available registered barrel.
     * @param callingBarrel the calling barrel (as to not return itself)
     * @return most available barrel
     */
    String getMostAvailableBarrelRemote(String callingBarrel) throws RemoteException;

    /**
     * Get the most available registered barrel.
     * @return most available barrel
     */
    String getMostAvailableBarrelRemote() throws RemoteException;

    /**
     * Search a single word in the most available barrel
     * @param word          the word to search
     * @param page          the page number
     * @param pageSize      the page size
     * @param isFreshSearch is this a fresh search (to prevent keep counting the search if the client is only changing the pages)
     * @return array list that contains arrays that contain the search results -> (url - title - description)
     * @throws RemoteException RMI Exception
     */
    ArrayList<ArrayList<String>> searchWord(String word, int page, int pageSize, boolean isFreshSearch) throws RemoteException;

    /**
     * Search a set of words in the most available barrel
     * @param words         the words to search
     * @param page          the page number
     * @param pageSize      the page size
     * @param isFreshSearch is this a fresh search (to prevent keep counting the search if the client is only changing the pages)
     * @return array list that contains arrays that contain the search results -> (url - title - description), or null if error
     * @throws RemoteException RMI Exception
     */
    ArrayList<ArrayList<String>> searchWordSet(ArrayList<String> words, int page, int pageSize, boolean isFreshSearch) throws RemoteException;

    /**
     * Get system info. Namely, each registered barrel, and it's availability and average response time,
     * and the top 10 searches
     * @return array list containing the info
     */
    ArrayList<String> getSystemInfo() throws RemoteException;

    /**
     * Get father urls of the provided urls list from the most available barrel
     * @param urls the urls
     * @return array list that contains arrays that contains all the father urls of the urls (i.e. [[fatherurl1, fatherurl2], [fatherurl24, fatherurl4], ...])
     * @throws RemoteException RMI Exception
     */
    ArrayList<ArrayList<String>> getFatherUrls(ArrayList<String> urls) throws RemoteException;

    /**
     * Get father urls of the provided url from the most available barrel
     * @param url the url
     * @return array list that contains the father urls of the url (i.e. [fatherurl1, fatherurl2, ...])
     * @throws RemoteException RMI Exception
     */
    ArrayList<String> getFatherUrls(String url) throws RemoteException;
    void registerUpdateCallback(UpdateCallback client) throws RemoteException;
    void unregisterUpdateCallback(UpdateCallback client) throws RemoteException;
}
