import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.rmi.ConnectException;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Properties;
import java.util.Scanner;


class ClientConfigLoader {
    private static final Properties properties = new Properties();

    public static class ConfigurationException extends RuntimeException {
        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static{
        loadProperties();
    }

    private static void loadProperties(){
        String filePath = "config/client.properties";
        try (InputStream input = new FileInputStream(filePath)){
            properties.load(input);
        } catch (IOException e){
            throw new ConfigurationException("Failed to load configuration properties", e);
        }
    }

    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
}

public class Client {
    private static final int maxRetries = 5;
    private static final int retryDelay = 1000;
    private static GatewayRemote gatewayRemote;
    private static String gatewayEndpoint;
    private static final String[] AVAILABLE_COMMANDS = {"help",
                                                        "clear",
                                                        "index",
                                                        "search",
                                                        "fathers",
                                                        "exit",
                                                        "status"
                                                        };
    private static final String[] COMMANDS_DESCRIPTION = {  "Display available commands",
                                                            "Clear screen",
                                                            "Index a provided Url (i.e \"index https://example.com\")",
                                                            "Search Urls by word(s) (i.e \"search word1 word2\")",
                                                            "Get father urls of the provided url (i.e \"fathers https://example.com\")",
                                                            "Close client",
                                                            "Get system status"
                                                            };


    /*
    public static <T> T callGatewayRMI(Supplier<T> action, String failureMessage) {
        T result = null;
        boolean success = false;

        for(int i = 0; i < maxRetries; i++) {
            try {
                result = action.get();
                success = true;
                break;
            } catch (ConnectException e) {
                reconnectToGatewayRMI();
                i--;
            } catch (RemoteException ignored) {}
        }

        if(!success) {
            System.out.println(failureMessage);
            return null;
        }

        return result;
    }*/


    private static GatewayRemote connectToGatewayRMI(){
        while(true){
            try {
                return (GatewayRemote) Naming.lookup(gatewayEndpoint);
            } catch (Exception e) {
                System.out.println("Failed to connect to Gateway! Retrying in " + retryDelay + " seconds...");
                try{
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ignored){
                    Thread.currentThread().interrupt();
                    return null;
                }

            }
        }
    }


    private static void reconnectToGatewayRMI(){
        System.out.println("Reconnecting to gateway...");
        gatewayRemote = null;
        while(gatewayRemote == null){
            gatewayRemote = connectToGatewayRMI();
            try {
                Thread.sleep(retryDelay);
            } catch (InterruptedException ignored) {}
        }
        System.out.println("Reconnected!");
    }


    private static void printAvailableCommands(){
        System.out.println("AVAILABLE COMMANDS");
        for(int i=0; i<AVAILABLE_COMMANDS.length; i++){
            System.out.println("\t" + AVAILABLE_COMMANDS[i] + " - " + COMMANDS_DESCRIPTION[i]);
        }
    }


    private static void indexUrls(String[] providedUrls){
        if(providedUrls.length > 2){ // when indexing more than 1 url
            ArrayList<String> urls = new ArrayList<>(Arrays.asList(providedUrls).subList(1, providedUrls.length));

            ArrayList<Integer> result = null;
            boolean added = false;
            for(int i=0; i<maxRetries; i++){
                try {
                    result = gatewayRemote.addUrlsToUrlsDeque(urls);
                    added = true;
                    break;
                } catch( ConnectException e){
                    reconnectToGatewayRMI();
                    i--;
                } catch (RemoteException ignored){}
            }
            if(!added) System.out.println("Error adding urls to deque!");
            else{
                if(result != null){
                    System.out.print("Not indexed bad URLs: ");
                    for (Integer integer : result) {
                        System.out.print(urls.get(integer) + " ");
                    }
                    System.out.println();
                }
            }

        } else if (providedUrls.length == 2){ // when indexing only one url
            boolean result = false;
            boolean added = false;
            for(int i=0; i<maxRetries; i++){
                try {
                    result = gatewayRemote.addUrlToUrlsDeque(providedUrls[1]);
                    added = true;
                    break;
                } catch( ConnectException e){
                    reconnectToGatewayRMI();
                    i--;
                } catch (RemoteException ignored){}
            }
            if(!added) System.out.println("Error adding url to deque!");
            else{
                if(result == false) System.out.println("Bad url, not indexed!");
            }
        } else { // when no url is provided
            System.out.println("Missing url to index");
        }
    }


