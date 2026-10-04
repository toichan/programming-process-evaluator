package servlet.student;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import entity.ExerciseBatchInput;
import entity.ExerciseUploadResolution;

final class ExerciseBatchForm {
	private ExerciseBatchForm() {}

	static ExerciseBatchInput read(Map<String, String> form) {
		return read(form, false);
	}

	static ExerciseBatchInput read(Map<String, String> form, boolean restoring) {
		long version = StudentExerciseInputVersion.read(form.get("expectedVersion"));
		Long targetParentId = form.containsKey("parentEntryId") && !form.get("parentEntryId").isEmpty()
				? ExerciseForm.number(form.get("parentEntryId"), true) : null;
		var items = items(form.get("items"));
		var ids = form.containsKey("entryIds") ? ids(form.get("entryIds"))
				: items.isEmpty() && form.containsKey("entryId")
						? List.of(ExerciseForm.number(form.get("entryId"), true))
						: items.stream().map(ExerciseBatchInput.RestoreTarget::entryId).toList();
		var names = new LinkedHashMap<Long, String>(names(form.get("names")));
		if (form.containsKey("entryId") && form.containsKey("name")) {
			long id = ExerciseForm.number(form.get("entryId"), true);
			if (names.putIfAbsent(id, form.get("name")) != null) {
				throw new IllegalArgumentException("同じ項目の別名を複数指定しないでください。");
			}
		}
		var restorations = restorations(form.get("restorations"));
		if (!items.isEmpty()) {
			for (var item : items) {
				if (item.name() != null && names.putIfAbsent(item.entryId(), item.name()) != null) {
					throw new IllegalArgumentException("同じ項目の別名を複数指定しないでください。");
				}
			}
			if (restoring && restorations.isEmpty()) restorations = items;
		}
		return new ExerciseBatchInput(ids, version, targetParentId, names, restorations);
	}

	static List<Long> ids(String json) {
		if (json == null) throw new IllegalArgumentException("操作対象を指定してください。");
		try {
			var parsed = JsonParser.parseString(json);
			if (!parsed.isJsonArray() || parsed.getAsJsonArray().isEmpty()) {
				throw new IllegalArgumentException("操作対象を選択してください。");
			}
			var ids = new ArrayList<Long>();
			for (var item : parsed.getAsJsonArray()) ids.add(positiveInteger(item));
			return List.copyOf(ids);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("操作対象の形式が正しくありません。", e);
		}
	}

	static List<Long> csvIds(String value) {
		if (value == null || value.isEmpty()) return List.of();
		var values = new ArrayList<Long>();
		var unique = new java.util.HashSet<Long>();
		for (String token : value.split(",", -1)) {
			long id = ExerciseForm.number(token, true);
			if (!unique.add(id)) throw new IllegalArgumentException("操作対象を重複して指定しないでください。");
			values.add(id);
		}
		return List.copyOf(values);
	}

