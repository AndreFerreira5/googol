import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class Client {
    private static final int maxRetries = 5;
    private static final String[] AVAILABLE_COMMANDS = {"index",
                                                        "search",
                                                        "exit"
                                                        };
    private static final String[] COMMANDS_DESCRIPTION = {"Index a provided Url (i.e \"index https://example.com\")",
                                                            "Search Urls by word(s) (i.e \"search word1 word2\")",
                                                            "Close client"
                                                            };
    private static GatewayRemote connectToGatewayRMI(){
        try {
            return (GatewayRemote) Naming.lookup("//localhost/GatewayService");
        } catch (Exception e) {
            System.out.println("GatewayClient exception: " + e.getMessage());
            return null;
        }
    }


    private static void printAvailableCommands(){
        System.out.println("AVAILABLE COMMANDS");
        for(int i=0; i<AVAILABLE_COMMANDS.length; i++){
            System.out.println(AVAILABLE_COMMANDS[i] + " - " + COMMANDS_DESCRIPTION[i]);
        }
    }


    public static void main(String[] args){
        Scanner scanner = new Scanner(System.in);

        // setup gateway RMI
        GatewayRemote gatewayRemote = connectToGatewayRMI();
        if(gatewayRemote == null) System.exit(1);
        System.out.println("Successfully connected to gateway!");

        System.out.println("-----WELCOME-----");
        printAvailableCommands();

        while(true){
            System.out.print(">");
            String input  = scanner.nextLine();
            String[] splitInput = input.split("\\s+");

            if (splitInput.length > 0 && Arrays.asList(AVAILABLE_COMMANDS).contains(splitInput[0])) {
                switch(splitInput[0]){
                    case "index":
                        if(splitInput.length > 2){ // when indexing more than 1 url
                            ArrayList<String> urls = new ArrayList<>(Arrays.asList(splitInput).subList(1, splitInput.length));

                            boolean added = false;
                            for(int i=0; i<maxRetries; i++){
                                try {
                                    gatewayRemote.addUrlsToUrlsDeque(urls);
                                    added = true;
                                    break;
                                } catch (RemoteException ignored){}
                            }
                            if(!added) System.out.println("Error adding urls to deque!");

                        } else if (splitInput.length == 2){ // when indexing only one url
                            boolean added = false;
                            for(int i=0; i<maxRetries; i++){
                                try {
                                    gatewayRemote.addUrlToUrlsDeque(splitInput[1]);
                                    added = true;
                                    break;
                                } catch (RemoteException ignored){}
                            }
                            if(!added) System.out.println("Error adding url to deque!");
                        } else { // when no url is provided
                            System.out.println("Missing url to index");
                        }

                        break;
                    case "search":
                        ArrayList<ArrayList<String>> response = null;
                        if(splitInput.length > 2){ // when searching more than one word
                            ArrayList<String> words = new ArrayList<>(Arrays.asList(splitInput).subList(1, splitInput.length));

                            boolean added = false;
                            for(int i=0; i<maxRetries; i++){
                                try {
                                    response = gatewayRemote.searchWords(words);
                                    added = true;
                                    break;
                                } catch (RemoteException ignored){}
                            }
                            if(!added) System.out.println("Error searching!");
                        } else if (splitInput.length == 2){ // when searching only one word
                            boolean added = false;
                            for(int i=0; i<maxRetries; i++){
                                try {
                                    response = gatewayRemote.searchWord(splitInput[1]);
                                    added = true;
                                    break;
                                } catch (RemoteException ignored){
                                }
                            }
                            if(!added) System.out.println("Error searching!");

                        } else { // when no word is provided
                            System.out.println("Missing word(s) to search");
                            break;
                        }

                        if(response == null) System.out.println("No results found");
                        // TODO maybe group the results by 10 here
                        else{
                            for (ArrayList<String> strings : response) {
                                System.out.println(strings);
                            }
                        }
                        break;
                    case "exit":
                        System.out.println("Exiting client...");
                        System.exit(0);
                }
            } else {
                System.out.println("Invalid command: " + splitInput[0]);
                printAvailableCommands();
            }
        }
    }
}
