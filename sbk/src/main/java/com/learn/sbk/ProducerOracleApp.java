package com.learn.sbk;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class ProducerOracleApp {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        // SSH Tunnel parameters
        String sshHost = "your_ssh_host";
        int sshPort = 22; // Default SSH port
        String sshUser = "your_ssh_username";
        String sshPassword = "your_ssh_password";
        String sshJumpHost = "jump_server_host";
        int sshJumpPort = 22; // Default SSH port
        String sshJumpUser = "jump_server_username";
        String sshJumpPassword = "jump_server_password";
        int localPort = 1234; // Local port for SSH tunnel

        // Establish SSH tunnel
        try {
            establishSSHTunnel(sshHost, sshPort, sshUser, sshPassword, sshJumpHost, sshJumpPort, sshJumpUser, sshJumpPassword, localPort);
        } catch (JSchException e) {
            e.printStackTrace();
            return; // Exit if SSH tunnel cannot be established
        }

        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        KafkaProducer<String, String> producer = new KafkaProducer<>(properties);

        // JDBC Connection parameters
        String jdbcUrl = "jdbc:oracle:thin:@localhost:" + localPort + "/ORCLPDB1"; // Using local port for SSH tunnel
        String username = "your_username";
        String password = "your_password";

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            for (int i = 0; i < 10; i++) {
                ProducerRecord<String, String> message = new ProducerRecord<>("helloworld", Integer.toString(i), "Hello" + i);
                producer.send(message).get();

                // Insert message into Oracle database
                insertMessage(connection, "helloworld", Integer.toString(i), "Hello" + i);
            }
        } catch (SQLException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            producer.close();
        }
    }

    private static void establishSSHTunnel(String sshHost, int sshPort, String sshUser, String sshPassword,
                                           String sshJumpHost, int sshJumpPort, String sshJumpUser, String sshJumpPassword,
                                           int localPort) throws JSchException {
        JSch jsch = new JSch();
        Session session = jsch.getSession(sshUser, sshHost, sshPort);
        session.setPassword(sshPassword);

        // Set SSH jump server if provided
        if (sshJumpHost != null && !sshJumpHost.isEmpty()) {
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.setPortForwardingL(localPort, sshJumpHost, sshJumpPort);
        }

        session.connect();
    }

    private static void insertMessage(Connection connection, String topic, String key, String value) throws SQLException {
        String schemaName = "C##DBDASHBOARDKPK"; // Change to your schema name
        String tableName = "Dummy"; // Change to your table name
        String sql = "INSERT INTO " + schemaName + "." + tableName + " (topic, message_key, message_value) VALUES (?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, topic);
            statement.setString(2, key);
            statement.setString(3, value);
            statement.executeUpdate();
        }
    }
}
