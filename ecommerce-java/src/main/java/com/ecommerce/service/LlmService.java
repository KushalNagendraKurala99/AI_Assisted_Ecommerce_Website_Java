package com.ecommerce.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Replaces llm.py
 *
 * Python original:
 *   def ask_llm(prompt):
 *       res = requests.post("http://localhost:11434/api/generate", json={...})
 *       return res.json()["response"]
 *
 * Java equivalent uses Spring WebClient (non-blocking HTTP client).
 */
@Service
public class LlmService {

    private final WebClient webClient;
    private final String model;

    public LlmService(
            @Value("${ollama.base-url}") String baseUrl,
            @Value("${ollama.model}") String model) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

    /**
     * Send a prompt to Ollama and return the text response.
     * Equivalent to ask_llm(prompt) in llm.py
     */
    @SuppressWarnings("unchecked")
    public String askLlm(String prompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false
        );
        Map<String, Object> response = webClient.post()
                .uri("/api/generate")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (response == null) return "";
        Object resp = response.get("response");
        return resp != null ? resp.toString() : "";
    }
}
