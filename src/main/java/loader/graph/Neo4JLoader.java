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
    private final IMap<String, String> wordsMap;  // Map con JSON Strings
    private final IMap<String, String> graphMap;  // Map con JSON Strings
    private final ObjectMapper objectMapper;      // Para deserializar JSON

    public Neo4JLoader(Neo4JConnection neo4jConnection, IMap<String, String> wordsMap, IMap<String, String> graphMap) {
        this.neo4jConnection = neo4jConnection;
        this.wordsMap = wordsMap;
        this.graphMap = graphMap;
        this.objectMapper = new ObjectMapper();
    }

    public void processGraphMap() {
        System.out.println("Processing graph_map to create relationships in Neo4J...");

        Set<String> keys = graphMap.keySet(); // Obtener todas las claves del mapa

        try (Session session = neo4jConnection.getSession()) {
            for (String key : keys) {
                try (Transaction tx = session.beginTransaction()) {
                    // Asegurarse de que el nodo principal existe
                    ensureNodeExists(tx, key);

                    // Obtener las palabras conectadas desde el JSON en graph_map
                    String connectedWordsJson = graphMap.get(key);
                    List<String> connectedWords = objectMapper.readValue(connectedWordsJson, new TypeReference<>() {});

                    for (String relatedWord : connectedWords) {
                        ensureNodeExists(tx, relatedWord);

                        // Calcular el peso entre las palabras
                        double weight = calculateFrequencyWeight(key, relatedWord);

                        // Crear la relación entre las palabras
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
        String query = "MERGE (w:Word {word: $word})";
        tx.run(query, org.neo4j.driver.Values.parameters("word", word));
    }

    private void createRelationship(Transaction tx, String source, String target, double weight) {
        // Asegurar que la relación sea no direccional (ordenar alfabéticamente)
        String sourceWord = source.compareTo(target) <= 0 ? source : target;
        String targetWord = source.compareTo(target) > 0 ? source : target;

        String query = """
                MATCH (source:Word {word: $source})
                MATCH (target:Word {word: $target})
                MERGE (source)-[r:RELATED_TO]->(target)
                ON CREATE SET r.weight = $weight
                """;
        tx.run(query, org.neo4j.driver.Values.parameters("source", sourceWord, "target", targetWord, "weight", weight));
    }

    private double calculateFrequencyWeight(String word1, String word2) {
        double freq1 = getWordFrequency(word1);
        double freq2 = getWordFrequency(word2);
        return (freq1 + freq2) / 2.0; // Calcular promedio
    }

    private double getWordFrequency(String word) {
        try {
            String wordDataJson = wordsMap.get(word);
            if (wordDataJson != null) {
                // Deserializar JSON a una lista de mapas
                List<Map<String, Object>> usages = objectMapper.readValue(wordDataJson, new TypeReference<>() {});
                return usages.stream()
                        .mapToDouble(usage -> (int) usage.getOrDefault("frequency", 0))
                        .sum();
            }
        } catch (Exception e) {
            System.err.println("Error retrieving frequency for word '" + word + "': " + e.getMessage());
        }
        return 0.0; // Retornar 0 si no hay datos
    }
}
