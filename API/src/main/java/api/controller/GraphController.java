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
            MATCH path = (source:Word {word: $source})-[:RELATED_TO*..20]-(target:Word {word: $target})
            WHERE all(n IN nodes(path) WHERE size([m IN nodes(path) WHERE m = n]) = 1)
            RETURN nodes(path) AS nodes, relationships(path) AS relationships, 
                reduce(totalWeight = 0, r IN relationships(path) | totalWeight + r.weight) AS total_weight
            ORDER BY total_weight DESC
            LIMIT 100
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

    @GetMapping("/longest-path")
    public Map<String, Object> getLongestPath(@RequestParam String source, @RequestParam String target) {
        Map<String, Object> response = new HashMap<>();
        String query = """
            MATCH path = (source:Word {word: $source})-[:RELATED_TO*..20]-(target:Word {word: $target})
            WHERE all(n IN nodes(path) WHERE size([m IN nodes(path) WHERE m = n]) = 1)
            RETURN [node IN nodes(path) | node.word] AS nodes, 
                [rel IN relationships(path) | rel.weight] AS weights,
                reduce(totalWeight = 0, r IN relationships(path) | totalWeight + r.weight) AS total_weight
            ORDER BY total_weight DESC
            LIMIT 1
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

    @GetMapping("/clusters")
    public Map<String, Object> getClusters() {
        Map<String, Object> response = new HashMap<>();
        String query = """
            MATCH (n:Word)-[:RELATED_TO*]-(m:Word)
            WITH id(n) AS cluster_id, n.word AS root_word, collect(DISTINCT m.word) AS connected_words
            RETURN cluster_id, [root_word] + connected_words AS cluster_nodes
        """;

        try (Session session = neo4jConnection.getSession()) {
            Result result = session.run(query);

            List<Map<String, Object>> clusters = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                clusters.add(Map.of(
                        "component_id", record.get("cluster_id").asInt(),
                        "nodes", record.get("cluster_nodes").asList()
                ));
            }

            if (clusters.isEmpty()) {
                response.put("message", "No clusters found.");
            } else {
                response.put("clusters", clusters);
            }
        } catch (Exception e) {
            response.put("error", e.getMessage());
        }

        return response;
    }

    @GetMapping("/high-degree-nodes")
    public Map<String, Object> getHighDegreeNodes() {
        Map<String, Object> response = new HashMap<>();
        String query = """
            MATCH (n:Word)-[r]-()
            WITH n, count(r) AS degree
            WHERE degree >= 8
            RETURN n.word AS word, degree
            ORDER BY degree DESC
        """;

        try (Session session = neo4jConnection.getSession()) {
            Result result = session.run(query);

            List<Map<String, Object>> nodes = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                nodes.add(Map.of(
                        "word", record.get("word").asString(),
                        "degree", record.get("degree").asInt()
                ));
            }

            if (nodes.isEmpty()) {
                response.put("message", "No nodes found with high degree of connectivity.");
            } else {
                response.put("high_degree_nodes", nodes);
            }
        } catch (Exception e) {
            response.put("error", e.getMessage());
        }

        return response;
    }

    @GetMapping("/nodes-by-degree/{degree}")
    public Map<String, Object> getNodesByDegree(@PathVariable int degree) {
        Map<String, Object> response = new HashMap<>();
        String query = """
            MATCH (n:Word)-[r]-()
            WITH n, count(r) AS degree
            WHERE degree = $degree
            RETURN n.word AS word, degree
        """;

        try (Session session = neo4jConnection.getSession()) {
            Result result = session.run(query, Map.of("degree", degree));

            List<Map<String, Object>> nodes = new ArrayList<>();
            while (result.hasNext()) {
                Record record = result.next();
                nodes.add(Map.of(
                        "word", record.get("word").asString(),
                        "degree", record.get("degree").asInt()
                ));
            }

            if (nodes.isEmpty()) {
                response.put("message", "No nodes found with degree " + degree + ".");
            } else {
                response.put("nodes", nodes);
            }
        } catch (Exception e) {
            response.put("error", e.getMessage());
        }

        return response;
    }
}