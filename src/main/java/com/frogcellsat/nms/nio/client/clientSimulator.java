package com.frogcellsat.nms.nio.client;
import java.io.IOException;
import java.io.*;  
import java.net.Socket;

class clientSimulator{
	
	 private static final int serverPORT = 8888;
	 private static final String serverHOST = "192.168.1.11";
	 
	 static public void main(String[] args) throws Exception {
		// Number of clients to create
        int numClients = 230; 
        // Create and start client threads
        for (int i = 1; i <= numClients; i++) {
            String paddedIndex = String.format("%02d", i);
            String Header = i+",64,70,2,6,4,65,0," + paddedIndex + ",0";
            Thread clientThread = new ClientThreads(serverHOST, serverPORT , Header);
            clientThread.start();
        }      
    }
}

class ClientThreads extends Thread {
	
    private String serverHost;
    private int serverPort;
    private String Header;

    public ClientThreads(String serverHost, int serverPort, String Header) {
    	// intialising client threads with host and port
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.Header=Header;
        
    }
   
    @Override
    public void run() {
    	try {
    	    Socket socket = new Socket(serverHost, serverPort);
    	    // Writing to server
    	    DataOutputStream dout = new DataOutputStream(socket.getOutputStream());
    	    dout.writeUTF(Header);
    	    dout.flush();
    	    dout.close();
    	    socket.close();
    	} catch (IOException e) {
    	    // Handle the IOException
    	    e.printStackTrace(); // or use a more appropriate error handling mechanism
    	}

    }
}
