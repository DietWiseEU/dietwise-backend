package eu.dietwise.v1.model;

import static eu.dietwise.v1.types.BiologicalGender.FEMALE;
import static eu.dietwise.v1.types.Country.GREECE;
import static org.assertj.core.api.Assertions.assertThat;

import eu.dietwise.v1.json.ObjectMapperModelUtils;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

public class PersonalInfoTest {
	@Test
	void testDeserialization() throws Exception {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(JsonMapper.builder()).build();
		var json = """
				{
					"country": "GR",
					"gender": "FEMALE",
					"yearOfBirth": 1979
				}
				""";
		var result = om.readValue(json, PersonalInfo.class);
		assertThat(result.getCountry()).isEqualTo(GREECE);
		assertThat(result.getGender()).isEqualTo(FEMALE);
		assertThat(result.getYearOfBirth()).isEqualTo(1979);
	}
}
