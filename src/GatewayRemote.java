import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface GatewayRemote extends Remote{
    char getDelimiter() throws RemoteException;
    int getCrawlingMaxDepth() throws RemoteException;

    String getMulticastAddress() throws RemoteException;

    int getPort() throws RemoteException;
    RawUrl getUrlFromDeque() throws InterruptedException, RemoteException;
    void addUrlToDeque(RawUrl rawUrl) throws RemoteException;
    void addUrlsToDeque(ArrayList<RawUrl> rawUrls) throws RemoteException;
    void incrementParsedUrls() throws RemoteException;
    long getParsedUrls() throws RemoteException;
    long incrementAndGetParsedUrls() throws RemoteException;
    void registerBarrel(String barrelEndpoint) throws RemoteException;
    void unregisterBarrel(String barrelEndpoint) throws RemoteException;
    ArrayList<String> getRegisteredBarrels() throws RemoteException;
}