    private static void searchWords(String[] providedWords){
        boolean isFreshSearch = true;
        ArrayList<ArrayList<String>> response;
        /*
        if(providedWords.length > 2){ // when searching more than one word
            ArrayList<String> words = new ArrayList<>(Arrays.asList(providedWords).subList(1, providedWords.length));

            boolean added = false;
            for(int i=0; i<maxRetries; i++){
                try {
                    response = gatewayRemote.searchWordSet(words);
                    added = true;
                    break;
                } catch( ConnectException e){
                    reconnectToGatewayRMI();
                    i--;
                } catch (RemoteException ignored){}
            }
            if(!added) System.out.println("Error searching!");
        } else if (providedWords.length == 2){ // when searching only one word
            boolean added = false;
            for(int i=0; i<maxRetries; i++){
                try {
                    response = gatewayRemote.searchWord(providedWords[1]);
                    added = true;
                    break;
                } catch( ConnectException e){
                    reconnectToGatewayRMI();
                    i--;
                } catch (RemoteException ignored){
                }
            }
            if(!added) System.out.println("Error searching!");

        } else { // when no word is provided
            System.out.println("Missing word(s) to search");
            return;
        }

        if(response == null) System.out.println("No results found");
        else{*/
            ArrayList<String> words = new ArrayList<>(Arrays.asList(providedWords).subList(1, providedWords.length));
            int page = 0;
            final int pageSize = 10;
            Scanner pageScanner = new Scanner(System.in);
            boolean keepPaginating = true;
            boolean showFatherUrls = false;

            while(keepPaginating){
                response = null;
                int start = page * pageSize;
                //int end = Math.min(start + pageSize, response.size());

                System.out.print("\033[H\033[2J");
                System.out.flush();

                boolean success = false;
                for (int i = 0; i < maxRetries; i++) {
                    try {
                        if (words.size() > 1) {
                            response = gatewayRemote.searchWordSet(words, page, pageSize, isFreshSearch);
                        } else {
                            response = gatewayRemote.searchWord(words.get(0), page, pageSize, isFreshSearch);
                        }
                        success = true;
                        break;
                    } catch (ConnectException e) {
                        reconnectToGatewayRMI();
                        i--;
                    } catch (RemoteException ignored) {
                    }
                }
                if(!success){
                    System.out.println("Error searching!");
                    return;
                } else if(response == null){
                    System.out.println("No results found");
                    return;
                }

                isFreshSearch = false;
                int totalPagesNum = Integer.parseInt(response.get(response.size()-1).get(0));

                //System.out.println("-----PAGE " + (page + 1) + " of " + (response.size() / pageSize + 1) + "-----");
                System.out.println("-----PAGE " + (page+1) + " of " + totalPagesNum + "-----");
                if(showFatherUrls){
                    ArrayList<String> pageLines = new ArrayList<>(); // array that contains the urls on the page
                    ArrayList<ArrayList<String>> fatherUrls = new ArrayList<>(); // array that contains arrays that contains all the father urls of the urls on the page
                    for(ArrayList<String> url : response){
                        pageLines.add(url.get(0));
                    }

                    success = false;
                    for(int i=0; i<maxRetries; i++){
                        try {
                            fatherUrls = gatewayRemote.getFatherUrls(pageLines);
                            success = true;
                            break;
                        } catch( ConnectException e){
                            reconnectToGatewayRMI();
                            i--;
                        } catch (RemoteException ignored){
                        }
                    }
                    if(!success){
                        System.out.println("Error retrieving father urls!");
                        int count = 0;
                        for(ArrayList<String> url : response){
                            if(count == pageSize) continue;
                            System.out.println(start+1+count + ". " + url.get(0) + (url.get(1) == null || url.get(1).isEmpty() ? "" : " - ") + (url.get(2) == null || url.get(2).isEmpty() ? "" : " - " + url.get(2)));
                            count++;
                        }
                    } else if(fatherUrls == null || fatherUrls.isEmpty()){
                        System.out.println("No father urls found!");
                        int count = 0;
                        for(ArrayList<String> url : response){
                            if(count == pageSize) continue;
                            System.out.println(start+1+count + ". " + url.get(0) + (url.get(1) == null || url.get(1).isEmpty() ? "" : " - ") + (url.get(2) == null || url.get(2).isEmpty() ? "" : " - " + url.get(2)));
                            count++;
                        }
                    }
                    else{
                        int count = 0;
                        for(ArrayList<String> url : response){
                            if(count == pageSize) continue;
                            System.out.println(start+1+count + ". " + url.get(0) + (url.get(1) == null || url.get(1).isEmpty() ? "" : " - ") + (url.get(2) == null || url.get(2).isEmpty() ? "" : " - " + url.get(2)));
                            System.out.println(fatherUrls.get(count).size() + " Father URLs: ");
                            for(String fatherUrl : fatherUrls.get(count)){
                                System.out.println("\t" + fatherUrl);
                            }
                            count++;
                        }
                    }

                } else {
                    int count = 0;
                    for(ArrayList<String> url : response){
                        if(count == pageSize) continue;
                        System.out.println(start+1+count + ". " + url.get(0) + (url.get(1) == null || url.get(1).isEmpty() ? "" : " - ") + (url.get(2) == null || url.get(2).isEmpty() ? "" : " - " + url.get(2)));
                        count++;
                    }
                    /*
                    for(int i=start; i<end; i++){
                        ArrayList<String> pageLine = response.get(i);
                        System.out.println(i+1 + ". " + pageLine.get(0) + (pageLine.get(1) == null || pageLine.get(1).isEmpty() ? "" : " - " + pageLine.get(2)) + (pageLine.get(2) == null || pageLine.get(2).isEmpty() ? "" : " - " + pageLine.get(2)));
                    }*/
                }



                if (start == 0) {
                    if((page+1) == totalPagesNum){
                        System.out.print("\nexit\n(f - toggle father urls)\n>");
                    } else{
                        System.out.print("\nexit - next >\n(f - toggle father urls)\n>");
                    }
                } else if ((page+1) == totalPagesNum){
                    System.out.print("\n< prev - exit\n(f - toggle father urls)\n>");
                } else {
                    System.out.print("\n< prev - exit - next >\n(f - toggle father urls)\n>");
                }

                String pageCommand = pageScanner.nextLine();
                switch (pageCommand) {
                    case "n":
                    case "next":
                        if ((page+1) < totalPagesNum) {
                            page++;
                        }
                        break;
                    case "p":
                    case "prev":
                        if (page > 0) {
                            page--;
                        }
                        break;
                    case "f":
                        showFatherUrls = !showFatherUrls;
                        break;
                    case "e":
                    case "exit":
                        keepPaginating = false;
                        break;
                    default:
                        break;
                }

            }
        //}
    }


