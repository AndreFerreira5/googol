import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class Client {
    private static final int maxRetries = 5;
    private static final String[] AVAILABLE_COMMANDS = {"help",
                                                        "clear",
                                                        "index",
                                                        "search",
                                                        "exit",
                                                        "status"
                                                        };
    private static final String[] COMMANDS_DESCRIPTION = {  "Display available commands",
                                                            "Clear screen",
                                                            "Index a provided Url (i.e \"index https://example.com\")",
                                                            "Search Urls by word(s) (i.e \"search word1 word2\")",
                                                            "Close client",
                                                            "Get system status"
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
            System.out.println("\t" + AVAILABLE_COMMANDS[i] + " - " + COMMANDS_DESCRIPTION[i]);
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


            switch(splitInput[0]){
                case "help":
                    printAvailableCommands();
                    break;
                case "clear":
                    System.out.print("\033[H\033[2J");
                    System.out.flush();
                    break;
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
                                response = gatewayRemote.searchWordSet(words);
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
                    else{
                        int page = 0;
                        final int pageSize = 10;
                        Scanner pageScanner = new Scanner(System.in);
                        boolean keepPaginating = true;

                        while(keepPaginating){
                            int start = page * pageSize;
                            int end = Math.min(start + pageSize, response.size());

                            System.out.print("\033[H\033[2J");
                            System.out.flush();

                            System.out.println("-----PAGE " + (page + 1) + " of " + (response.size() / pageSize + 1) + "-----");
                            for(int i=start; i<end; i++){
                                System.out.println(response.get(i));
                            }

                            if (start == 0) {
                                System.out.println("\nexit - next >\n");
                            } else if (end == response.size()){
                                System.out.println("\n< prev - exit\n");
                            } else {
                                System.out.println("\n< prev - exit - next >\n");
                            }

                            String pageCommand = pageScanner.nextLine();
                            switch (pageCommand) {
                                case "next":
                                    if (end < response.size()) {
                                        page++;
                                    }
                                    break;
                                case "prev":
                                    if (page > 0) {
                                        page--;
                                    }
                                    break;
                                case "exit":
                                    keepPaginating = false;
                                    break;
                                default:
                                    break;
                            }

                        }
                    }
                    break;
                case "status":
                    ArrayList<String> status = null;
                    boolean success = false;
                    for(int i=0; i<maxRetries; i++){
                        try {
                            status = gatewayRemote.getSystemInfo();
                            success = true;
                            break;
                        } catch (RemoteException ignored){}
                    }
                    if(!success) System.out.println("Error getting system status!");
                    else{
                        System.out.println("-----SYSTEM STATUS-----");
                        for(String info : status){
                            System.out.println(info);
                        }
                    }
                    break;
                case "exit":
                    System.out.println("Exiting client...");
                    System.exit(0);
                    break;
                default:
                    System.out.println("Invalid command: " + splitInput[0]);
                    printAvailableCommands();
                    break;
            }
        }
    }
}