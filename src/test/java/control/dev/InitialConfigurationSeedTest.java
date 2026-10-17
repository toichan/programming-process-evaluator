package control.dev;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InitialConfigurationSeedTest {
	@TempDir Path directory;

	@Test
	void writesPrivateFileAndRefusesOverwriteOrPublicPermissions() throws Exception {
		JsonObject snapshot = emptySnapshot();
		Path file = directory.resolve("settings.json");
		InitialConfigurationSeed.writePrivate(file, snapshot);
		assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file));
		assertEquals(snapshot, InitialConfigurationSeed.readPrivate(file));
		assertThrows(java.nio.file.FileAlreadyExistsException.class,
				() -> InitialConfigurationSeed.writePrivate(file, snapshot));
		Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));
		assertThrows(IllegalArgumentException.class, () -> InitialConfigurationSeed.readPrivate(file));
	}

	@Test
	void refusesTamperingAndUnexpectedTablesEvenWithUpdatedChecksum() throws Exception {
		JsonObject snapshot = emptySnapshot();
		snapshot.getAsJsonObject("data").getAsJsonObject("schools").getAsJsonArray("rows").add(new JsonArray());
		assertThrows(IllegalArgumentException.class, () -> InitialConfigurationSeed.validate(snapshot));
		JsonObject extra = emptySnapshot();
		extra.getAsJsonObject("data").add("consent_records", new JsonObject());
		rechecksum(extra);
		assertThrows(IllegalArgumentException.class, () -> InitialConfigurationSeed.validate(extra));
	}

	@Test
	void refusesSchemaShapedInjectionAndWrongRowWidth() throws Exception {
		JsonObject snapshot = emptySnapshot();
		JsonArray names = new JsonArray();
		names.add("user_id`); DELETE FROM users; --");
		snapshot.getAsJsonObject("data").getAsJsonObject("users").add("columns", names);
		rechecksum(snapshot);
		assertThrows(IllegalArgumentException.class, () -> InitialConfigurationSeed.validate(snapshot));
		JsonObject wrong = emptySnapshot();
		wrong.getAsJsonObject("data").getAsJsonObject("users").getAsJsonArray("rows").add(new JsonArray());
		rechecksum(wrong);
		assertThrows(IllegalArgumentException.class, () -> InitialConfigurationSeed.validate(wrong));
	}

	@Test
	void refusesSymlinks() throws Exception {
		Path file = directory.resolve("settings.json");
		InitialConfigurationSeed.writePrivate(file, emptySnapshot());
		Path link = directory.resolve("link.json");
		Files.createSymbolicLink(link, file);
		assertThrows(IllegalArgumentException.class, () -> InitialConfigurationSeed.readPrivate(link));
	}

	private static JsonObject emptySnapshot() throws Exception {
		JsonObject snapshot = new JsonObject();
		snapshot.addProperty("format", "ppe-private-initial-configuration-v1");
		snapshot.addProperty("datetimePolicy", "verbatim-stored-values-no-timezone-assumption");
		JsonObject data = new JsonObject();
		for (String table : InitialConfigurationSeed.TABLES) {
			JsonObject definition = new JsonObject();
			JsonArray names = new JsonArray();
			names.add("synthetic_id");
			definition.add("columns", names);
			definition.add("rows", new JsonArray());
			data.add(table, definition);
		}
		snapshot.add("data", data);
		rechecksum(snapshot);
		return snapshot;
	}

	private static void rechecksum(JsonObject snapshot) throws Exception {
		snapshot.addProperty("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(new com.google.gson.GsonBuilder().serializeNulls().create()
						.toJson(snapshot.get("data")).getBytes(StandardCharsets.UTF_8))));
	}
}
