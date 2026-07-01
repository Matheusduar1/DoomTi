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
                // A sua chave de Autenticação Segura (AQ.) - Autenticando com sucesso!
                String apiKey = "sua chave aqui"; 
                
                // O SEGREDO ESTAVA AQUI: A Google aposentou a geração 1.5!
                // Atualizado para os modelos modernos de 2026.
                String[] modelosParaTestar = {
                    "gemini-3.5-flash",
                    "gemini-3.1-flash-lite",
                    "gemini-2.5-flash",
                    "gemini-2.0-flash"
                };
                
                String systemPrompt = "Você é um professor carismático de um curso técnico de TI. "
                        + "O nome do seu aluno é " + playerName + ". "
                        + "Você ama responder dúvidas sobre desenvolvimento de jogos, programação e hardware. "
                        + "Sempre responda de forma curta (máximo 2 frases pequenas) para caber na caixa de diálogo do jogo.";
                
                String safeMessage = message.replace("\"", "'").replace("\n", " ").replace("\\", "/");
                String promptFinal = systemPrompt + " Aluno diz: " + safeMessage;
                
                String jsonBody = "{\n" +
                        "  \"contents\": [{\n" +
                        "    \"parts\":[{\"text\": \"" + promptFinal + "\"}]\n" +
                        "  }]\n" +
                        "}";

                HttpClient client = HttpClient.newHttpClient();
                boolean sucesso = false;

                System.out.println("--- PROCURANDO UM CÉREBRO ATIVO NOS SERVIDORES DA GOOGLE ---");

                for (String modelo : modelosParaTestar) {
                    // Sem o ?key= na URL, a autenticação segue pelo Header
                    String url = "https://generativelanguage.googleapis.com/v1beta/models/" + modelo + ":generateContent";
                    
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Content-Type", "application/json")
                            .header("x-goog-api-key", apiKey) 
                            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                            .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        String resBody = response.body();
                        int textIndex = resBody.indexOf("\"text\":");
                        if (textIndex != -1) {
                            int start = resBody.indexOf("\"", textIndex + 7) + 1;
                            int end = resBody.indexOf("\"", start);
                            
                            while(resBody.charAt(end - 1) == '\\') {
                                end = resBody.indexOf("\"", end + 1);
                            }
                            
                            String answer = resBody.substring(start, end);
                            answer = answer.replace("\\n", " ").replace("\\*", "");
                            callback.onResponse(answer);
                            
                            sucesso = true;
                            System.out.println("[SUCESSO TOTAL] O Google aceitou o modelo moderno: " + modelo);
                            break; 
                        }
                    } else {
                        System.out.println("[MODELO MORTO] " + modelo + " falhou (Erro " + response.statusCode() + "). Pulando para o próximo...");
                    }
                }

                if (!sucesso) {
                    callback.onError("Os modelos modernos também falharam. Olhe o Console para debugar.");
                }

            } catch (Exception e) {
                e.printStackTrace(); 
                callback.onError("Erro fatal no Java: " + e.getMessage());
            }
        }).start();
    }
}