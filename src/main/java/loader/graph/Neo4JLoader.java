package loader.graph;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.map.IMap;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Neo4JLoader {

    private final Neo4JConnection neo4jConnection;
    private final IMap<String, String> wordsMap;
    private final IMap<String, String> graphMap;
    private final ObjectMapper objectMapper;

    public Neo4JLoader(Neo4JConnection neo4jConnection, IMap<String, String> wordsMap, IMap<String, String> graphMap) {
        this.neo4jConnection = neo4jConnection;
        this.wordsMap = wordsMap;
        this.graphMap = graphMap;
        this.objectMapper = new ObjectMapper();
    }

    public void processGraphMap() {
        System.out.println("Processing graph_map to create relationships in Neo4J...");

        Set<String> keys = graphMap.keySet();

        try (Session session = neo4jConnection.getSession()) {
            for (String key : keys) {
                try (Transaction tx = session.beginTransaction()) {
                    ensureNodeExists(tx, key);

                    String connectedWordsJson = graphMap.get(key);
                    List<String> connectedWords = objectMapper.readValue(connectedWordsJson, new TypeReference<>() {});

                    for (String relatedWord : connectedWords) {
                        ensureNodeExists(tx, relatedWord);

                        double weight = calculateFrequencyWeight(key, relatedWord);

                        createRelationship(tx, key, relatedWord, weight);
                    }

                    tx.commit();
                    System.out.println("Processed word '" + key + "' with connections.");
                } catch (Exception e) {
                    System.err.println("Failed to process word '" + key + "': " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        System.out.println("All relationships have been created in Neo4J.");
    }

    private void ensureNodeExists(Transaction tx, String word) {
        String query = "MERGE (w:Word {word: $word}) SET w.createdAt = timestamp(), w.updatedAt = timestamp()";
        tx.run(query, org.neo4j.driver.Values.parameters("word", word));
    }

    private void createRelationship(Transaction tx, String source, String target, double weight) {

        String sourceWord = source.compareTo(target) <= 0 ? source : target;
        String targetWord = source.compareTo(target) > 0 ? source : target;

        String query = """
                MATCH (source:Word {word: $source})
                MATCH (target:Word {word: $target})
                MERGE (source)-[r:RELATED_TO]->(target)
                ON CREATE SET r.weight = $weight, r.createdAt = timestamp()
                ON MATCH SET r.updatedAt = timestamp()
                """;
        tx.run(query, org.neo4j.driver.Values.parameters("source", sourceWord, "target", targetWord, "weight", weight));
    }

    private double calculateFrequencyWeight(String word1, String word2) {
        double freq1 = getWordFrequency(word1);
        double freq2 = getWordFrequency(word2);
        return (freq1 + freq2) / 2.0;
    }

    private double getWordFrequency(String word) {
        try {
            String wordDataJson = wordsMap.get(word);
            if (wordDataJson != null) {
                List<Map<String, Object>> usages = objectMapper.readValue(wordDataJson, new TypeReference<>() {});
                return usages.stream()
                        .mapToDouble(usage -> (int) usage.getOrDefault("frequency", 0))
                        .sum();
            }
        } catch (Exception e) {
            System.err.println("Error retrieving frequency for word '" + word + "': " + e.getMessage());
        }
        return 0.0;
    }
}
