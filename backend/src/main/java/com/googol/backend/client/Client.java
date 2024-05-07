package com.googol.backend.client;

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

import com.googol.backend.gateway.GatewayRemote;


/**
 * The type Client config loader.
 */
class ClientConfigLoader {
    private static final Properties properties = new Properties();

    /**
     * The type Configuration exception.
     */
    public static class ConfigurationException extends RuntimeException {
        /**
         * Instantiates a new Configuration exception.
         *
         * @param message the message
         * @param cause   the cause
         */
        public ConfigurationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static{
        try {
            loadProperties();
        } catch (ClientConfigLoader.ConfigurationException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    private static void loadProperties(){
        String filePath = "config/client.properties";
        try (InputStream input = new FileInputStream(filePath)){
            properties.load(input);
        } catch (IOException e){
            throw new ConfigurationException("Failed to load configuration properties " + e.getMessage(), e);
        }
    }

    /**
     * Gets property.
     *
     * @param key the key
     * @return the property
     */
    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
}

/**
 * The type Client.
 */
public class Client {
    private static boolean verbosity = false; // default
    private static int maxRetries = 5; // default
    private static int retryDelay = 1000; // default
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
        // state flags
        boolean isFreshSearch = true;
        boolean didPageChange = true;
        boolean keepPaginating = true;
        boolean showFatherUrls = false;

        /* pagination state global variables used to cache data of the actual page to prevent unnecessary requests to the gateway */
        ArrayList<ArrayList<String>> response = null;
        ArrayList<ArrayList<String>> fatherUrls = null;

        // get all searched words
        ArrayList<String> words = new ArrayList<>(Arrays.asList(providedWords).subList(1, providedWords.length));

        int page = 0;
        final int pageSize = 10;
        Scanner pageScanner = new Scanner(System.in);

        while(keepPaginating){
            int start = page * pageSize;

            // clear the terminal (this might not work for all terminals/environments)
            System.out.print("\033[H\033[2J");
            System.out.flush();


            // if the page did not change, use the previous response
            // otherwise clear response to later get results from gateway
            // * this is to prevent unnecessary requests *
            if(didPageChange){
                response = null;

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
            }


            isFreshSearch = false;
            int totalPagesNum = Integer.parseInt(response.get(response.size()-1).get(0));

            System.out.println("-----PAGE " + (page+1) + " of " + totalPagesNum + "-----");
            if(showFatherUrls){
                if(!didPageChange && fatherUrls != null){
                    int count = 0;
                    for(ArrayList<String> url : response){
                        if(count == pageSize || url.size() < 3) continue;
                        System.out.println(start+1+count + ". " + url.get(0) + (url.get(1) == null || url.get(1).isEmpty() ? "" : " - ") + (url.get(2) == null || url.get(2).isEmpty() ? "" : " - " + url.get(2)));
                        System.out.println(fatherUrls.get(count).size() + " Father URLs: ");
                        for(String fatherUrl : fatherUrls.get(count)){
                            System.out.println("\t" + fatherUrl);
                        }
                        count++;
                    }
                } else {
                    ArrayList<String> pageLines = new ArrayList<>(); // array that contains the urls on the page
                    fatherUrls = new ArrayList<>(); // array that contains arrays that contains all the father urls of the urls on the page
                    for(ArrayList<String> url : response){
                        pageLines.add(url.get(0));
                    }

                    boolean success = false;
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
                    System.out.println("FATHER URLS: " + fatherUrls);
                    if(!success){
                        System.out.println("Error retrieving father urls!");
                        int count = 0;
                        for(ArrayList<String> url : response){
                            if(count == pageSize || url.size() < 3) continue;
                            System.out.println(start+1+count + ". " + url.get(0) + (url.get(1) == null || url.get(1).isEmpty() ? "" : " - ") + (url.get(2) == null || url.get(2).isEmpty() ? "" : " - " + url.get(2)));
                            count++;
                        }
                    } else if(fatherUrls == null || fatherUrls.isEmpty()){
                        System.out.println("No father urls found!");
                        int count = 0;
                        for(ArrayList<String> url : response){
                            if(count == pageSize || url.size() < 3) continue;
                            System.out.println(start+1+count + ". " + url.get(0) + (url.get(1) == null || url.get(1).isEmpty() ? "" : " - ") + (url.get(2) == null || url.get(2).isEmpty() ? "" : " - " + url.get(2)));
                            count++;
                        }
                    } else {
                        int count = 0;
                        for(ArrayList<String> url : response){
                            if(count == pageSize || url.size() < 3) continue;
                            System.out.println(start+1+count + ". " + url.get(0) + (url.get(1) == null || url.get(1).isEmpty() ? "" : " - ") + (url.get(2) == null || url.get(2).isEmpty() ? "" : " - " + url.get(2)));
                            System.out.println(fatherUrls.get(count).size() + " Father URLs: ");
                            for(String fatherUrl : fatherUrls.get(count)){
                                System.out.println("\t" + fatherUrl);
                            }
                            count++;
                        }
                    }
                }
            } else {
                int count = 0;
                for(ArrayList<String> url : response){
                    if(count == pageSize || url.size() < 3) continue;
                    System.out.println(start+1+count + ". " + url.get(0) + (url.get(1) == null || url.get(1).isEmpty() ? "" : " - ") + (url.get(2) == null || url.get(2).isEmpty() ? "" : " - " + url.get(2)));
                    count++;
                }
            }



            if (start == 0) {
                if((page+1) == totalPagesNum){
                    System.out.print("\n(e)xit\n(f - toggle father urls)\n>");
                } else{
                    System.out.print("\n(e)xit - (n)ext >\n(f - toggle father urls)\n>");
                }
            } else if ((page+1) == totalPagesNum){
                System.out.print("\n< (p)rev - (e)xit\n(f - toggle father urls)\n>");
            } else {
                System.out.print("\n< (p)rev - (e)xit - (n)ext >\n(f - toggle father urls)\n>");
            }

            String pageCommand = pageScanner.nextLine();
            switch (pageCommand) {
                case "n":
                case "next":
                    if ((page+1) < totalPagesNum) {
                        page++;
                        didPageChange = true;
                        fatherUrls = null;
                    } else {
                        didPageChange = false;
                    }
                    break;
                case "p":
                case "prev":
                    if (page > 0) {
                        page--;
                        didPageChange = true;
                        fatherUrls = null;
                    } else {
                        didPageChange = false;
                    }
                    break;
                case "f":
                    showFatherUrls = !showFatherUrls;
                    didPageChange = false;
                    break;
                case "e":
                case "exit":
                    System.out.print("\033[H\033[2J");
                    System.out.flush();
                    keepPaginating = false;
                    break;
                default:
                    if(pageCommand.isEmpty()) break;

                    try{
                        int pageNumber = Integer.parseInt(pageCommand);
                        if(pageNumber >= 0 && pageNumber <= totalPagesNum){
                            page = pageNumber-1;
                            didPageChange = true;
                            fatherUrls = null;
                        }
                    } catch (NumberFormatException ignored){}

                    break;
            }

        }
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
        ArrayList<ArrayList<String>> status = null;
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
            System.out.println("Barrels:");
            for(String info : status.get(0)){
                System.out.println("\t" + info);
            }
            System.out.println("Downloaders:");
            for(String info : status.get(1)){
                System.out.println("\t" + info);
            }
            System.out.println("Urls to process: " + status.get(2).get(0));
            System.out.println("Top 10 searches:");
            for(String info : status.get(3)){
                System.out.println("\t" + info);
            }
        }
    }


