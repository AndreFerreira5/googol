import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface GatewayRemote extends Remote{
    char getDelimiter() throws RemoteException;
    int getCrawlingMaxDepth() throws RemoteException;

    String getMulticastAddress() throws RemoteException;

    int getPort() throws RemoteException;
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
    ArrayList<ArrayList<String>> searchWord(String word) throws RemoteException;
    //ArrayList<ArrayList<String>> searchWords(ArrayList<String> words) throws RemoteException;
    ArrayList<ArrayList<String>> searchWordSet(ArrayList<String> words) throws RemoteException;
    ArrayList<String> getSystemInfo() throws RemoteException;
    ArrayList<ArrayList<String>> getFatherUrls(ArrayList<String> urls) throws RemoteException;
    //ArrayList<String> getFatherUrls(String url) throws RemoteException;
}