	static Map<Long, String> names(String json) {
		if (json == null || json.isEmpty()) return Map.of();
		try {
			var parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) {
				throw new IllegalArgumentException("別名の指定が正しくありません。");
			}
			var names = new LinkedHashMap<Long, String>();
			for (var item : parsed.getAsJsonObject().entrySet()) {
				long id = ExerciseForm.number(item.getKey(), true);
				if (!item.getValue().isJsonPrimitive() || !item.getValue().getAsJsonPrimitive().isString()) {
					throw new IllegalArgumentException("別名の指定が正しくありません。");
				}
				names.put(id, item.getValue().getAsString());
			}
			return Map.copyOf(names);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("別名の形式が正しくありません。", e);
		}
	}

	static List<ExerciseBatchInput.RestoreTarget> restorations(String json) {
		if (json == null || json.isEmpty()) return List.of();
		try {
			var parsed = JsonParser.parseString(json);
			if (!parsed.isJsonArray()) {
				throw new IllegalArgumentException("復元先の指定が正しくありません。");
			}
			var targets = new ArrayList<ExerciseBatchInput.RestoreTarget>();
			for (var item : parsed.getAsJsonArray()) {
				if (!item.isJsonObject()) throw new IllegalArgumentException("復元先の指定が正しくありません。");
				var object = item.getAsJsonObject();
				long id = positiveInteger(object.get("entryId"));
				boolean specified = object.has("destinationSpecified")
						&& object.get("destinationSpecified").isJsonPrimitive()
						&& object.get("destinationSpecified").getAsJsonPrimitive().isBoolean()
						&& object.get("destinationSpecified").getAsBoolean();
				Long parentId = null;
				if (object.has("parentEntryId")) {
					JsonElement value = object.get("parentEntryId");
					specified = true;
					if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
						if (!value.getAsString().isEmpty()) parentId = ExerciseForm.number(value.getAsString(), true);
					} else {
						parentId = positiveInteger(value);
					}
				}
				String name = optionalString(object, "name");
				targets.add(new ExerciseBatchInput.RestoreTarget(id, specified, parentId, name));
			}
			return List.copyOf(targets);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("復元先の形式が正しくありません。", e);
		}
	}

	static List<ExerciseBatchInput.RestoreTarget> items(String json) {
		if (json == null || json.isEmpty()) return List.of();
		try {
			var parsed = JsonParser.parseString(json);
			if (!parsed.isJsonArray()) throw new IllegalArgumentException("項目ごとの指定が正しくありません。");
			var targets = new ArrayList<ExerciseBatchInput.RestoreTarget>();
			for (var item : parsed.getAsJsonArray()) {
				if (!item.isJsonObject()) throw new IllegalArgumentException("項目ごとの指定が正しくありません。");
				var object = item.getAsJsonObject();
				long id = positiveInteger(object.get("entryId"));
				boolean destinationSpecified = object.has("parentEntryId");
				Long parentId = null;
				if (destinationSpecified) {
					JsonElement parent = object.get("parentEntryId");
					if (parent.isJsonPrimitive() && parent.getAsJsonPrimitive().isString()) {
						if (!parent.getAsString().isEmpty()) parentId = ExerciseForm.number(parent.getAsString(), true);
					} else {
						parentId = positiveInteger(parent);
					}
				}
				targets.add(new ExerciseBatchInput.RestoreTarget(id, destinationSpecified,
						parentId, optionalString(object, "name")));
			}
			return List.copyOf(targets);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("項目ごとの指定の形式が正しくありません。", e);
		}
	}

	static List<ExerciseUploadResolution> uploadResolutions(String json) {
		if (json == null || json.isEmpty()) return List.of();
		try {
			var parsed = JsonParser.parseString(json);
			if (!parsed.isJsonArray()) {
				throw new IllegalArgumentException("アップロードの同名解決が正しくありません。");
			}
			var resolutions = new ArrayList<ExerciseUploadResolution>();
			for (var item : parsed.getAsJsonArray()) {
				if (!item.isJsonObject()) throw new IllegalArgumentException("アップロードの同名解決が正しくありません。");
				var object = item.getAsJsonObject();
				String path = requiredString(object, "path");
				String action = requiredString(object, "action").toUpperCase(java.util.Locale.ROOT);
				ExerciseUploadResolution.Action parsedAction = switch (action) {
					case "MERGE" -> ExerciseUploadResolution.Action.MERGE;
					case "RENAME" -> ExerciseUploadResolution.Action.RENAME;
					case "SKIP" -> ExerciseUploadResolution.Action.SKIP;
					default -> throw new IllegalArgumentException("アップロードの同名解決が正しくありません。");
				};
				resolutions.add(new ExerciseUploadResolution(path, parsedAction, optionalString(object, "name")));
			}
			return List.copyOf(resolutions);
		} catch (JsonParseException | IllegalArgumentException e) {
			if (e instanceof IllegalArgumentException invalid) throw invalid;
			throw new IllegalArgumentException("アップロードの同名解決の形式が正しくありません。", e);
		}
	}

	private static long positiveInteger(JsonElement element) {
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()
				|| !element.getAsString().matches("[1-9][0-9]*")) {
			throw new IllegalArgumentException("対象IDを正しく指定してください。");
		}
		return ExerciseForm.number(element.getAsString(), true);
	}

	private static String requiredString(com.google.gson.JsonObject object, String key) {
		if (!object.has(key) || !object.get(key).isJsonPrimitive()
				|| !object.get(key).getAsJsonPrimitive().isString()) {
			throw new IllegalArgumentException("アップロードの同名解決が正しくありません。");
		}
		return object.get(key).getAsString();
	}

	private static String optionalString(com.google.gson.JsonObject object, String key) {
		return object.has(key) ? requiredString(object, key) : null;
	}

	private static final class StudentExerciseInputVersion {
		private StudentExerciseInputVersion() {}
		static long read(String value) {
			return entity.StudentExerciseInput.requireVersion(ExerciseForm.number(value, false));
		}
	}
}