    private static void loadConfig(){
        try{
            // load verbosity
            String verbosityConfig = ClientConfigLoader.getProperty("client.verbosity");
            if(verbosityConfig == null){
                System.err.println("Verbosity not found in property file! Defaulting to " + verbosity + "...");
            } else {
                try{
                    verbosity = Integer.parseInt(verbosityConfig) == 1;
                    System.out.println("Verbosity: " + verbosity);
                } catch (NumberFormatException e){
                    System.err.println("Verbosity is not a number! Defaulting to " + verbosity + "...");
                }
            }

            if(verbosity) System.out.println("----------CONFIG----------");


            String gatewayHost = ClientConfigLoader.getProperty("gateway.host");
            if(gatewayHost == null){
                System.err.println("Gateway Host property not found in property file! Exiting...");
                System.exit(1);
            }
            if(verbosity) System.out.println("Gateway Host: " + gatewayHost);

            String gatewayServiceName = ClientConfigLoader.getProperty("gateway.serviceName");
            if(gatewayServiceName == null){
                System.err.println("Gateway Service Name property not found in property file! Exiting...");
                System.exit(1);
            }
            if(verbosity) System.out.println("Gateway Service Name: " + gatewayServiceName);

            gatewayEndpoint = "//"+gatewayHost+"/"+gatewayServiceName;
            if(verbosity) System.out.println("Gateway Endpoint: " + gatewayEndpoint);

            // load max retires num
            String  maxRetriesConfig = ClientConfigLoader.getProperty("client.maxRetries");
            if(maxRetriesConfig == null){ // if not found, set to default (defined on top of the class)
                System.err.println("Max Retries property not found in property file! Defaulting to " + maxRetries + "...");
            } else { // if found, check it
                try{
                    int maxRetriesInt = Integer.parseInt(maxRetriesConfig);
                    if (maxRetriesInt > 0) { // if max number of retries is valid
                        maxRetries = maxRetriesInt;
                        if(verbosity) System.out.println("Max Retries: " + maxRetries);
                    } else { // if max number of retries is not valid, set it to default (defined on top of the class)
                        System.out.println("Max Retries cannot be lower or equal to 0! Defaulting to " + maxRetries + "...");
                    }
                } catch (NumberFormatException e){
                    System.err.println("Max Retries is not a number! Defaulting to " + maxRetries + "...");
                }

            }

            // load retry delay
            String retryDelayProperty = ClientConfigLoader.getProperty("client.retryDelay");
            if(retryDelayProperty == null){ // if not found, set to default (defined on top of the class)
                System.err.println("Retry Delay property not found in property file! Defaulting to " + retryDelay + "...");
            } else { // if found, check it
                try {
                    int retryDelayInt = Integer.parseInt(retryDelayProperty);
                    if (retryDelayInt > 0) { // if retry delay is valid
                        retryDelay = retryDelayInt;
                        if(verbosity) System.out.println("Retry Delay: " + retryDelay);
                    } else { // if retry delay is not valid, set it to default (defined on top of the class)
                        System.out.println("Retry Delay cannot be lower or equal to 0! Defaulting to " + retryDelay + "...");
                    }
                } catch (NumberFormatException e){
                    System.err.println("Retry Delay is not a number! Defaulting to " + retryDelay + "...");
                }

            }

        } catch (ClientConfigLoader.ConfigurationException e){
            System.err.println("Failed to load configuration file: " + e.getMessage());
            System.err.println("Exiting...");
            if(verbosity) System.out.println("-------------------------\n\n");
            System.exit(1);
        }

        if(verbosity) System.out.println("-------------------------\n\n");
    }

    /**
     * Main.
     *
     * @param args the args
     */
    public static void main(String[] args){
        loadConfig();

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