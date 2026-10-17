package control.dev;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lib.mysql.Client;

/**
 * Private, local-only configuration preservation/rehearsal, not a production deploy seed.
 */
public final class InitialConfigurationSeed {
	private static final Gson GSON = new GsonBuilder().serializeNulls().create();
	private static final String FORMAT = "ppe-private-initial-configuration-v1";
	static final List<String> TABLES = List.of(
			"users", "schools", "classrooms", "student_profiles", "student_class_memberships",
			"teacher_school_permissions", "teacher_feature_permissions", "student_teacher_credentials",
			"student_login_sequence", "rubrics", "rubric_dimensions", "rubric_criteria", "criterion_levels",
			"tasks", "task_features", "task_test_cases", "task_hints", "task_class_assignments",
			"school_common_prompt_versions", "prompt_versions", "prompt_fluctuation_items",
			"evaluation_examples", "surveys", "survey_questions", "survey_question_options");
	private static final Map<String, List<String>> DEFERRED = Map.of(
			"users", List.of("created_by_user_id", "updated_by_user_id"),
			"tasks", List.of("active_prompt_version_id", "supersedes_task_id"));

	private InitialConfigurationSeed() {}

	public static void main(String[] args) throws Exception {
		if (args.length != 2 || !List.of("export", "apply", "verify").contains(args[0])) {
			throw new IllegalArgumentException("Usage: export|apply|verify <private-file>");
		}
		String database = System.getenv("DB_NAME");
		if (!"simulation".equals(System.getenv("PPE_ENV"))
				|| database == null
				|| !(database.equals("programming_process_evaluator") || database.matches("ppe_initial_seed_[a-z0-9_]+"))) {
			throw new IllegalStateException("Configuration rehearsal requires an explicitly selected local/simulation DB.");
		}
		if ("apply".equals(args[0]) && !database.matches("ppe_initial_seed_[a-z0-9_]+")) {
			throw new IllegalStateException("Apply is restricted to an isolated ppe_initial_seed_ database.");
		}
		Path file = Path.of(args[1]).toAbsolutePath().normalize();
		try (Connection connection = Client.createConnection()) {
			if (!database.equals(connection.getCatalog())) throw new IllegalStateException("Unexpected database.");
			switch (args[0]) {
				case "export" -> writePrivate(file, capture(connection));
				case "apply" -> apply(connection, readPrivate(file));
				case "verify" -> requireEqual(readPrivate(file), capture(connection));
				default -> throw new IllegalArgumentException("Unsupported command.");
			}
			System.out.println("Initial configuration " + args[0] + ": PASS (25 allowlisted tables; no values logged).");
		} finally {
			Client.closeDataSource();
		}
	}

	static JsonObject capture(Connection c) throws Exception {
		if (!c.getAutoCommit()) throw new IllegalStateException("Capture requires an idle connection.");
		c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
		c.setReadOnly(true);
		c.setAutoCommit(false);
		try {
			JsonObject result = captureInTransaction(c);
			c.commit();
			return result;
		} catch (Exception failure) {
			c.rollback();
			throw failure;
		} finally {
			c.setAutoCommit(true);
			c.setReadOnly(false);
		}
	}

	private static JsonObject captureInTransaction(Connection c) throws Exception {
		JsonObject data = new JsonObject();
		for (String table : TABLES) {
			List<String> columns = columns(c, table);
			JsonObject definition = new JsonObject();
			JsonArray names = new JsonArray();
			columns.forEach(names::add);
			definition.add("columns", names);
			JsonArray rows = new JsonArray();
			try (var statement = c.createStatement();
					var result = statement.executeQuery("SELECT " + identifiers(columns) + " FROM `" + table
							+ "` ORDER BY " + identifiers(columns))) {
				while (result.next()) {
					JsonArray row = new JsonArray();
					for (int i = 1; i <= columns.size(); i++) row.add(result.getString(i));
					rows.add(row);
				}
			}
			definition.add("rows", rows);
			data.add(table, definition);
		}
		JsonObject envelope = new JsonObject();
		envelope.addProperty("format", FORMAT);
		envelope.addProperty("datetimePolicy", "verbatim-stored-values-no-timezone-assumption");
		envelope.add("data", data);
		envelope.addProperty("sha256", hash(data));
		return envelope;
	}

