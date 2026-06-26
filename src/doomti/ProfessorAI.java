package doomti;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ProfessorAI {

    public interface AICallback {
        void onResponse(String response);
        void onError(String error);
    }

    public static void ask(String playerName, String message, AICallback callback) {
        new Thread(() -> {
            try {
                // COLE A NOVA CHAVE AQUI (Deve começar com AIza)
                String apiKey = "sua chave"; 
                
                String model = "gemini-1.5-flash";
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
                
                String jsonBody = "{\"contents\":[{\"parts\":[{\"text\":\"Você é um professor de TI. Aluno: " + playerName + ". Dúvida: " + message + "\"}]}]}";

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    // Extração básica da resposta
                    String res = response.body();
                    callback.onResponse(res.split("\"text\": \"")[1].split("\"")[0]);
                } else {
                    callback.onError("Erro " + response.statusCode() + ": Verifique a nova chave AIza.");
                }

            } catch (Exception e) {
                callback.onError("Erro: " + e.getMessage());
            }
        }).start();
    }
}