package eu.dietwise.sys;

import jakarta.inject.Singleton;

import eu.dietwise.v1.json.ObjectMapperModelUtils;
import io.quarkus.jackson.JsonMapperBuilderCustomizer;
import tools.jackson.databind.json.JsonMapper;

@Singleton
public class DietwiseObjectMapperCustomizer implements JsonMapperBuilderCustomizer {
	@Override
	public void customize(JsonMapper.Builder builder) {
		ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(builder);
	}
}
