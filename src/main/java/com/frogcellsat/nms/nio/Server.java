package com.frogcellsat.nms.nio;

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Calendar;
import java.sql.Timestamp;  
import java.util.Iterator;
import java.util.Set;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Server {
	
	private static final String HOSTIP = "192.168.1.11";
	private static final int PORT = 8888;
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;
    // map serial number with client channel 
    private Map<SocketChannel, String> channelSerialNumberMap;
    private static final Logger logger = Logger.getLogger(Server.class.getName());
    public int client_no=0;
    public static void main(String[] args) throws IOException {
    	
        Server serverHandler = new Server();
        serverHandler.configureLogger(); // Configure logger first
        serverHandler.startServer(HOSTIP, PORT);
        
    }
    
// logging configurations
public void configureLogger() {
        try {
            // Create logs directory if it doesn't exist
            Path logDirPath = Paths.get("logs");
            if (!Files.exists(logDirPath)) {
                Files.createDirectories(logDirPath);
            }

            // Create a FileHandler to write log messages to a file
            FileHandler fileHandler = new FileHandler("logs/logfile.txt");
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

public void startServer(String ipAddress, int port) throws IOException {
	
        initializeSelector();
        createServerSocketChannel(ipAddress, port);
        
   
        channelSerialNumberMap = new HashMap<>(); // Initialize the map
        
        logger.info("Server started on port " + port);
        System.out.println("Server started on port " + port);
        
        SocketChannel clientchannel = null;
        String clientIP = null;
        Timestamp t = null;
        boolean serverRunning = true;
        
        while (serverRunning) {
            try {
            	 int readyChannels = selector.select();
                 if (readyChannels == 0) {
                     continue; // No channels are ready, continue to the next iteration
                 }
            } 
            catch (IOException e) {
                logger.log(Level.SEVERE, "Error selecting IO channels, server might be broken", e);
                continue;
            }
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                
                if (key.isAcceptable()) {
                    clientchannel = acceptNewConnection(key);
                    InetSocketAddress remoteAddress = (InetSocketAddress)clientchannel.getRemoteAddress();
                    clientIP = remoteAddress.toString();		
                    Calendar calendar = Calendar.getInstance();
                    t = new Timestamp(calendar.getTime().getTime());
                } 
                
                else if (key.isReadable()) {
                    readDataFromClient(key,clientIP,t);
                }
                keyIterator.remove();
            }
        }   
	}

private void initializeSelector() throws IOException {
        selector = Selector.open();
    }

private void createServerSocketChannel(String ipAddress, int port) throws IOException {
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(ipAddress, port));
        
        Thread selectionThread = new Thread(new SelectionLoop(serverChannel, newConnections, connectionEvents),name:"SelectionLoop");
        selectionThread.start();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    } 
    
private SocketChannel acceptNewConnection(SelectionKey key) throws IOException {
    	SocketChannel clientChannel=null;
    	String remoteAddress=null;
    	try {
    		
        	ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
            clientChannel = serverChannel.accept();
            remoteAddress = clientChannel.getRemoteAddress().toString();
            System.out.println("New Connection accepted: " + remoteAddress + " client number : " + client_no++);
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);
            
        }
        catch(IOException e) {
        	 logger.log(Level.WARNING, "Error accepting connection", e);
        }
    	logger.info("Connection from " + remoteAddress);
    	
           
        return clientChannel;
    } 

// read data from client
private void readDataFromClient(SelectionKey key,String clientIP, Timestamp t) throws IOException {   	
    SocketChannel clientChannel = (SocketChannel) key.channel();
    try {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int bytesRead = clientChannel.read(buffer);
        
        if (bytesRead == -1) {
            // Client has closed the connection
            key.cancel();
            clientChannel.close();
            String serialNumber = channelSerialNumberMap.get(clientChannel);
            
            if (serialNumber != null) {
                // Update connection status to "disconnected" in the database
                String connectionStatus = "disconnected";
                DBConfig dbConfig = new DBConfig();
                dbConfig.updateConnectionStatus(serialNumber, connectionStatus);
                // Remove the mapping from the map
                channelSerialNumberMap.remove(clientChannel);
          }
            System.out.println("Connection closed by client: " + clientChannel.getRemoteAddress());
           
        } 
        else if (bytesRead > 0) {
        	// try reading the input from user
        	try {
                buffer.flip();
                byte[] data = new byte[buffer.limit()];
                buffer.get(data);
                String receivedMessage = new String(data);
                System.out.println("Received message from client: " + receivedMessage);
                parseData(data,clientIP,t);
                // Send a response back to the client
                String response = "Server response: " + receivedMessage;
                ByteBuffer responseBuffer = ByteBuffer.wrap(response.getBytes());
                clientChannel.write(responseBuffer);
                buffer.clear();
        	}
        	// log error otherwise 
        	catch (Exception e) {
        		e.printStackTrace();
        		logger.log(Level.WARNING, "Interrupted while reading data from client");
        	}
       }
    } 
    catch (IOException e) {
        // Handle the exception appropriately
        key.cancel();
        close(clientChannel);
        String serialNumber = channelSerialNumberMap.get(clientChannel);
        
        if (serialNumber != null) {
            // Update connection status to "disconnected" when client socket is closed
        	DBConfig dbConfig = new DBConfig();
            dbConfig.updateConnectionStatus(serialNumber, "disconnected");
            // Remove the mapping from the map
            channelSerialNumberMap.remove(clientChannel);
        }  
       // e.printStackTrace();
        logger.log(Level.INFO,"Client disconnected abruptly ! ");
    }
}

// parse that data to extract useful information
public void parseData(byte[] data,String clientIP,Timestamp t) {
		// convert data into string
		String inputString = new String(data);
		// change this logic here 
		int secondLastCommaIndex = inputString.lastIndexOf(",", inputString.lastIndexOf(",") - 1);
		int lastCommaIndex = inputString.lastIndexOf(",");
		String extractedValue = null;
		if (secondLastCommaIndex != -1 && lastCommaIndex != -1) {
		    extractedValue = inputString.substring(secondLastCommaIndex + 1, lastCommaIndex);
		}
		// this serial number needs to be passed to another function to get stored in the map 
		String extractedPartNumber = "65";
		String connectionStatus = "connected";
		DBConfig.storeDataInDatabase(extractedValue,extractedPartNumber,clientIP,t,connectionStatus);	
		
        // Store the mapping of client channel and serial number
        SocketChannel clientChannel = (SocketChannel) selector.selectedKeys().iterator().next().channel();
        channelSerialNumberMap.put(clientChannel, extractedValue);
	}

// close the channel 
private void close(SocketChannel clientchannel) {
    	  try {
    		  logger.info(clientchannel + " has client closed the connection");
    		  clientchannel.close();
          } catch (IOException e) {
        	  e.printStackTrace();
              logger.log(Level.WARNING, "Error closing connection", e);
          }
    }
}
