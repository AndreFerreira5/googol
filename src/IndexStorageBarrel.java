import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class IndexStorageBarrel extends Thread{
    private AdaptiveRadixTree art;
    public final UUID uuid = UUID.randomUUID();
    private final String multicastAddress;
    private final int port;
    private final int multicastServerConnectMaxRetries = 5;
    private final int retryDelay = 5;
    private final char DELIMITER = Gateway.DELIMITER;
    private ExecutorService executorService;


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
        //byte[] dataBuffer = new byte[messageSize];
        byte[] dataBuffer = new byte[65507];
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


    public void exportART(){
        exportART(this.art);
    }


    private static void exportART(AdaptiveRadixTree art){
        try{
            art.exportART();
        } catch(FileNotFoundException e){
            System.out.println("TREE FILE NOT FOUND! Stopping the exportation...");
        } catch(IOException e) {
            System.out.println("ERROR OPENING FILE: " + e + "\nStopping the exportation...");
        }
    }


    private static void importART(AdaptiveRadixTree art){
        try{
            art.importART();
        } catch(FileNotFoundException e){
            System.out.println("TREE FILE NOT FOUND! Skipping the importation...");
        } catch(IOException e) {
            System.out.println("ERROR OPENING FILE: " + e + "\nSkipping the importation...");
        }
    }


    private void importART(){
        try{
            this.art.importART();
        } catch(FileNotFoundException e){
            System.out.println("TREE FILE NOT FOUND! Skipping the importation...");
        } catch(IOException e) {
            System.out.println("ERROR OPENING FILE: " + e + "\nSkipping the importation...");
        }
    }


    public void run(){
        log("UP!");

        log("Importing ART...");
        importART();

        MulticastSocket socket = setupMulticastConn();
        if(socket == null) return;
        log("Successfully joined multicast group!");

        try{
            while(true){
                String message = getMulticastMessage(socket, 0);

                // spawn new runnable helper object to process the message received and submit it to thread pool
                BarrelMessageHelper helper = new BarrelMessageHelper(message, art, DELIMITER);
                executorService.submit(helper);
            }
        } catch (Exception e){
            log("Error: " + e);
        } finally {
            shutdown();
            exportART(art);
        }

    }


    public ArrayList<Long> searchWord(String word){
        return art.find(word);
    }


    public AdaptiveRadixTree getART(){
        return this.art;
    }


    public void shutdown() {
        log("Shutting down executor service...");
        executorService.shutdown(); // initiates an orderly shutdown
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow(); // cancel currently executing tasks
            }
        } catch (InterruptedException ie) {
            executorService.shutdownNow();
        }
    }


    private void log(String text){
        System.out.println("[BARREL " + uuid.toString().substring(0, 8) + "] " + text);
    }

    public IndexStorageBarrel(String multicastAddress, int port){
        this.art = new AdaptiveRadixTree();
        this.multicastAddress = multicastAddress;
        this.port = port;
        this.executorService = Executors.newCachedThreadPool();
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


    public void insert(String word, long linkIndex){
        art.insert(word, linkIndex);
    }

    public ArrayList<Long> getLinkIndices(String word){
        return art.find(word);
    }
}


class BarrelMessageHelper implements Runnable {
    private String message;
    private AdaptiveRadixTree art;
    private final char DELIMITER;

    public BarrelMessageHelper(String message, AdaptiveRadixTree art, char DELIMITER) {
        this.message = message;
        this.art = art;
        this.DELIMITER = DELIMITER;
    }

    private ArrayList<String> parseMessage(String message){
        String[] splitMessage = message.split(Pattern.quote(String.valueOf(DELIMITER)));
        return new ArrayList<>(Arrays.asList(splitMessage));
    }

    @Override
    public void run() {
        ArrayList<String> parsedMessage = parseMessage(message);
        long id = Long.parseLong(parsedMessage.get(0));
        for(int i = 1; i < parsedMessage.size(); i++){
            String word = parsedMessage.get(i);
            art.insert(word, id);
        }
    }
}