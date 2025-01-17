package com.example;

import com.example.graph.Neo4JConnection;
import com.example.graph.Neo4JLoader;
import com.example.hazelcast.HazelcastConnection;

public class TestNeo4J {
    public static void main(String[] args) {
        String neo4jUri = System.getenv("NEO4J_URI");
        String neo4jUser = System.getenv("NEO4J_USER");
        String neo4jPassword = System.getenv("NEO4J_PASSWORD");
        String hazelcastIp = System.getenv("HAZELCAST_IP");

        if (neo4jUri == null || neo4jUser == null || neo4jPassword == null) {
            System.err.println("Error: Neo4J environment variables are not set.");
            return;
        }

        if (hazelcastIp == null || hazelcastIp.isEmpty()) {
            System.err.println("Error: HAZELCAST_IP environment variable is not set.");
            return;
        }

        HazelcastConnection hazelcastConnection = new HazelcastConnection(hazelcastIp);
        Neo4JConnection neo4jConnection = new Neo4JConnection(neo4jUri, neo4jUser, neo4jPassword);

        try {
            Neo4JLoader loader = new Neo4JLoader(
                    neo4jConnection,
                    hazelcastConnection.getWordsMap(),
                    hazelcastConnection.getGraphMap()
            );
            loader.processGraphMap();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            hazelcastConnection.close();
            neo4jConnection.close();
        }
    }
}
