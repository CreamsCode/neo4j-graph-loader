package loader.graph;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

public class Neo4JConnection {

    private final Driver driver;

    public Neo4JConnection(String uri, String user, String password) {
        this.driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
        System.out.println("Connected to Neo4J at " + uri);
    }

    public Session getSession() {
        return driver.session();
    }

    public void close() {
        driver.close();
        System.out.println("Neo4J connection closed.");
    }
}
