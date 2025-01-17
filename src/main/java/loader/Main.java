package loader;

import loader.graph.Neo4JConnection;
import loader.graph.Neo4JLoader;
import loader.hazelcast.HazelcastConnection;

public class Main {
    public static void main(String[] args) {
        String neo4jUri = System.getenv("NEO4J_URI");
        String neo4jUser = System.getenv("NEO4J_USER");
        String neo4jPassword = System.getenv("NEO4J_PASSWORD");
        String hazelcastIp = System.getenv("HAZELCAST_IP");
        boolean authEnabled = false;

        System.out.println("NEO4J_URI: " + neo4jUri);
        System.out.println("NEO4J_USER: " + neo4jUser);
        System.out.println("NEO4J_PASSWORD: " + neo4jPassword);

        if (neo4jUri == null || neo4jUser == null || neo4jPassword == null) {
            System.err.println("Error: Neo4J environment variables are not set.");
            return;
        }

        if (hazelcastIp == null || hazelcastIp.isEmpty()) {
            System.err.println("Error: HAZELCAST_IP environment variable is not set.");
            return;
        }

        HazelcastConnection hazelcastConnection = new HazelcastConnection(hazelcastIp);
        Neo4JConnection neo4jConnection = new Neo4JConnection(neo4jUri, neo4jUser, neo4jPassword,authEnabled);

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
