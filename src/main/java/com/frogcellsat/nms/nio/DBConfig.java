package com.frogcellsat.nms.nio;
import java.sql.Timestamp;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class DBConfig {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/nionms";
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "1234";
    
    private static HikariDataSource dataSource;
    
    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(getDatabaseURL());
        config.setUsername(getDatabaseUsername());
        config.setPassword(getDatabasePassword());
        // Configure other HikariCP properties if needed

        dataSource = new HikariDataSource(config);
    }

    public static String getDatabaseURL() {
        return DB_URL;
    }

    public static String getDatabaseUsername() {
        return DB_USERNAME;
    }

    public static String getDatabasePassword() {
        return DB_PASSWORD;
    }
    
    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
    
	// store the data in database
	public static void storeDataInDatabase(String serialNumber, String partNumber, String clientAdd, Timestamp now,String connectionStatus) {
		try (Connection connection = DBConfig.getConnection();
	         PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM system_detail WHERE serialNumber = ?")) {
	        statement.setString(1, serialNumber);
	        try (ResultSet resultSet = statement.executeQuery()) {
	            resultSet.next();
	            int count = resultSet.getInt(1);
	            if (count > 0) {
	                // Serial number exists, update connection status and timestamp
	                try (PreparedStatement updateStatement = connection.prepareStatement(
	                        "UPDATE system_detail SET connectionStatus = ?, timeStamp = ? WHERE serialNumber = ?")) {
	                    updateStatement.setString(1, connectionStatus);
	                    updateStatement.setTimestamp(2, now);
	                    updateStatement.setString(3, serialNumber);
	                    updateStatement.executeUpdate();
	                    System.out.println("Data updated successfully in the database.");
	                }
	            } else {
	                // Serial number does not exist, insert new entry
	                try (PreparedStatement insertStatement = connection.prepareStatement(
	                    "INSERT INTO system_detail (serialNumber, partNumber, connectionStatus, timeStamp, clientIP) VALUES (?, ?, ?, ?, ?)")) {
	                    insertStatement.setString(1, serialNumber);
	                    insertStatement.setString(2, partNumber);
	                    insertStatement.setString(3, connectionStatus);
	                    insertStatement.setTimestamp(4, now);
	                    insertStatement.setString(5, clientAdd);
	                    insertStatement.executeUpdate();
	                    System.out.println("Data stored successfully in the database.");
	                }
	            }
	        }
	        
	    } catch (SQLException e) {
	    	e.printStackTrace();
	        System.out.println("Error occurred while storing data in the database: " + e.getMessage());
	    }
	}
	
	public void updateConnectionStatus(String serialNumber, String connectionStatus) {
	    try (Connection connection = DBConfig.getConnection();
	         PreparedStatement statement = connection.prepareStatement(
	                 "UPDATE system_detail SET connectionStatus = ? WHERE serialNumber = ?")) {
	        statement.setString(1, connectionStatus);
	        statement.setString(2, serialNumber);
	        int rowsAffected = statement.executeUpdate();

	        if (rowsAffected > 0) {
	            System.out.println("Connection status updated successfully.");
	        } else {
	            System.out.println("Serial number not found in the database.");
	        }
	    } catch (SQLException e) {
	    	e.printStackTrace();
	        System.out.println("Error occurred while updating connection status in the database: " + e.getMessage());
	    }
	}
}


