package api.controller;

import java.util.*;

import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.springframework.web.bind.annotation.*;

import loader.graph.Neo4JConnection;

@RestController
@RequestMapping("/api")
public class GraphController {
    private final Neo4JConnection neo4jConnection;

    public GraphController() {
        String neo4jUri = System.getProperty("NEO4J_URI");
        String neo4jUser = System.getProperty("NEO4J_USER");
        String neo4jPassword = System.getProperty("NEO4J_PASSWORD");

        if (neo4jUri == null || neo4jUser == null || neo4jPassword == null) {
            throw new IllegalArgumentException("Neo4J properties (NEO4J_URI, NEO4J_USER, NEO4J_PASSWORD) are not set.");
        }

        this.neo4jConnection = new Neo4JConnection(neo4jUri, neo4jUser, neo4jPassword, true);
    }

    @GetMapping("/shortest-path")
    public Map<String, Object> getShortestPath(@RequestParam String source, @RequestParam String target) {
        Map<String, Object> response = new HashMap<>();
        String query = """
            MATCH path = shortestPath((source:Word {word: $source})-[*]-(target:Word {word: $target}))
            RETURN [node IN nodes(path) | node.word] AS nodes, 
                   [rel IN relationships(path) | rel.weight] AS weights,
                   reduce(totalWeight = 0, r IN relationships(path) | totalWeight + r.weight) AS total_weight
        """;

        try (Session session = neo4jConnection.getSession()) {
            Result result = session.run(query, Map.of("source", source, "target", target));
            if (result.hasNext()) {
                Record record = result.next();
                response.put("nodes", record.get("nodes").asList());
                response.put("weights", record.get("weights").asList());
                response.put("total_weight", record.get("total_weight").asDouble());
            } else {
                response.put("error", "No path found between the specified nodes.");
            }
        } catch (Exception e) {
            response.put("error", e.getMessage());
        }

        return response;
    }

    @GetMapping("/all-paths")
    public Map<String, Object> getAllPaths(@RequestParam String source, @RequestParam String target) {
        Map<String, Object> response = new HashMap<>();
        String query = """
            MATCH path = (source:Word {word: $source})-[*]-(target:Word {word: $target})
            RETURN nodes(path) AS nodes, relationships(path) AS relationships, 
                   reduce(total = 0, r IN relationships(path) | total + r.weight) AS total_weight
        """;

        try (Session session = neo4jConnection.getSession()) {
            Result result = session.run(query, Map.of("source", source, "target", target));

            List<Map<String, Object>> paths = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                Map<String, Object> path = new HashMap<>();
                path.put("nodes", record.get("nodes").asList(node -> node.get("word").asString()));
                path.put("weights", record.get("relationships").asList(rel -> rel.get("weight").asInt()));
                path.put("total_weight", record.get("total_weight").asDouble());
                paths.add(path);
            }

            if (paths.isEmpty()) {
                response.put("error", "No paths found between the specified nodes.");
            } else {
                response.put("all_paths", paths);
            }
        } catch (Exception e) {
            response.put("error", e.getMessage());
        }

        return response;
    }

    @GetMapping("/isolated-nodes")
    public Map<String, Object> getIsolatedNodes() {
        Map<String, Object> response = new HashMap<>();
        String query = """
            MATCH (n:Word)
            WHERE NOT (n)-[]-()
            RETURN n.word AS word
        """;

        try (Session session = neo4jConnection.getSession()) {
            Result result = session.run(query);

            List<String> isolatedNodes = new ArrayList<>();
            while (result.hasNext()) {
                isolatedNodes.add(result.next().get("word").asString());
            }

            if (isolatedNodes.isEmpty()) {
                response.put("message", "No isolated nodes found.");
            } else {
                response.put("isolated_nodes", isolatedNodes);
            }
        } catch (Exception e) {
            response.put("error", e.getMessage());
        }

        return response;
    }
}
