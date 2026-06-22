package ci.proxyma.passwordcli;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Client responsable de l'interopérabilité Java <-> Docker.
 *
 * Communication choisie : appel HTTP REST vers le conteneur (plutôt qu'un
 * "docker exec" via ProcessBuilder). Ce choix illustre une architecture
 * orientée micro-service : le programme Java reste totalement indépendant
 * de la technologie utilisée à l'intérieur du conteneur (Python/Flask/zxcvbn
 * ici), tant que le contrat HTTP/JSON est respecté. On pourrait remplacer
 * zxcvbn par CrackLib ou un modèle Ollama sans changer une ligne de ce client.
 *
 * Utilise {@link HttpClient} natif de Java 11+ (donc disponible nativement
 * en Java 21) plutôt qu'une dépendance externe comme OkHttp, pour limiter
 * le nombre de bibliothèques tierces du projet.
 */
public class AuditClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public AuditClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Vérifie que le conteneur est démarré et répond, avant de lancer
     * l'audit réel. Évite d'envoyer N requêtes d'audit (mode rafale) pour
     * échouer N fois si le conteneur n'est simplement pas lancé.
     */
    public boolean isServiceAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    /**
     * Envoie un mot de passe au conteneur d'audit et retourne le résultat structuré.
     *
     * @throws AuditException si le conteneur est inaccessible ou renvoie une erreur,
     *         afin que l'appelant (Main) puisse afficher un message clair plutôt
     *         que de laisser fuiter une exception technique brute.
     */
    public AuditResult audit(String password) throws AuditException {
        try {
            String corpsRequete = objectMapper.writeValueAsString(Map.of("password", password));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/audit"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(corpsRequete))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new AuditException(
                        "Le conteneur d'audit a renvoyé une erreur (HTTP " + response.statusCode() + ") : "
                                + response.body());
            }

            return objectMapper.readValue(response.body(), AuditResult.class);

        } catch (IOException e) {
            throw new AuditException(
                    "Impossible de contacter le conteneur d'audit à l'adresse " + baseUrl
                            + ". Vérifiez qu'il est démarré (docker compose up -d).", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuditException("La requête d'audit a été interrompue.", e);
        }
    }

    /**
     * Exception dédiée pour distinguer une erreur d'interopérabilité Docker
     * d'une erreur de logique métier classique — utile pour le diagnostic.
     */
    public static class AuditException extends Exception {
        public AuditException(String message) {
            super(message);
        }

        public AuditException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
