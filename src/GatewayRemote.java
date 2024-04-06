import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface GatewayRemote extends Remote{
    char getParsingDelimiter() throws RemoteException;
    int getCrawlingMaxDepth() throws RemoteException;

    String getMulticastAddress() throws RemoteException;

    int getMulticastPort() throws RemoteException;
    RawUrl getUrlFromDeque() throws InterruptedException, RemoteException;
    void addUrlToUrlsDeque(RawUrl rawUrl) throws RemoteException;
    boolean addUrlToUrlsDeque(String url) throws RemoteException;
    void addRawUrlsToUrlsDeque(ArrayList<RawUrl> rawUrls) throws RemoteException;
    ArrayList<Integer> addUrlsToUrlsDeque(ArrayList<String> rawUrls) throws RemoteException;
    void incrementParsedUrls() throws RemoteException;
    long getParsedUrls() throws RemoteException;
    long incrementAndGetParsedUrls() throws RemoteException;
    void registerBarrel(String barrelEndpoint) throws RemoteException;
    void unregisterBarrel(String barrelEndpoint) throws RemoteException;
    ArrayList<String> getRegisteredBarrels() throws RemoteException;
    int getRegisteredBarrelsCount() throws RemoteException;
    String getRandomBarrelRemote() throws RemoteException;
    String getMostAvailableBarrelRemote(String callingBarrel) throws RemoteException;
    String getMostAvailableBarrelRemote() throws RemoteException;
    ArrayList<ArrayList<String>> searchWord(String word, int page, int pageSize, boolean isFreshSearch) throws RemoteException;
    ArrayList<ArrayList<String>> searchWordSet(ArrayList<String> words, int page, int pageSize, boolean isFreshSearch) throws RemoteException;
    ArrayList<String> getSystemInfo() throws RemoteException;
    ArrayList<ArrayList<String>> getFatherUrls(ArrayList<String> urls) throws RemoteException;
    ArrayList<String> getFatherUrls(String url) throws RemoteException;
}
