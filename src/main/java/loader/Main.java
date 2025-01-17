package loader;

import loader.graph.Neo4JConnection;
import loader.graph.Neo4JLoader;
import loader.hazelcast.HazelcastConnection;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        String neo4jUri = System.getProperty("NEO4J_URI");
        String neo4jUser = System.getProperty("NEO4J_USER");
        String neo4jPassword = System.getProperty("NEO4J_PASSWORD");
        String hazelcastIp = System.getProperty("HAZELCAST_IP");
        boolean authEnabled = false;

        if (neo4jUri == null || neo4jUser == null || neo4jPassword == null) {
            System.err.println("Error: Neo4J environment variables are not set.");
            return;
        }

        if (hazelcastIp == null || hazelcastIp.isEmpty()) {
            System.err.println("Error: HAZELCAST_IP environment variable is not set.");
            return;
        }

        HazelcastConnection hazelcastConnection = new HazelcastConnection(hazelcastIp);
        Neo4JConnection neo4jConnection = new Neo4JConnection(neo4jUri, neo4jUser, neo4jPassword, authEnabled);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        Runnable task = () -> {
            try {
                Neo4JLoader loader = new Neo4JLoader(
                        neo4jConnection,
                        hazelcastConnection.getWordsMap(),
                        hazelcastConnection.getGraphMap()
                );
                loader.processGraphMap();
            } catch (Exception e) {
                e.printStackTrace();
            }
        };

        scheduler.scheduleAtFixedRate(task, 0, 10, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdown();
            hazelcastConnection.close();
            neo4jConnection.close();
        }));
    }
}