    private static void fatherUrls(String url){
        ArrayList<String> fatherUrls = null;
        boolean success = false;
        for(int i=0; i<maxRetries; i++){
            try {
                fatherUrls = gatewayRemote.getFatherUrls(url);
                success = true;
                break;
            } catch( ConnectException e){
                reconnectToGatewayRMI();
                i--;
            } catch (RemoteException ignored){}
        }
        if(!success) System.out.println("Error getting father urls!");
        else{
            if(fatherUrls == null) {
                System.out.println("No father urls found!");
                return;
            }

            System.out.println("-----" + url + " FATHER URLS-----");
            for(String father : fatherUrls){
                System.out.println(father);
            }
        }
    }
    private static void getSystemStatus(){
        ArrayList<String> status = null;
        boolean success = false;
        for(int i=0; i<maxRetries; i++){
            try {
                status = gatewayRemote.getSystemInfo();
                success = true;
                break;
            } catch( ConnectException e){
                reconnectToGatewayRMI();
                i--;
            } catch (RemoteException ignored){}
        }
        if(!success) System.out.println("Error getting system status!");
        else{
            System.out.println("-----SYSTEM STATUS-----");
            for(String info : status){
                System.out.println(info);
            }
        }
    }


    public static void main(String[] args){
        try{
            String gatewayHost = ClientConfigLoader.getProperty("gateway.host");
            if(gatewayHost == null){
                System.err.println("Gateway Host property not found in property file! Exiting...");
                System.exit(1);
            }
            String gatewayServiceName = ClientConfigLoader.getProperty("gateway.serviceName");
            if(gatewayServiceName == null){
                System.err.println("Gateway Service Name property not found in property file! Exiting...");
                System.exit(1);
            }

            gatewayEndpoint = "//"+gatewayHost+"/"+gatewayServiceName;

        } catch (ClientConfigLoader.ConfigurationException e){
            System.err.println("Failed to load configuration file: " + e.getMessage());
            System.err.println("Exiting...");
            System.exit(1);
        }

        Scanner scanner = new Scanner(System.in);

        // setup gateway RMI
        gatewayRemote = connectToGatewayRMI();
        if(gatewayRemote == null) System.exit(1);
        System.out.println("Successfully connected to gateway!");

        System.out.println("-----WELCOME-----");
        printAvailableCommands();

        while(true){
            System.out.print(">");
            String input  = scanner.nextLine();
            String[] splitInput = input.split("\\s+");

            if(splitInput.length == 0){
                System.out.println("Invalid command! Type \"help\" for available commands.");
                continue;
            }

            switch(splitInput[0]){
                case "help":
                    printAvailableCommands();
                    break;
                case "clear":
                    System.out.print("\033[H\033[2J");
                    System.out.flush();
                    break;
                case "index":
                    indexUrls(splitInput);
                    break;
                case "search":
                    searchWords(splitInput);
                    break;
                case "fathers":
                    fatherUrls(splitInput[1]);
                    break;
                case "status":
                    getSystemStatus();
                    break;
                case "exit":
                    System.out.println("Exiting client...");
                    System.exit(0);
                    break;
                default:
                    System.out.println("Invalid command! Type \"help\" for available commands.");
                    break;
            }
        }
    }
}