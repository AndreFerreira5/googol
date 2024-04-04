import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public interface IndexStorageBarrelRemote extends Remote{
    ArrayList<ArrayList<String>> searchWord(String word) throws RemoteException;
    ArrayList<ArrayList<String>> searchWords(ArrayList<String> words) throws RemoteException;
    ArrayList<ArrayList<String>> searchWordSet(ArrayList<String> words) throws RemoteException;
    void exportBarrel() throws RemoteException;
    AdaptiveRadixTree getArt() throws RemoteException;
    ConcurrentHashMap<ParsedUrlIdPair, ParsedUrl> getParsedUrlsMap() throws RemoteException;
    ConcurrentHashMap<String, ParsedUrlIdPair> getUrlToUrlKeyPairMap() throws RemoteException;
    ConcurrentHashMap<Long, ParsedUrlIdPair> getIdToUrlKeyPairMap() throws RemoteException;
    double getAvailability() throws RemoteException;
    ArrayList<ArrayList<String>> getFatherUrls(ArrayList<String> urls) throws RemoteException;
}
