package com.frogcellsat.nms.nio;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

class SelectionLoop implements Runnable {
	
    private static final Logger LOGGER = Logger.getLogger(SelectionLoop.class.getName());
    private final Selector selector;
    private final ArrayBlockingQueue<Connection> newConnections;
    SelectionLoop(ServerSocketChannel serverSocketChannel,
                  ArrayBlockingQueue<Connection> newConnections
                  ) throws IOException {
    	
        serverSocketChannel.configureBlocking(false);
        this.selector = Selector.open();
        serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

        this.newConnections = newConnections;
    }
    @Override
    public void run() {
        while (true) {
            try {
                selector.select();
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Error selecting IO channels, server might be broken", e);
                continue;
            }
            Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
            while (selectedKeys.hasNext()) {
                SelectionKey key = selectedKeys.next();
                selectedKeys.remove();
                if (!key.isValid()) {
                    continue;
                }
                if (key.isAcceptable()) {
                    accept(key);
                } else if (key.isReadable()) {
                    read(key);
                } else if (key.isWritable()) {
                    write(key);
                }
            }
        }
    }

    private void accept(SelectionKey key) {
    	ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
    	SocketChannel clientChannel;
    	String remoteAddress;
    	
    	try {
            clientChannel = serverChannel.accept();
            remoteAddress = clientChannel.getRemoteAddress().toString();
            System.out.println("New Connection accepted: " + remoteAddress + " client number : " + client_no++);
            
        }
        catch(IOException e) {
        	 logger.log(Level.WARNING, "Error accepting connection", e);
        	 return;
        }
    	logger.info("Connection from " + remoteAddress);
        return clientChannel;

        NioSocketConnection connection;
        try {
            connection = new NioSocketConnection(selector, socketChannel, modeChangeRequestQueue);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error creating connection from SocketChannel", e);
            return;
        }

        try {
            newConnections.put(connection);
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Interrupted while putting new Connection");
        }
    }

    private void read(SelectionKey key) {
        NioSocketConnection connection = (NioSocketConnection) key.attachment();

        int read;
        try {
            read = connection.readFromChannel();
        } catch (IOException e) {
            read = -1;
        }

        if (read == -1) {
            key.cancel();
            close(connection);
        }

        try {
            connectionEvents.put(new DataReceived(connection));
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Interrupted while putting DATA ConnectionEvent");
        }
    }

    private void write(SelectionKey key) {
        NioSocketConnection connection = (NioSocketConnection) key.attachment();
        try {
            connection.writeToChannel();
        } catch (IOException e) {
            close(connection);
            return;
        }

        if (connection.shouldClose() && connection.nothingToWrite()) {
            close(connection);
        }
    }

    private void close(NioSocketConnection connection) {
        try {
            connection.channel.close();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing connection", e);
        }

        try {
            connectionEvents.put(new CloseConnection(connection));
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Interrupted while putting CLOSE ConnectionEvent");
        }
    }
}

