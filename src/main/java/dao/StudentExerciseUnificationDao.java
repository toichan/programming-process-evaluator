package dao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

import com.google.gson.Gson;

import entity.ExerciseConflictException;
import entity.ExerciseUnificationResult;
import entity.ExerciseUnification;
import entity.StudentExerciseEntry;
import entity.StudentExerciseEntry.Status;
import entity.StudentExerciseEntry.Type;
import entity.StudentExerciseInput;
import entity.StudentExercisePage.Scope;

public final class StudentExerciseUnificationDao {
	private final StudentExerciseDao dao = new StudentExerciseDao();

	private record PlannedEntry(StudentExerciseEntry entry, String name, String path) {}
	private record Plan(ExerciseUnification preview, List<Scope> scopes, List<PlannedEntry> entries) {}

	public ExerciseUnification preview(Connection connection, long userId) throws SQLException {
		return plan(connection, userId).preview();
	}

	public ExerciseUnificationResult unify(Connection connection, long userId, String token) throws SQLException {
		if (token == null || !token.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("統合内容を確認してから実行してください。");
		}
		Plan plan = plan(connection, userId);
		var preview = plan.preview();
		if (!preview.required() || !token.equals(preview.token())) {
			throw new ExerciseConflictException("統合対象が更新されています。統合内容をもう一度確認してください。");
		}
		long target = preview.targetExerciseId();
		// Release old namespaces inside this transaction before applying validated final paths.
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE student_exercise_entries SET trash_root_entry_id = exercise_entry_id
				WHERE student_exercise_id = ? AND entry_status = 'active' AND trash_root_entry_id IS NULL
				""")) {
			for (var scope : plan.scopes()) {
				statement.setLong(1, scope.exerciseId());
				statement.executeUpdate();
			}
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE student_exercise_entries SET source_exercise_id = COALESCE(source_exercise_id, student_exercise_id),
				  student_exercise_id = ?, name = ?, path = ?, trash_root_entry_id = ?
				WHERE exercise_entry_id = ? AND student_exercise_id = ?
				""")) {
			for (var planned : plan.entries()) {
				var entry = planned.entry();
				statement.setLong(1, target);
				statement.setString(2, planned.name());
				statement.setString(3, planned.path());
				statement.setObject(4, entry.trashRootEntryId());
				statement.setLong(5, entry.entryId());
				statement.setLong(6, entry.exerciseId());
				if (statement.executeUpdate() != 1) throw new SQLException("Unification entry update affected an unexpected row count.");
			}
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE student_exercises SET merged_into_exercise_id = ?, version = version + 1,
				  updated_at = CURRENT_TIMESTAMP
				WHERE student_exercise_id = ? AND student_user_id = ? AND version = ?
				""")) {
			for (var scope : plan.scopes()) {
				statement.setObject(1, scope.exerciseId() == target ? null : target);
				statement.setLong(2, scope.exerciseId());
				statement.setLong(3, userId);
				statement.setLong(4, scope.version());
				if (statement.executeUpdate() != 1) throw new SQLException("Unification scope update affected an unexpected row count.");
			}
		}
		return new ExerciseUnificationResult(target, preview.expectedVersion() + 1);
	}

	private Plan plan(Connection connection, long userId) throws SQLException {
		var scopes = dao.findScopes(connection, userId, true);
		if (scopes.size() < 2) {
			return new Plan(new ExerciseUnification(false, scopes.isEmpty() ? null : scopes.get(0).exerciseId(),
					scopes.isEmpty() ? 0 : scopes.get(0).version(), null, 0, List.of()), scopes, List.of());
		}
		Scope target = scopes.stream().filter(s -> "student_created".equals(s.origin())).findFirst().orElse(scopes.get(0));
		var ordered = new ArrayList<Scope>();
		ordered.add(target);
		scopes.stream().filter(s -> s.exerciseId() != target.exerciseId()).forEach(ordered::add);
		var reservedNames = new HashSet<String>();
		var planned = new ArrayList<PlannedEntry>();
		var items = new ArrayList<ExerciseUnification.Item>();
		for (var original : ordered) {
			var scope = dao.requireScope(connection, userId, original.exerciseId(), true);
			if (!scope.state().isEditable()) {
				throw new IllegalArgumentException("「" + scope.name()
						+ "」は閲覧専用のため統合できません。元のデータは残しています。教師・管理者に状態を確認してください。");
			}
			var entries = dao.findEntries(connection, scope.exerciseId());
			var index = new HashMap<Long, StudentExerciseEntry>();
			var children = new HashMap<Long, List<StudentExerciseEntry>>();
			for (var entry : entries) {
				index.put(entry.entryId(), entry);
				if (entry.parentEntryId() != null) {
					children.computeIfAbsent(entry.parentEntryId(), ignored -> new ArrayList<>()).add(entry);
				}
			}
			for (var entry : entries) {
				if (entry.parentEntryId() != null) {
					var parent = index.get(entry.parentEntryId());
					if (parent == null || parent.type() != Type.FOLDER) {
						throw new IllegalArgumentException("「" + scope.name()
								+ "」に元の親フォルダが見つからない項目があります。ごみ箱から復元先を指定して復元するか、教師・管理者に確認してください。統合は行っていません。");
					}
				}
			}
			var paths = new LinkedHashMap<Long, String>();
			for (var root : entries) {
				if (root.parentEntryId() != null) continue;
				String name = root.name();
				if (root.status() == Status.ACTIVE && root.trashRootEntryId() == null) {
					int number = 2;
					while (reservedNames.contains(collationKey(connection, name))) {
						name = alternativeName(root, number++);
					}
					reservedNames.add(collationKey(connection, name));
				}
				paths.put(root.entryId(), StudentExerciseDao.checkedPath(null, name));
				var pending = new ArrayDeque<StudentExerciseEntry>();
				pending.add(root);
				while (!pending.isEmpty()) {
					var entry = pending.removeFirst();
					String path = paths.get(entry.entryId());
					planned.add(new PlannedEntry(entry, entry.entryId() == root.entryId() ? name : entry.name(), path));
					for (var child : children.getOrDefault(entry.entryId(), List.of())) {
						if (paths.containsKey(child.entryId())) throw new SQLException("Cyclic exercise hierarchy.");
						paths.put(child.entryId(), StudentExerciseDao.checkedPath(path, child.name()));
						pending.add(child);
					}
				}
				items.add(new ExerciseUnification.Item(root.entryId(), scope.exerciseId(), scope.name(),
						root.type().getValue(), root.status().getValue(), root.path(), paths.get(root.entryId())));
			}
			if (paths.size() != entries.size()) throw new SQLException("Exercise hierarchy contains unreachable entries.");
		}
		var finalPaths = new HashSet<String>();
		for (var item : planned) {
			var entry = item.entry();
			if (entry.status() == Status.ACTIVE && entry.trashRootEntryId() == null
					&& !finalPaths.add(collationKey(connection, item.path()))) {
				throw new ExerciseConflictException("統合後に同名の項目が重なります。元のデータは残しています。名前を変更してから統合内容を確認してください。",
						ExerciseConflictException.Reason.NAME);
			}
		}
		String token = fingerprint(userId, ordered, planned);
		return new Plan(new ExerciseUnification(true, target.exerciseId(), target.version(), token,
				planned.size(), items), List.copyOf(ordered), List.copyOf(planned));
	}

	private static String alternativeName(StudentExerciseEntry entry, int number) {
		String name = entry.name();
		String suffix = entry.type() == Type.FILE && name.toLowerCase(java.util.Locale.ROOT).endsWith(".py")
				? name.substring(name.length() - 3) : "";
		String base = name.substring(0, name.length() - suffix.length());
		String candidate = base + " (" + number + ")" + suffix;
		try {
			return new StudentExerciseInput(entry.type(), candidate).name();
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("「" + entry.path()
					+ "」の別名候補を使用できません。元の項目の名前を短くするなど変更してから、統合内容を確認してください。元のデータは変更していません。", e);
		}
	}

	private static String collationKey(Connection connection, String text) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT HEX(WEIGHT_STRING(CONVERT(? USING utf8mb4) COLLATE utf8mb4_0900_ai_ci))
				""")) {
			statement.setString(1, text);
			try (var rows = statement.executeQuery()) {
				if (!rows.next()) throw new SQLException("Database name comparison returned no value.");
				return rows.getString(1);
			}
		}
	}

	private static String fingerprint(long userId, List<Scope> scopes, List<PlannedEntry> entries) {
		var snapshot = new ArrayList<Object>();
		snapshot.add(userId);
		scopes.forEach(s -> snapshot.add(List.of(s.exerciseId(), s.version(), s.name(),
				s.origin(), s.state().getValue(), s.saved())));
		// Gson's java.time reflection is deliberately avoided in the preview fingerprint.
		for (var item : entries) {
			var e = item.entry();
			snapshot.add(java.util.Arrays.asList(e.entryId(), e.exerciseId(), e.parentEntryId(), e.type().getValue(),
					e.name(), e.path(), e.status().getValue(), e.trashRootEntryId(), e.content(), e.description(),
					e.updatedAt() == null ? null : e.updatedAt().toString(),
					e.trashedAt() == null ? null : e.trashedAt().toString(), item.name(), item.path()));
		}
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(new Gson().toJson(snapshot).getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required for exercise unification.", e);
		}
	}
}
