package lib.web;

public final class CsvCells {
	private CsvCells() {}
	public static String encode(String value) {
		value = value == null ? "" : value;
		if (!value.isEmpty() && "=+@-\t\r\n".indexOf(value.charAt(0)) >= 0) value = "'" + value;
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}
}
