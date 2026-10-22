package eu.dietwise.v1.types;

/**
 * The language of a client's user interface, in which the client is sent the texts it shows, e.g. an Intervention.
 */
public enum UiLanguage {
	EN,
	NL,
	EL,
	LT;

	public static UiLanguage fromCode(String code) {
		return switch (code.trim().toLowerCase()) {
			case "en" -> EN;
			case "nl" -> NL;
			case "el" -> EL;
			case "lt" -> LT;
			default -> throw new IllegalArgumentException("Unsupported language code: " + code);
		};
	}
}
