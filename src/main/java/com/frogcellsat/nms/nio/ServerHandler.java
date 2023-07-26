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

public class ServerHandler {
	
	private static final String HOSTIP = "192.168.1.11";
	private static final int PORT = 8888;
    private Selector selector;
    private ServerSocketChannel serverSocketChannel;
    // map serial number with client channel 
    private Map<SocketChannel, String> channelSerialNumberMap;
    private static final Logger logger = Logger.getLogger(ServerHandler.class.getName());
    public int client_no=0;
    public static void main(String[] args) throws IOException {
    	
        ServerHandler serverHandler = new ServerHandler();
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
	int read;
	ByteBuffer buffer;
    try {
    	// assuming client exchanges some data
    	// try reading from the channel
        buffer = ByteBuffer.allocate(1024);
        read = clientChannel.read(buffer);
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
    
    catch (IOException e) {
        read = -1;
    }
    if (read == -1) {
    	// client closed the connection 
        key.cancel();
        close(clientChannel);
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
    	      // check if sno of client exists in database and update connection status
    		  // if there exists one 
    	      String serialNumber = channelSerialNumberMap.get(clientchannel);
    	      // Update connection status to "disconnected" when client socket is closed
    	      DBConfig dbConfig = new DBConfig();
    	      dbConfig.updateConnectionStatus(serialNumber, "disconnected");
    	      // Remove the mapping from the map
    	      channelSerialNumberMap.remove(clientchannel);
    		  
          } 	  
    	  catch (IOException e) {
        	  e.printStackTrace();
              logger.log(Level.WARNING, "Error closing connection", e);
          }
    }
}
