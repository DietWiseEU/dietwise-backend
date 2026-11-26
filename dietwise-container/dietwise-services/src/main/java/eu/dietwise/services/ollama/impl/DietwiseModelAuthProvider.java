package eu.dietwise.services.ollama.impl;

import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.services.ollama.OllamaConfig;
import io.quarkiverse.langchain4j.auth.ModelAuthProvider;

@ApplicationScoped
public class DietwiseModelAuthProvider implements ModelAuthProvider {
	private final OllamaConfig config;

	public DietwiseModelAuthProvider(OllamaConfig config) {
		this.config = config;
	}

	@Override
	public String getAuthorization(Input input) {
		if ("ollama.com".equals(input.uri().getHost())) {
			return config.getApiKey();
		}
		return null;
	}
}