	static boolean apply(Connection c, JsonObject expected) throws Exception {
		validate(expected);
		if (!c.getCatalog().matches("ppe_initial_seed_[a-z0-9_]+")) {
			throw new IllegalStateException("Apply requires an isolated configuration rehearsal database.");
		}
		if (!c.getAutoCommit()) throw new IllegalStateException("Apply requires an idle connection.");
		String lock = "initial-seed:" + c.getCatalog();
		try (var statement = c.prepareStatement("SELECT GET_LOCK(?, 0)")) {
			statement.setString(1, lock);
			try (var r = statement.executeQuery()) {
				if (!r.next() || r.getInt(1) != 1) throw new IllegalStateException("Another seed owns the database lock.");
			}
		}
		c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
		c.setAutoCommit(false);
		try {
			requireNoHistory(c);
			for (String table : TABLES) {
				if (!columns(c, table).equals(strings(expected.getAsJsonObject("data").getAsJsonObject(table)
						.getAsJsonArray("columns")))) {
					throw new IllegalStateException("Configuration schema mismatch in " + table + ".");
				}
			}
			if (count(c, "users") != 0 || count(c, "schools") != 0 || count(c, "rubrics") != 0) {
				requireEqual(expected, captureInTransaction(c));
				c.commit();
				return false;
			}
			for (String table : TABLES) {
				if (!"student_login_sequence".equals(table) && count(c, table) != 0) {
					throw new IllegalStateException("Nonempty target configuration: " + table + ".");
				}
			}
			for (String table : TABLES) {
				JsonObject definition = expected.getAsJsonObject("data").getAsJsonObject(table);
				List<String> names = strings(definition.getAsJsonArray("columns"));
				if ("student_login_sequence".equals(table)) {
					if (count(c, table) != 1) throw new IllegalStateException("Unexpected initial login sequence.");
					JsonArray row = definition.getAsJsonArray("rows").get(0).getAsJsonArray();
					try (var statement = c.prepareStatement(
							"UPDATE student_login_sequence SET next_number=? WHERE singleton_id=1")) {
						statement.setString(1, row.get(names.indexOf("next_number")).getAsString());
						if (statement.executeUpdate() != 1) throw new SQLException("Login sequence was not initialized.");
					}
					continue;
				}
				String sql = "INSERT INTO `" + table + "` (" + identifiers(names) + ") VALUES ("
						+ String.join(",", java.util.Collections.nCopies(names.size(), "?")) + ")";
				try (var statement = c.prepareStatement(sql)) {
					for (JsonElement element : definition.getAsJsonArray("rows")) {
						JsonArray row = element.getAsJsonArray();
						for (int i = 0; i < names.size(); i++) {
							JsonElement value = row.get(i);
							if (DEFERRED.getOrDefault(table, List.of()).contains(names.get(i)) || value.isJsonNull()) {
								statement.setNull(i + 1, Types.VARCHAR);
							} else statement.setString(i + 1, value.getAsString());
						}
						statement.executeUpdate();
					}
				}
			}
			// The profile INSERT trigger initializes first-login flags; restore the preserved flags explicitly.
			restore(c, expected, "student_profiles", List.of("first_login_status", "must_change_password"));
			for (var entry : DEFERRED.entrySet()) restore(c, expected, entry.getKey(), entry.getValue());
			requireEqual(expected, captureInTransaction(c));
			c.commit();
			return true;
		} catch (Exception failure) {
			c.rollback();
			throw failure;
		} finally {
			c.setAutoCommit(true);
			try (var statement = c.prepareStatement("SELECT RELEASE_LOCK(?)")) {
				statement.setString(1, lock);
				statement.executeQuery().close();
			}
		}
	}

	private static void restore(Connection c, JsonObject expected, String table, List<String> restored) throws Exception {
		JsonObject definition = expected.getAsJsonObject("data").getAsJsonObject(table);
		List<String> names = strings(definition.getAsJsonArray("columns"));
		String key = names.get(0);
		try (var statement = c.prepareStatement("UPDATE `" + table + "` SET "
				+ String.join(",", restored.stream().map(name -> "`" + name + "`=?").toList())
				+ " WHERE `" + key + "`=?")) {
			for (JsonElement element : definition.getAsJsonArray("rows")) {
				JsonArray row = element.getAsJsonArray();
				int parameter = 1;
				for (String name : restored) {
					JsonElement value = row.get(names.indexOf(name));
					if (value.isJsonNull()) statement.setNull(parameter++, Types.VARCHAR);
					else statement.setString(parameter++, value.getAsString());
				}
				statement.setString(parameter, row.get(0).getAsString());
				statement.executeUpdate();
			}
		}
	}

