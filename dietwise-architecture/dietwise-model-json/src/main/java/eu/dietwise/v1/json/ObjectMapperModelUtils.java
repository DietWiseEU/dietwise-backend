package eu.dietwise.v1.json;

import tools.jackson.databind.json.JsonMapper;

public interface ObjectMapperModelUtils {
	static JsonMapper.Builder applyDefaultObjectMapperConfiguration(JsonMapper.Builder builder) {
		return builder.addModule(new DietwiseJacksonModule());
	}
}
