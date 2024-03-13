import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

public class IndexStorageBarrel extends Thread{
    private AdaptiveRadixTree art;
    public final UUID uuid = UUID.randomUUID();
    private final String multicastAddress;
    private final int port;
    private final int multicastServerConnectMaxRetries = 5;
    private final int retryDelay = 5;


    private int getMulticastMessageSize(MulticastSocket socket){
        byte[] messageSize = new byte[Integer.BYTES];
        DatagramPacket packet = new DatagramPacket(messageSize, messageSize.length);
        try{
            socket.receive(packet);
        } catch (IOException e){
            log("Error receiving multicast message size.");
            // TODO trigger sync between barrels
        }

        return messageSize[0];
    }


    private String getMulticastMessage(MulticastSocket socket, int messageSize){
        byte[] dataBuffer = new byte[messageSize];
        DatagramPacket packet = new DatagramPacket(dataBuffer, dataBuffer.length);
        try{
            socket.receive(packet);
        } catch (IOException e){
            log("Error receiving multicast message size.");
            // TODO trigger sync between barrels
        }

        return new String(packet.getData(), 0, packet.getLength());
    }


    private MulticastSocket setupMulticastConn(){
        MulticastSocket socket = null;
        int attempts = 0;
        while (attempts < multicastServerConnectMaxRetries) {
            try {
                socket = new MulticastSocket(port);
                InetAddress group = InetAddress.getByName(multicastAddress);
                socket.joinGroup(group);

                return socket;
            } catch (IOException e) {
                log("Error connecting to multicast group. Retrying in "+ retryDelay +"s...");
                attempts++;
                try {
                    Thread.sleep(retryDelay); // wait before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log("Interrupted during retry wait! Interrupting...");
                    interrupt();
                }
            }
        }

        // if the connection wasn't successful
        if(socket != null) socket.close();
        log("Error connecting to multicast group after " + multicastServerConnectMaxRetries + " attempts! Exiting...");
        interrupt();
        return null;
    }


    public void run(){
        log("UP!");

        MulticastSocket socket = setupMulticastConn();
        if(socket == null) return;
        log("Successfully joined multicast group!");

        while(true){
            /* Two-Step Reception */
            int messageSize = getMulticastMessageSize(socket);
            String message = getMulticastMessage(socket, messageSize);
            log("Received message: " + message);
            // TODO treat the data and send it to barrel, if succeded
        }
    }

    private void log(String text){
        System.out.println("[BARREL " + uuid.toString().substring(0, 8) + "] " + text);
    }

    public IndexStorageBarrel(String multicastAddress, int port){
        this.art = new AdaptiveRadixTree();
        this.multicastAddress = multicastAddress;
        this.port = port;
    }

    public static class Builder{
        private String multicastAddress = "224.3.2.1";
        private int port = 4321;

        public Builder multicastAddress(String multicastAddress){
            this.multicastAddress = multicastAddress;
            return this;
        }

        public Builder port(int port){
            this.port = port;
            return this;
        }

        public IndexStorageBarrel build(){
            return new IndexStorageBarrel(multicastAddress, port);
        }
    }


    public void insert(String word, int linkIndex){
        art.insert(word, linkIndex);
    }

    public ArrayList<Long> getLinkIndices(String word){
        return art.find(word);
    }
}