package eu.dietwise.services.ollama.impl;

import java.util.Optional;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import eu.dietwise.services.ollama.OllamaConfig;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class OllamaConfigImpl implements OllamaConfig {
	public static final String OLLAMA_API_KEY_KEY = "ollama.api-key";

	private final String apiKey;

	@Inject
	public OllamaConfigImpl(
			@ConfigProperty(name = OLLAMA_API_KEY_KEY, defaultValue = "") Optional<String> ollamaApiKey
	) {
		apiKey = ollamaApiKey.orElse(null);
	}

	@Override
	public String getApiKey() {
		return apiKey;
	}
}
