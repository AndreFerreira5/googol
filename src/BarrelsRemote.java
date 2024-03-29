import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface BarrelsRemote extends Remote {
    ArrayList<ArrayList<String>> searchWord(String word) throws RemoteException;

    ArrayList<ArrayList<String>> searchWords(ArrayList<String> words) throws RemoteException;
}
