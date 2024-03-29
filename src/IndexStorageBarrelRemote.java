import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface IndexStorageBarrelRemote {
    public ArrayList<ArrayList<String>> searchWord(String word) throws RemoteException;
    public ArrayList<ArrayList<String>> searchWords(ArrayList<String> words) throws RemoteException;
}
