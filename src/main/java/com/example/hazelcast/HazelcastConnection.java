package com.example.hazelcast;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

public class HazelcastConnection {
    private HazelcastInstance hazelcastClient;

    public HazelcastConnection(String hazelcastIp) {
        if (hazelcastIp == null || hazelcastIp.isEmpty()) {
            throw new IllegalArgumentException("Hazelcast IP address must not be null or empty");
        }

        connectWithRetry(hazelcastIp);
    }

    private void connectWithRetry(String hazelcastIp) {
        int maxRetries = 5;
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                System.out.println("Attempting to connect to Hazelcast cluster (attempt " + (attempt + 1) + "/" + maxRetries + ")...");
                ClientConfig clientConfig = new ClientConfig();
                clientConfig.getNetworkConfig()
                            .addAddress(hazelcastIp + ":5701");

                hazelcastClient = HazelcastClient.newHazelcastClient(clientConfig);
                System.out.println("Connected to Hazelcast at " + hazelcastIp + ":5701");
                return;
            } catch (Exception e) {
                System.err.println("Failed to connect to Hazelcast: " + e.getMessage());
                attempt++;
                if (attempt >= maxRetries) {
                    System.err.println("Exceeded maximum retry attempts. Unable to connect to Hazelcast.");
                    throw new IllegalStateException("Unable to connect to Hazelcast after " + maxRetries + " attempts", e);
                }

                try {
                    Thread.sleep(2000); // Wait 2 seconds before retrying
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("Retry sleep interrupted.");
                    throw new IllegalStateException("Retry process interrupted", ie);
                }
            }
        }
    }

    public HazelcastInstance getHazelcastClient() {
        if (hazelcastClient == null) {
            throw new IllegalStateException("Hazelcast client is not connected.");
        }
        return hazelcastClient;
    }

    public IMap<String, String> getWordsMap() {
        return getHazelcastClient().getMap("words_map");
    }

    public IMap<String, String> getGraphMap() {
        return getHazelcastClient().getMap("graph_map");
    }

    public void close() {
        if (hazelcastClient != null) {
            hazelcastClient.shutdown();
            System.out.println("Hazelcast client connection closed.");
        }
    }
}