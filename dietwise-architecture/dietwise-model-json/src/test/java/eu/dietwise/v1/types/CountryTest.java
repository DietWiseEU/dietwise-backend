package eu.dietwise.v1.types;

import static eu.dietwise.v1.types.Country.GREECE;
import static org.assertj.core.api.Assertions.assertThat;

import eu.dietwise.v1.json.ObjectMapperModelUtils;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class CountryTest {
	@Test
	void testSerialization() throws Exception {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(JsonMapper.builder()).build();
		var result = om.writeValueAsString(GREECE);
		assertThat(result).isEqualTo("\"GR\"");
	}

	@Test
	void testDeserialization() throws Exception {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(JsonMapper.builder()).build();
		var result = om.readValue("\"GR\"", Country.class);
		assertThat(result).isEqualTo(GREECE);
	}
}