	private static void requireNoHistory(Connection c) throws Exception {
		try (var query = c.prepareStatement("SELECT table_name FROM information_schema.tables "
				+ "WHERE table_schema=? AND table_type='BASE TABLE'")) {
			query.setString(1, c.getCatalog());
			try (var rows = query.executeQuery()) {
				while (rows.next()) {
					String table = rows.getString(1);
					if (!TABLES.contains(table) && !List.of("flyway_schema_history", "consent_document_versions").contains(table)
							&& count(c, table) != 0) {
						throw new IllegalStateException("Target contains excluded history: " + table + ".");
					}
				}
			}
		}
	}

	private static List<String> columns(Connection c, String table) throws Exception {
		List<String> names = new ArrayList<>();
		try (var statement = c.prepareStatement("SELECT column_name FROM information_schema.columns "
				+ "WHERE table_schema=? AND table_name=? AND extra NOT LIKE '%GENERATED%' ORDER BY ordinal_position")) {
			statement.setString(1, c.getCatalog());
			statement.setString(2, table);
			try (var rows = statement.executeQuery()) {
				while (rows.next()) names.add(rows.getString(1));
			}
		}
		if (names.isEmpty()) throw new IllegalStateException("Required configuration table is unavailable: " + table + ".");
		return names;
	}

	private static String identifiers(List<String> names) {
		if (names.isEmpty() || names.stream().anyMatch(name -> !name.matches("[a-z][a-z0-9_]*"))) {
			throw new IllegalArgumentException("Invalid schema identifier.");
		}
		return String.join(",", names.stream().map(name -> "`" + name + "`").toList());
	}

	private static long count(Connection c, String table) throws Exception {
		try (var statement = c.createStatement();
				var rows = statement.executeQuery("SELECT COUNT(*) FROM " + identifiers(List.of(table)))) {
			rows.next();
			return rows.getLong(1);
		}
	}

	static void requireEqual(JsonObject expected, JsonObject actual) {
		validate(expected);
		validate(actual);
		if (!expected.getAsJsonObject("data").equals(actual.getAsJsonObject("data"))) {
			throw new IllegalStateException("Configuration content/reference mismatch; refusing to overwrite.");
		}
	}

	static void validate(JsonObject envelope) {
		if (!FORMAT.equals(envelope.get("format").getAsString())
				|| !"verbatim-stored-values-no-timezone-assumption".equals(envelope.get("datetimePolicy").getAsString())) {
			throw new IllegalArgumentException("Unsupported configuration format or datetime policy.");
		}
		JsonObject data = envelope.getAsJsonObject("data");
		if (!new ArrayList<>(data.keySet()).equals(TABLES) || !hash(data).equals(envelope.get("sha256").getAsString())) {
			throw new IllegalArgumentException("Invalid configuration table list or checksum.");
		}
		for (String table : TABLES) {
			JsonObject definition = data.getAsJsonObject(table);
			List<String> names = strings(definition.getAsJsonArray("columns"));
			identifiers(names);
			if (names.size() != names.stream().distinct().count()) throw new IllegalArgumentException("Duplicate columns.");
			for (JsonElement element : definition.getAsJsonArray("rows")) {
				JsonArray row = element.getAsJsonArray();
				if (row.size() != names.size()) throw new IllegalArgumentException("Invalid configuration row shape.");
				for (JsonElement value : row) {
					if (!value.isJsonNull() && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())) {
						throw new IllegalArgumentException("Configuration values must be strings or null.");
					}
				}
			}
		}
	}

	private static List<String> strings(JsonArray values) {
		List<String> result = new ArrayList<>();
		for (JsonElement value : values) result.add(value.getAsString());
		return result;
	}

	private static String hash(JsonObject data) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(GSON.toJson(data).getBytes(StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException failure) {
			throw new IllegalStateException("SHA-256 is unavailable.", failure);
		}
	}

	static void writePrivate(Path file, JsonObject snapshot) throws Exception {
		validate(snapshot);
		Path parent = file.toAbsolutePath().getParent().toRealPath();
		Path worktree = Path.of("").toAbsolutePath().normalize();
		if (parent.startsWith(worktree)) {
			throw new IllegalArgumentException("Private configuration must be stored outside the working directory.");
		}
		try (var writer = Files.newBufferedWriter(
				Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))),
				StandardCharsets.UTF_8, StandardOpenOption.WRITE)) {
			GSON.toJson(snapshot, writer);
		}
	}

	static JsonObject readPrivate(Path file) throws Exception {
		if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
				|| !Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS)
						.equals(PosixFilePermissions.fromString("rw-------"))) {
			throw new IllegalArgumentException("Configuration file must be a private 0600 regular file.");
		}
		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject snapshot = JsonParser.parseReader(reader).getAsJsonObject();
			validate(snapshot);
			return snapshot;
		}
	}
}
