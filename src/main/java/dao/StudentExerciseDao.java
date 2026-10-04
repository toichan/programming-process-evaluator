package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.UUID;

import entity.ExerciseConflictException;
import entity.ExerciseNotFoundException;
import entity.ExerciseBatchInput;
import entity.ExerciseBatchResult;
import entity.ExerciseDownload;
import entity.ExerciseDuplicatePreview;
import entity.ExerciseSaveResult;
import entity.ExerciseExecutionResult;
import entity.PythonExecutionResult;
import entity.ExerciseUploadPreview;
import entity.ExerciseUploadResolution;
import entity.StudentExerciseEntry;
import entity.StudentExerciseEntry.Status;
import entity.StudentExerciseEntry.Type;
import entity.StudentExerciseInput;
import entity.StudentExercisePage.Scope;
import entity.StudentExercisePage.State;

public final class StudentExerciseDao {
	public ExerciseExecutionResult findLatestExecution(Connection connection, long userId, long exerciseId,
			long entryId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT c.* FROM code_executions c
				JOIN student_exercise_entries e ON e.exercise_entry_id = c.exercise_entry_id
				JOIN student_exercises s ON s.student_exercise_id = e.student_exercise_id
				WHERE c.execution_context = 'student_exercise' AND c.actor_user_id = ?
				  AND s.student_user_id = ? AND s.student_exercise_id = ? AND e.exercise_entry_id = ?
				  AND s.deleted_at IS NULL AND e.entry_status = 'active'
				ORDER BY c.execution_id DESC LIMIT 1
				""")) {
			statement.setLong(1, userId);
			statement.setLong(2, userId);
			statement.setLong(3, exerciseId);
			statement.setLong(4, entryId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) return null;
				int exit = rows.getInt("exit_code");
				Integer exitCode = rows.wasNull() ? null : exit;
				return new ExerciseExecutionResult(rows.getLong("execution_id"),
						new PythonExecutionResult(rows.getString("execution_status"), exitCode,
								rows.getString("standard_output"), rows.getString("standard_error"),
								rows.getBoolean("standard_output_truncated"), rows.getBoolean("standard_error_truncated"),
								rows.getString("error_code")), rows.getString("standard_input"),
						rows.getTimestamp("executed_at").toLocalDateTime());
			}
		}
	}

	public void requireExecutableFile(Connection connection, long userId, long exerciseId, long entryId)
			throws SQLException {
		exerciseId = resolveScope(connection, userId, exerciseId);
		Scope scope = requireScope(connection, userId, exerciseId, true);
		if (!scope.state().isEditable()) {
			throw new IllegalArgumentException("この状態の演習は実行できません。");
		}
		StudentExerciseEntry entry = requireActiveEntry(findEntries(connection, exerciseId), entryId);
		if (entry.type() != Type.FILE) {
			throw new IllegalArgumentException("実行するファイルを指定してください。");
		}
	}

	public long recordExecution(Connection connection, long userId, long exerciseId, long entryId,
			String source, String input, PythonExecutionResult result, int duration) throws SQLException {
		requireExecutableFile(connection, userId, exerciseId, entryId);
		if (!List.of("succeeded", "failed", "timed_out").contains(result.getStatus())) {
			throw new SQLException("The isolated runner returned an unsupported terminal status.");
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO code_executions (actor_user_id, exercise_entry_id, execution_context,
				  source_code, standard_input, execution_status, exit_code, standard_output,
				  standard_output_truncated, standard_error, standard_error_truncated, error_code,
				  duration_milliseconds, executed_at)
				VALUES (?, ?, 'student_exercise', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, userId);
			statement.setLong(2, entryId);
			statement.setString(3, source);
			statement.setString(4, input);
			statement.setString(5, result.getStatus());
			statement.setObject(6, result.getExitCode());
			statement.setString(7, result.getStandardOutput());
			statement.setBoolean(8, result.isStandardOutputTruncated());
			statement.setString(9, result.getStandardError());
			statement.setBoolean(10, result.isStandardErrorTruncated());
			statement.setString(11, result.getErrorCode());
			statement.setInt(12, Math.max(0, duration));
			statement.executeUpdate();
			return generatedId(statement);
		}
	}

	public void requireActiveStudent(Connection connection, long userId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT u.user_id FROM users u JOIN student_profiles p ON p.user_id = u.user_id
				WHERE u.user_id = ? AND u.user_type = 'student'
				  AND u.account_status = 'active' AND u.deleted_at IS NULL
				  AND p.must_change_password = FALSE FOR UPDATE
				""")) {
			statement.setLong(1, userId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) throw new SecurityException("Active student authentication is required.");
			}
		}
	}

	public List<Scope> findScopes(Connection connection, long userId) throws SQLException {
		return findScopes(connection, userId, false);
	}

	public List<Scope> findScopes(Connection connection, long userId, boolean forUpdate) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT student_exercise_id, scope_name, exercise_origin, exercise_status, save_status, version
				FROM student_exercises WHERE student_user_id = ?
				  AND deleted_at IS NULL AND exercise_status <> 'archived' AND merged_into_exercise_id IS NULL
				ORDER BY student_exercise_id
				""" + (forUpdate ? " FOR UPDATE" : ""))) {
			statement.setLong(1, userId);
			try (ResultSet rows = statement.executeQuery()) {
				List<Scope> scopes = new ArrayList<>();
				while (rows.next()) scopes.add(mapScope(rows));
				return List.copyOf(scopes);
			}
		}
	}

	public Scope requireScope(Connection connection, long userId, long exerciseId, boolean forUpdate)
			throws SQLException {
		String sql = """
				SELECT student_exercise_id, scope_name, exercise_origin, exercise_status, save_status, version
				FROM student_exercises WHERE student_user_id = ? AND student_exercise_id = ?
				  AND deleted_at IS NULL AND exercise_status <> 'archived' AND merged_into_exercise_id IS NULL
				""" + (forUpdate ? " FOR UPDATE" : "");
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, userId);
			statement.setLong(2, exerciseId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) throw new ExerciseNotFoundException();
				return mapScope(rows);
			}
		}
	}

	public long resolveScope(Connection connection, long userId, long exerciseId) throws SQLException {
		var visited = new HashSet<Long>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT COALESCE(merged_into_exercise_id, student_exercise_id) FROM student_exercises
				WHERE student_exercise_id = ? AND student_user_id = ?
				  AND deleted_at IS NULL AND exercise_status <> 'archived'
				""")) {
			while (visited.add(exerciseId)) {
				statement.setLong(1, exerciseId);
				statement.setLong(2, userId);
				try (var rows = statement.executeQuery()) {
					if (!rows.next()) throw new ExerciseNotFoundException();
					long resolved = rows.getLong(1);
					if (resolved == exerciseId) return exerciseId;
					exerciseId = resolved;
				}
			}
			throw new SQLException("Cyclic exercise unification references.");
		}
	}

	public List<StudentExerciseEntry> findEntries(Connection connection, long exerciseId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT exercise_entry_id, student_exercise_id, parent_entry_id, entry_type, name,
				  path, description, current_content, entry_status, updated_at, trash_root_entry_id, trashed_at
				FROM student_exercise_entries WHERE student_exercise_id = ? ORDER BY path, exercise_entry_id
				""")) {
			statement.setLong(1, exerciseId);
			try (ResultSet rows = statement.executeQuery()) {
				List<StudentExerciseEntry> entries = new ArrayList<>();
				while (rows.next()) {
					long parent = rows.getLong("parent_entry_id");
					Long nullableParent = rows.wasNull() ? null : parent;
					long trashRoot = rows.getLong("trash_root_entry_id");
					Long nullableTrashRoot = rows.wasNull() ? null : trashRoot;
					Timestamp updated = rows.getTimestamp("updated_at");
					Timestamp trashed = rows.getTimestamp("trashed_at");
					entries.add(new StudentExerciseEntry(rows.getLong("exercise_entry_id"),
							rows.getLong("student_exercise_id"), nullableParent,
							Type.fromValue(rows.getString("entry_type")), rows.getString("name"),
							rows.getString("path"), rows.getString("description"),
							rows.getString("current_content"), Status.fromValue(rows.getString("entry_status")),
							updated == null ? null : updated.toLocalDateTime(), nullableTrashRoot,
							trashed == null ? null : trashed.toLocalDateTime()));
				}
				return List.copyOf(entries);
			}
		}
	}

	public List<StudentExerciseEntry> visibleEntries(List<StudentExerciseEntry> entries) throws SQLException {
		Map<Long, StudentExerciseEntry> index = index(entries);
		List<StudentExerciseEntry> visible = new ArrayList<>();
		for (StudentExerciseEntry entry : entries) {
			var trashRoot = index.get(entry.trashRootEntryId());
			boolean recoverableTrash = entry.status() == Status.ACTIVE && trashRoot != null
					&& trashRoot.status() == Status.TRASHED;
			if (entry.status() == Status.TRASHED
					|| recoverableTrash
					|| (entry.status() != Status.DELETED && !hasAncestorStatus(entry, index, Status.DELETED))) {
				visible.add(entry);
			}
		}
		return List.copyOf(visible);
	}

	public List<StudentExerciseEntry> downloadableEntries(List<StudentExerciseEntry> entries) throws SQLException {
		Map<Long, StudentExerciseEntry> index = index(entries);
		List<StudentExerciseEntry> active = new ArrayList<>();
		for (StudentExerciseEntry entry : entries) {
			if (entry.status() == Status.ACTIVE && entry.trashRootEntryId() == null
					&& !hasAncestorStatus(entry, index, Status.TRASHED)
					&& !hasAncestorStatus(entry, index, Status.DELETED)) active.add(entry);
		}
		return List.copyOf(active);
	}

	public ExerciseDownload selectedDownload(Connection connection, long userId, long exerciseId,
			String rootName, List<Long> selectedIds) throws SQLException {
		requireScope(connection, userId, exerciseId, false);
		var entries = downloadableEntries(findEntries(connection, exerciseId));
		var entryIndex = index(entries);
		List<StudentExerciseEntry> roots = List.of();
		List<StudentExerciseEntry> chosen;
		if (selectedIds == null) {
			chosen = entries;
			if (chosen.isEmpty()) {
				throw new IllegalArgumentException("ダウンロード対象のフォルダ・ファイルがありません。");
			}
			return new ExerciseDownload(chosen, ExerciseDownload.archiveName(rootName), "application/zip", true);
		} else {
			roots = normalizeDownloadRoots(entries, selectedIds);
			if (roots.isEmpty()) throw new IllegalArgumentException("ダウンロード対象を指定してください。");
			var selected = new ArrayList<StudentExerciseEntry>();
			for (var root : roots) {
				for (var entry : entries) {
					if (isWithin(entry, root.entryId(), entryIndex)) selected.add(entry);
				}
			}
			if (roots.size() == 1 && roots.get(0).type() == Type.FOLDER) {
				String prefix = roots.get(0).path();
				String folderName = roots.get(0).name();
				chosen = selected.stream().map(entry -> withPath(entry,
						entry.path().equals(prefix) ? folderName
								: folderName + entry.path().substring(prefix.length())))
						.toList();
			} else {
				chosen = List.copyOf(selected);
			}
		}
		return ExerciseDownload.selected(rootName, roots, chosen);
	}

	private List<StudentExerciseEntry> normalizeDownloadRoots(List<StudentExerciseEntry> entries,
			List<Long> selectedIds) throws SQLException {
		var selected = normalizeRoots(entries, selectedIds, Status.ACTIVE);
		var roots = new ArrayList<StudentExerciseEntry>();
		for (var candidate : selected) {
			boolean coveredBySelectedFolder = selected.stream().anyMatch(ancestor ->
					ancestor.type() == Type.FOLDER
							&& ancestor.entryId() != candidate.entryId()
							&& candidate.path().startsWith(ancestor.path() + "/"));
			if (!coveredBySelectedFolder) roots.add(candidate);
		}
		return List.copyOf(roots);
	}

	public ExerciseBatchResult batchMove(Connection connection, long userId, long exerciseId,
			ExerciseBatchInput input) throws SQLException {
		Scope scope = requireMutableScope(connection, userId, exerciseId, input.expectedVersion());
		var entries = findEntries(connection, exerciseId);
		var roots = normalizeRoots(entries, input.entryIds(), Status.ACTIVE);
		var index = index(entries);
		requireNamesForRoots(input.names(), roots);
		StudentExerciseEntry targetParent = input.targetParentId() == null ? null
				: requireActiveEntry(entries, input.targetParentId());
		if (targetParent != null && targetParent.type() != Type.FOLDER) {
			throw new IllegalArgumentException("移動先はフォルダを指定してください。");
		}
		Map<Long, String> plannedPaths = new LinkedHashMap<>();
		Map<Long, Long> rootParents = new HashMap<>();
		Map<Long, String> rootNames = new HashMap<>();
		var moving = new HashSet<Long>();
		boolean changed = false;
		for (var root : roots) {
			for (var item : entries) {
				if (isWithin(item, root.entryId(), index) && item.status() == Status.ACTIVE
						&& item.trashRootEntryId() == null) moving.add(item.entryId());
			}
			if (targetParent != null && isWithin(targetParent, root.entryId(), index)) {
				throw new IllegalArgumentException("フォルダ自身や、その中のフォルダには移動できません。");
			}
			String requestedName = input.names().get(root.entryId());
			String name = requestedName == null || requestedName.equals(root.name()) ? root.name()
					: new StudentExerciseInput(root.type(), requestedName).name();
			Long parentId = input.targetParentId();
			String parentPath = targetParent == null ? null : targetParent.path();
			rootParents.put(root.entryId(), parentId);
			rootNames.put(root.entryId(), name);
			changed |= !java.util.Objects.equals(root.parentEntryId(), parentId) || !root.name().equals(name);
			String newRootPath = checkedPath(parentPath, name);
			for (var item : entries) {
				if (!isWithin(item, root.entryId(), index)) continue;
				String suffix = item.entryId() == root.entryId() ? "" : item.path().substring(root.path().length());
				plannedPaths.put(item.entryId(), newRootPath + suffix);
			}
		}
		if (!changed) return new ExerciseBatchResult("unchanged", exerciseId, scope.version(), 0);
		var plannedActivePaths = plannedPaths.entrySet().stream()
				.filter(path -> moving.contains(path.getKey())).map(Map.Entry::getValue).toList();
		if (hasDuplicateDatabasePaths(connection, plannedActivePaths)) {
			throw new ExerciseConflictException("選択した項目同士が同じ名前になります。別名を指定してください。",
					ExerciseConflictException.Reason.NAME);
		}
		for (var path : plannedPaths.entrySet()) {
			if (!moving.contains(path.getKey())) continue;
			Long existing = entryAtPath(connection, exerciseId, path.getValue());
			if (existing != null && !moving.contains(existing)) {
				throw new ExerciseConflictException("移動先に同名の項目があります。別名を指定してください。",
						ExerciseConflictException.Reason.NAME);
			}
		}
		stagePaths(connection, exerciseId, moving);
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE student_exercise_entries SET parent_entry_id = ?, name = ?, path = ?,
				  updated_at = CURRENT_TIMESTAMP WHERE student_exercise_id = ? AND exercise_entry_id = ?
				""")) {
			for (var root : roots) {
				statement.setObject(1, rootParents.get(root.entryId()));
				statement.setString(2, rootNames.get(root.entryId()));
				statement.setString(3, plannedPaths.get(root.entryId()));
				statement.setLong(4, exerciseId);
				statement.setLong(5, root.entryId());
				requireOne(statement.executeUpdate());
			}
			for (var item : entries) {
				if (!plannedPaths.containsKey(item.entryId()) || roots.stream()
						.anyMatch(root -> root.entryId() == item.entryId())) continue;
				statement.setObject(1, item.parentEntryId());
				statement.setString(2, item.name());
				statement.setString(3, plannedPaths.get(item.entryId()));
				statement.setLong(4, exerciseId);
				statement.setLong(5, item.entryId());
				requireOne(statement.executeUpdate());
			}
		}
		updateScope(connection, exerciseId, scope.version(), scope.state().getValue(), scope.saved() ? "saved" : "unsaved");
		return new ExerciseBatchResult("moved", exerciseId, scope.version() + 1, roots.size());
	}

	public ExerciseBatchResult batchTrash(Connection connection, long userId, long exerciseId,
			ExerciseBatchInput input) throws SQLException {
		Scope scope = requireMutableScope(connection, userId, exerciseId, input.expectedVersion());
		var entries = findEntries(connection, exerciseId);
		var roots = normalizeRoots(entries, input.entryIds(), Status.ACTIVE);
		var entryIndex = index(entries);
		Timestamp now = Timestamp.from(java.time.Instant.now());
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE student_exercise_entries SET entry_status = ?, trash_root_entry_id = ?, trashed_at = ?,
				  updated_at = CURRENT_TIMESTAMP WHERE student_exercise_id = ? AND exercise_entry_id = ?
				""")) {
			for (var root : roots) {
				for (var item : entries) {
					if (!isWithin(item, root.entryId(), entryIndex)) continue;
					if (item.entryId() != root.entryId()
							&& (item.status() != Status.ACTIVE || item.trashRootEntryId() != null)) continue;
					statement.setString(1, item.entryId() == root.entryId() ? "trashed" : "active");
					statement.setLong(2, root.entryId());
					statement.setTimestamp(3, now);
					statement.setLong(4, exerciseId);
					statement.setLong(5, item.entryId());
					requireOne(statement.executeUpdate());
				}
			}
		}
		updateScope(connection, exerciseId, scope.version(), scope.state().getValue(), scope.saved() ? "saved" : "unsaved");
		return new ExerciseBatchResult("trashed", exerciseId, scope.version() + 1, roots.size());
	}

	public ExerciseBatchResult batchRestore(Connection connection, long userId, long exerciseId,
			ExerciseBatchInput input) throws SQLException {
		Scope scope = requireMutableScope(connection, userId, exerciseId, input.expectedVersion());
		var initial = findEntries(connection, exerciseId);
		var restoreIds = input.restorations().isEmpty()
				? input.entryIds().stream().map(id -> new ExerciseBatchInput.RestoreTarget(id, false, null, null)).toList()
				: input.restorations();
		if (!restoreIds.stream().map(ExerciseBatchInput.RestoreTarget::entryId).collect(java.util.stream.Collectors.toSet())
				.equals(new HashSet<>(input.entryIds()))) {
			throw new IllegalArgumentException("すべての復元対象の場所を指定してください。");
		}
		var initialIndex = index(initial);
		var depths = new HashMap<Long, Integer>();
		for (var target : restoreIds) {
			var entry = initialIndex.get(target.entryId());
			if (entry == null || entry.status() != Status.TRASHED) throw new ExerciseNotFoundException();
			depths.put(target.entryId(), ancestorDepth(entry, initialIndex));
		}
		var sorted = restoreIds.stream().sorted(Comparator.comparingInt(target -> depths.get(target.entryId()))).toList();
		for (var target : sorted) {
			restoreEntry(connection, userId, exerciseId, target.entryId(), scope.version(),
					target.destinationSpecified(), target.parentEntryId(), target.name(), false);
		}
		updateScope(connection, exerciseId, scope.version(), scope.state().getValue(), scope.saved() ? "saved" : "unsaved");
		return new ExerciseBatchResult("restored", exerciseId, scope.version() + 1, sorted.size());
	}

	public ExerciseDuplicatePreview previewDuplicate(Connection connection, long userId, long exerciseId,
			List<Long> selectedIds) throws SQLException {
		return previewDuplicate(connection, userId, exerciseId, selectedIds, null);
	}

	public ExerciseDuplicatePreview previewDuplicate(Connection connection, long userId, long exerciseId,
			List<Long> selectedIds, Long expectedVersion) throws SQLException {
		if (expectedVersion != null) requireMutableScope(connection, userId, exerciseId, expectedVersion);
		else requireScope(connection, userId, exerciseId, false);
		var entries = downloadableEntries(findEntries(connection, exerciseId));
		var roots = normalizeRoots(entries, selectedIds, Status.ACTIVE);
		var suggestions = new ArrayList<ExerciseDuplicatePreview.Item>();
		var reserved = new ArrayList<String>();
		for (var root : roots) {
			var parent = root.parentEntryId() == null ? null : requireActiveEntry(entries, root.parentEntryId());
			String candidate = suggestName(connection, exerciseId, parent == null ? null : parent.path(),
					root.name(), root.type(), reserved);
			reserved.add(checkedPath(parent == null ? null : parent.path(), candidate));
			suggestions.add(new ExerciseDuplicatePreview.Item(root.entryId(), root.path(), root.type(), candidate));
		}
		return new ExerciseDuplicatePreview(suggestions);
	}

	public ExerciseBatchResult duplicate(Connection connection, long userId, long exerciseId,
			ExerciseBatchInput input) throws SQLException {
		Scope scope = requireMutableScope(connection, userId, exerciseId, input.expectedVersion());
		var entries = downloadableEntries(findEntries(connection, exerciseId));
		var roots = normalizeRoots(entries, input.entryIds(), Status.ACTIVE);
		var entryIndex = index(entries);
		requireNamesForRoots(input.names(), roots);
		var reserved = new ArrayList<String>();
		var planned = new ArrayList<ClonePlan>();
		for (var root : roots) {
			String name = input.names().getOrDefault(root.entryId(), null);
			if (name == null) name = suggestName(connection, exerciseId,
					parentPath(entries, root.parentEntryId()), root.name(), root.type(), reserved);
			name = cloneName(root.type(), name);
			String newRootPath = checkedPath(parentPath(entries, root.parentEntryId()), name);
			for (var item : entries) {
				if (!isWithin(item, root.entryId(), entryIndex)) continue;
				String suffix = item.entryId() == root.entryId() ? "" : item.path().substring(root.path().length());
				String targetPath = newRootPath + suffix;
				if (targetPath.codePointCount(0, targetPath.length()) > StudentExerciseInput.MAX_PATH_CHARACTERS) {
					throw new IllegalArgumentException("複製後のパス全体は1000文字以内にしてください。");
				}
				var destinationExists = entryAtPath(connection, exerciseId, targetPath);
				if (destinationExists != null) {
					throw new ExerciseConflictException("複製先に同名の項目があります。別名を指定してください。",
							ExerciseConflictException.Reason.NAME);
				}
				planned.add(new ClonePlan(root.entryId(), item, targetPath,
						item.entryId() == root.entryId() ? root.parentEntryId() : item.parentEntryId()));
				reserved.add(targetPath);
			}
		}
		if (hasDuplicateDatabasePaths(connection, planned.stream().map(ClonePlan::path).toList())) {
			throw new ExerciseConflictException("選択した複製同士が同じ名前になります。別名を指定してください。",
					ExerciseConflictException.Reason.NAME);
		}
		var clonedIds = new HashMap<Long, Long>();
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO student_exercise_entries (student_exercise_id, parent_entry_id, entry_type,
				  name, path, language, current_content, entry_status, created_at, source_exercise_id)
				VALUES (?, ?, ?, ?, ?, ?, ?, 'active', CURRENT_TIMESTAMP, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			for (var plan : planned) {
				Long parentId = plan.source().entryId() == plan.rootId()
						? plan.parentEntryId() : clonedIds.get(plan.parentEntryId());
				if (plan.source().entryId() != plan.rootId() && parentId == null) {
					throw new SQLException("The selected exercise hierarchy is inconsistent.");
				}
				String name = plan.path().substring(plan.path().lastIndexOf('/') + 1);
				statement.setLong(1, exerciseId);
				statement.setObject(2, parentId);
				statement.setString(3, plan.source().type().getValue());
				statement.setString(4, name);
				statement.setString(5, plan.path());
				statement.setString(6, plan.source().type() == Type.FILE ? "python" : null);
				statement.setString(7, plan.source().type() == Type.FILE ? plan.source().content() : null);
				statement.setLong(8, exerciseId);
				statement.executeUpdate();
				clonedIds.put(plan.source().entryId(), generatedId(statement));
			}
		}
		updateScope(connection, exerciseId, scope.version(), scope.state().getValue(), scope.saved() ? "saved" : "unsaved");
		return new ExerciseBatchResult("duplicated", exerciseId, scope.version() + 1, roots.size());
	}

	public StudentExerciseEntry requireActiveEntry(List<StudentExerciseEntry> entries, long entryId)
			throws SQLException {
		Map<Long, StudentExerciseEntry> index = index(entries);
		StudentExerciseEntry entry = index.get(entryId);
		if (entry == null || entry.status() != Status.ACTIVE || entry.trashRootEntryId() != null
				|| hasAncestorStatus(entry, index, Status.TRASHED)
				|| hasAncestorStatus(entry, index, Status.DELETED)) {
			throw new ExerciseNotFoundException();
		}
		return entry;
	}

	public ExerciseSaveResult create(Connection connection, long userId, Long exerciseId,
			Long parentId, long expectedVersion, StudentExerciseInput input) throws SQLException {
		long id = creationScope(connection, userId, exerciseId, parentId, expectedVersion);
		long entryId = insertEntry(connection, id, parentId, input, input.type() == Type.FILE ? "" : null);
		updateScope(connection, id, expectedVersion, "in_progress", "unsaved");
		return new ExerciseSaveResult(id, entryId, expectedVersion + 1);
	}

	private long creationScope(Connection connection, long userId, Long exerciseId,
			Long parentId, long expectedVersion) throws SQLException {
		long id;
		if (exerciseId == null) {
			if (parentId != null || expectedVersion != 0) {
				throw new IllegalArgumentException("初回作成情報が正しくありません。");
			}
			// The caller holds the student's user row lock before looking up the first scope.
			if (!findScopes(connection, userId).isEmpty()) {
				throw new ExerciseConflictException("演習領域が作成されています。画面を再読み込みしてください。");
			}
			id = insertScope(connection, userId);
		} else {
			id = exerciseId;
			requireMutableScope(connection, userId, id, expectedVersion);
		}
		return id;
	}

	private long insertEntry(Connection connection, long id, Long parentId,
			StudentExerciseInput input, String content) throws SQLException {
		String parentPath = null;
		if (parentId != null) {
			StudentExerciseEntry parent = requireActiveEntry(findEntries(connection, id), parentId);
			if (parent.type() != Type.FOLDER) {
				throw new IllegalArgumentException("作成先はフォルダを指定してください。");
			}
			parentPath = parent.path();
		}
		String path = input.pathUnder(parentPath);
		if (entryAtPath(connection, id, path) != null) {
			throw new ExerciseConflictException("同じ場所に同名の項目があります。別の名前を入力してください。",
					ExerciseConflictException.Reason.NAME);
		}
		long entryId;
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO student_exercise_entries (student_exercise_id, parent_entry_id,
				  entry_type, name, path, language, current_content, entry_status, created_at, source_exercise_id)
				VALUES (?, ?, ?, ?, ?, ?, ?, 'active', CURRENT_TIMESTAMP, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, id);
			statement.setObject(2, parentId);
			statement.setString(3, input.type().getValue());
			statement.setString(4, input.name());
			statement.setString(5, path);
			statement.setString(6, input.type() == Type.FILE ? "python" : null);
			statement.setString(7, content);
			statement.setLong(8, id);
			statement.executeUpdate();
			entryId = generatedId(statement);
		}
		return entryId;
	}

	public ExerciseUploadPreview previewUpload(Connection connection, long userId, Long exerciseId,
			Long parentId, long version, entity.ExerciseUpload upload) throws SQLException {
		if (upload == null) throw new IllegalArgumentException("アップロード内容を指定してください。");
		if (exerciseId == null) {
			if (parentId != null || version != 0 || !findScopes(connection, userId).isEmpty()) {
				throw new ExerciseConflictException("演習領域が更新されています。画面を再読み込みしてください。");
			}
			return new ExerciseUploadPreview(List.of());
		}
		requireMutableScope(connection, userId, exerciseId, version);
		var entries = findEntries(connection, exerciseId);
		String basePath = null;
		if (parentId != null) {
			var parent = requireActiveEntry(entries, parentId);
			if (parent.type() != Type.FOLDER) throw new IllegalArgumentException("アップロード先はフォルダを指定してください。");
			basePath = parent.path();
		}
		List<UploadNode> nodes = uploadNodes(connection, upload);
		var conflicts = new ArrayList<ExerciseUploadPreview.Conflict>();
		for (var node : nodes) {
			String targetPath = checkedUploadPath(basePath, node.path());
			Long existingId = entryAtPath(connection, exerciseId, targetPath);
			if (existingId == null) continue;
			var existing = entries.stream().filter(entry -> entry.entryId() == existingId).findFirst()
					.orElseThrow(() -> new SQLException("The upload conflict is not in the exercise scope."));
			List<String> actions = node.type() == Type.FOLDER && existing.type() == Type.FOLDER
					? List.of("merge", "rename", "skip") : List.of("rename", "skip");
			String targetParent = targetPath.contains("/")
					? targetPath.substring(0, targetPath.lastIndexOf('/')) : null;
			String suggestedName = suggestName(connection, exerciseId, targetParent,
					node.path().substring(node.path().lastIndexOf('/') + 1), node.type(), List.of());
			conflicts.add(new ExerciseUploadPreview.Conflict(node.path(), node.type(), existing.type(),
					suggestedName, actions));
		}
		return new ExerciseUploadPreview(conflicts);
	}

	public entity.ExerciseUploadResult uploadResolved(Connection connection, long userId, Long exerciseId,
			Long parentId, long version, entity.ExerciseUpload upload,
			List<ExerciseUploadResolution> resolutions) throws SQLException {
		if (upload == null) throw new IllegalArgumentException("アップロード内容を指定してください。");
		if (resolutions == null) resolutions = List.of();
		var resolutionByPath = new HashMap<String, ExerciseUploadResolution>();
		for (var resolution : resolutions) {
			if (resolutionByPath.putIfAbsent(resolution.path(), resolution) != null) {
				throw new IllegalArgumentException("同じアップロード項目の解決を複数指定しないでください。");
			}
		}
		List<UploadNode> nodes = uploadNodes(connection, upload);
		long id = creationScope(connection, userId, exerciseId, parentId, version);
		String basePath = null;
		if (parentId != null) {
			var parent = requireActiveEntry(findEntries(connection, id), parentId);
			if (parent.type() != Type.FOLDER) throw new IllegalArgumentException("アップロード先はフォルダを指定してください。");
			basePath = parent.path();
		}
		var createdFolders = new ArrayList<Long>();
		var foldersBySourcePath = new HashMap<String, MappedUploadFolder>();
		var skippedFolders = new HashSet<String>();
		int addedFiles = 0;
		int skippedFiles = 0;
		for (var file : upload.files()) {
			Long currentParent = parentId;
			String currentTargetPath = basePath == null ? "" : basePath;
			String sourcePrefix = "";
			String[] parts = file.path().split("/");
			boolean skipped = false;
			for (int i = 0; i < parts.length - 1; i++) {
				sourcePrefix = sourcePrefix.isEmpty() ? parts[i] : sourcePrefix + "/" + parts[i];
				if (isSkipped(sourcePrefix, skippedFolders)) {
					skipped = true;
					break;
				}
				var mapped = foldersBySourcePath.get(sourcePrefix);
				if (mapped != null) {
					currentParent = mapped.entryId();
					currentTargetPath = mapped.path();
					continue;
				}
				String sourceName = parts[i];
				String targetPath = checkedPath(currentTargetPath, sourceName);
				Long existingId = entryAtPath(connection, id, targetPath);
				if (existingId != null) {
					var currentEntries = findEntries(connection, id);
					var existing = currentEntries.stream().filter(entry -> entry.entryId() == existingId)
							.findFirst().orElseThrow(() -> new SQLException("The upload conflict is not in the exercise scope."));
					var resolution = resolutionByPath.remove(sourcePrefix);
					if (resolution == null) throw uploadNameConflict("同名項目の解決を選択してください。");
					if (resolution.action() == ExerciseUploadResolution.Action.SKIP) {
						skippedFolders.add(sourcePrefix);
						skipped = true;
						break;
					}
					if (resolution.action() == ExerciseUploadResolution.Action.MERGE) {
						if (existing.type() != Type.FOLDER) {
							throw uploadNameConflict("ファイルとフォルダは結合できません。別名か対象外を選択してください。");
						}
						currentParent = existing.entryId();
						currentTargetPath = existing.path();
						foldersBySourcePath.put(sourcePrefix, new MappedUploadFolder(currentParent, currentTargetPath));
						continue;
					}
					if (resolution.action() != ExerciseUploadResolution.Action.RENAME) {
						throw new IllegalArgumentException("フォルダの同名解決が正しくありません。");
					}
					sourceName = new StudentExerciseInput(Type.FOLDER, resolution.name()).name();
					targetPath = checkedPath(currentTargetPath, sourceName);
					if (entryAtPath(connection, id, targetPath) != null) {
						throw uploadNameConflict("別名の項目も既に存在します。別の名前を指定してください。");
					}
				} else if (resolutionByPath.containsKey(sourcePrefix)) {
					throw uploadNameConflict("プレビュー後にアップロードの同名状態が変わりました。");
				}
				long newFolder = insertEntry(connection, id, currentParent,
						new StudentExerciseInput(Type.FOLDER, sourceName), null);
				createdFolders.add(newFolder);
				currentParent = newFolder;
				currentTargetPath = targetPath;
				foldersBySourcePath.put(sourcePrefix, new MappedUploadFolder(currentParent, currentTargetPath));
			}
			if (skipped) {
				skippedFiles++;
				continue;
			}
			String sourceFilePath = file.path();
			String targetPath = checkedPath(currentTargetPath, parts[parts.length - 1]);
			Long existingFileId = entryAtPath(connection, id, targetPath);
			var resolution = resolutionByPath.remove(sourceFilePath);
			if (existingFileId != null) {
				if (resolution == null) throw uploadNameConflict("同名ファイルの解決を選択してください。");
				if (resolution.action() == ExerciseUploadResolution.Action.SKIP) {
					skippedFiles++;
					continue;
				}
				if (resolution.action() != ExerciseUploadResolution.Action.RENAME) {
					throw uploadNameConflict("同名ファイルは結合できません。別名か対象外を選択してください。");
				}
				String renamed = new StudentExerciseInput(Type.FILE, resolution.name()).name();
				targetPath = checkedPath(currentTargetPath, renamed);
				if (entryAtPath(connection, id, targetPath) != null) {
					throw uploadNameConflict("別名のファイルも既に存在します。別の名前を指定してください。");
				}
			} else if (resolution != null) {
				throw uploadNameConflict("プレビュー後にアップロードの同名状態が変わりました。");
			}
			String targetName = targetPath.substring(targetPath.lastIndexOf('/') + 1);
			insertEntry(connection, id, currentParent, new StudentExerciseInput(Type.FILE, targetName), file.code());
			addedFiles++;
		}
		for (String skipped : skippedFolders) {
			String prefix = skipped + "/";
			resolutionByPath.keySet().removeIf(path -> path.startsWith(prefix));
		}
		if (!resolutionByPath.isEmpty()) {
			throw new IllegalArgumentException("現在の競合項目に適用できない解決指定があります。再度プレビューしてください。");
		}
		if (addedFiles == 0) {
			for (int i = createdFolders.size() - 1; i >= 0; i--) {
				try (PreparedStatement statement = connection.prepareStatement(
						"DELETE FROM student_exercise_entries WHERE student_exercise_id = ? AND exercise_entry_id = ?")) {
					statement.setLong(1, id);
					statement.setLong(2, createdFolders.get(i));
					requireOne(statement.executeUpdate());
				}
			}
			Scope current = requireScope(connection, userId, id, false);
			return new entity.ExerciseUploadResult("skipped", id, current.version(), 0, skippedFiles);
		}
		Scope scope = requireScope(connection, userId, id, true);
		if (scope.version() != version) {
			throw new ExerciseConflictException("演習が更新されています。画面を再読み込みしてから、アップロードをやり直してください。");
		}
		updateScope(connection, id, version, "temporarily_saved", "saved");
		return new entity.ExerciseUploadResult("uploaded", id, version + 1, addedFiles, skippedFiles);
	}

	public ExerciseSaveResult upload(Connection connection, long userId, Long exerciseId,
			Long parentId, long version, entity.ExerciseUpload upload) throws SQLException {
		long id = creationScope(connection, userId, exerciseId, parentId, version);
		String base = "";
		if (parentId != null) {
			var parent = requireActiveEntry(findEntries(connection, id), parentId);
			if (parent.type() != Type.FOLDER) throw new IllegalArgumentException("作成先はフォルダを指定してください。");
			base = parent.path();
		}
		long lastEntry = 0;
		for (var file : upload.files()) {
			Long currentParent = parentId;
			String path = base;
			String[] parts = file.path().split("/");
			for (int i = 0; i < parts.length - 1; i++) {
				var input = new StudentExerciseInput(Type.FOLDER, parts[i]);
				String nextPath = input.pathUnder(path);
				Long existing = entryAtPath(connection, id, nextPath);
				if (existing == null) {
					currentParent = insertEntry(connection, id, currentParent, input, null);
				} else {
					var entries = findEntries(connection, id);
					if (entries.stream().noneMatch(e -> e.entryId() == existing && e.status() == Status.ACTIVE)) {
						throw new ExerciseConflictException("アップロード先に同名の項目が存在します。ごみ箱も確認してください。");
					}
					var folder = requireActiveEntry(entries, existing);
					if (folder.type() != Type.FOLDER) {
						throw new ExerciseConflictException("アップロード先に同名のファイルが存在します。",
								ExerciseConflictException.Reason.NAME);
					}
					currentParent = existing;
					nextPath = folder.path();
				}
				path = nextPath;
			}
			lastEntry = insertEntry(connection, id, currentParent,
					new StudentExerciseInput(Type.FILE, parts[parts.length - 1]), file.code());
		}
		updateScope(connection, id, version, "temporarily_saved", "saved");
		return new ExerciseSaveResult(id, lastEntry, version + 1);
	}

	private Long entryAtPath(Connection connection, long id, String path) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"""
				SELECT exercise_entry_id FROM student_exercise_entries
				WHERE student_exercise_id = ? AND path = ? AND active_path_hash IS NOT NULL
				""")) {
			statement.setLong(1, id);
			statement.setString(2, path);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? rows.getLong(1) : null;
			}
		}
	}

	public ExerciseSaveResult save(Connection connection, long userId, long exerciseId,
			long entryId, long version, String code) throws SQLException {
		requireMutableScope(connection, userId, exerciseId, version);
		StudentExerciseEntry entry = requireActiveEntry(findEntries(connection, exerciseId), entryId);
		if (entry.type() != Type.FILE) {
			throw new IllegalArgumentException("保存するファイルを指定してください。");
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE student_exercise_entries SET current_content = ?, updated_at = CURRENT_TIMESTAMP
				WHERE student_exercise_id = ? AND exercise_entry_id = ?
				""")) {
			statement.setString(1, code);
			statement.setLong(2, exerciseId);
			statement.setLong(3, entryId);
			requireOne(statement.executeUpdate());
		}
		updateScope(connection, exerciseId, version, "temporarily_saved", "saved");
		return new ExerciseSaveResult(exerciseId, entryId, version + 1);
	}

	public ExerciseSaveResult relocate(Connection connection, long userId, long exerciseId,
			long entryId, long version, boolean moving, Long parentId, String name) throws SQLException {
		Scope scope = requireMutableScope(connection, userId, exerciseId, version);
		var entries = findEntries(connection, exerciseId);
		var entry = requireActiveEntry(entries, entryId);
		Long targetParent = moving ? parentId : entry.parentEntryId();
		String targetName = name == null || name.equals(entry.name()) ? entry.name()
				: new StudentExerciseInput(entry.type(), name).name();
		if (!moving && name == null) throw new IllegalArgumentException("新しい名前を入力してください。");
		String parentPath = null;
		if (targetParent != null) {
			var parent = requireActiveEntry(entries, targetParent);
			if (parent.type() != Type.FOLDER) throw new IllegalArgumentException("移動先はフォルダを指定してください。");
			var index = index(entries);
			var visited = new HashSet<Long>();
			for (var ancestor = parent; ancestor != null; ancestor = index.get(ancestor.parentEntryId())) {
				if (!visited.add(ancestor.entryId())) throw new SQLException("Cyclic exercise hierarchy.");
				if (ancestor.entryId() == entryId) {
					throw new IllegalArgumentException("フォルダ自身や、その中のフォルダには移動できません。");
				}
			}
			parentPath = parent.path();
		}
		if (java.util.Objects.equals(targetParent, entry.parentEntryId()) && targetName.equals(entry.name())) {
			return new ExerciseSaveResult(exerciseId, entryId, version);
		}
		updateHierarchy(connection, exerciseId, entries, entry, targetParent, targetName, parentPath, null);
		updateScope(connection, exerciseId, version, scope.state().getValue(), scope.saved() ? "saved" : "unsaved");
		return new ExerciseSaveResult(exerciseId, entryId, version + 1);
	}

	private void updateHierarchy(Connection connection, long exerciseId, List<StudentExerciseEntry> entries,
			StudentExerciseEntry entry, Long targetParent, String targetName, String parentPath, Long restoringRoot)
			throws SQLException {
		long entryId = entry.entryId();
		var paths = new java.util.LinkedHashMap<Long, String>();
		paths.put(entryId, checkedPath(parentPath, targetName));
		var pending = new java.util.ArrayDeque<Long>();
		pending.add(entryId);
		while (!pending.isEmpty()) {
			long parent = pending.removeFirst();
			for (var child : entries) {
				if (java.util.Objects.equals(child.parentEntryId(), parent)) {
					if (paths.containsKey(child.entryId())) throw new SQLException("Cyclic exercise hierarchy.");
					paths.put(child.entryId(), checkedPath(paths.get(parent), child.name()));
					pending.add(child.entryId());
				}
			}
		}
		for (var item : entries) {
			String path = paths.get(item.entryId());
			if (path == null || (item.entryId() != entryId && (item.status() != Status.ACTIVE
					|| !java.util.Objects.equals(item.trashRootEntryId(), restoringRoot)))) continue;
			Long existing = entryAtPath(connection, exerciseId, path);
			if (existing != null && !paths.containsKey(existing)) {
				throw new ExerciseConflictException("同じ場所に同名の項目があります。別の名前を入力してください。上書きはしません。",
						ExerciseConflictException.Reason.NAME);
			}
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE student_exercise_entries SET parent_entry_id = ?, name = ?, path = ?,
				  updated_at = CURRENT_TIMESTAMP WHERE student_exercise_id = ? AND exercise_entry_id = ?
				""")) {
			for (var item : entries) {
				String path = paths.get(item.entryId());
				if (path == null) continue;
				statement.setObject(1, item.entryId() == entryId ? targetParent : item.parentEntryId());
				statement.setString(2, item.entryId() == entryId ? targetName : item.name());
				statement.setString(3, path);
				statement.setLong(4, exerciseId);
				statement.setLong(5, item.entryId());
				requireOne(statement.executeUpdate());
			}
		}
	}

	static String checkedPath(String parent, String name) {
		StudentExerciseInput.validateName(name);
		String path = parent == null || parent.isEmpty() ? name : parent + "/" + name;
		if (path.codePointCount(0, path.length()) > StudentExerciseInput.MAX_PATH_CHARACTERS) {
			throw new IllegalArgumentException("フォルダ内の項目も含め、パス全体は1000文字以内にしてください。");
		}
		return path;
	}

	private static String checkedUploadPath(String basePath, String relativePath) {
		String path = basePath;
		for (String part : relativePath.split("/", -1)) {
			path = checkedPath(path, part);
		}
		return path;
	}

	public ExerciseSaveResult changeTrash(Connection connection, long userId, long exerciseId,
			long entryId, long version, boolean restore) throws SQLException {
		if (restore) return restore(connection, userId, exerciseId, entryId, version, false, null, null);
		Scope scope = requireMutableScope(connection, userId, exerciseId, version);
		List<StudentExerciseEntry> entries = findEntries(connection, exerciseId);
		requireActiveEntry(entries, entryId);
		var members = new HashSet<Long>();
		members.add(entryId);
		var pending = new java.util.ArrayDeque<Long>();
		pending.add(entryId);
		while (!pending.isEmpty()) {
			long parent = pending.removeFirst();
			for (var child : entries) {
				if (java.util.Objects.equals(child.parentEntryId(), parent) && child.status() == Status.ACTIVE
						&& child.trashRootEntryId() == null) {
					if (!members.add(child.entryId())) throw new SQLException("Cyclic exercise hierarchy.");
					pending.add(child.entryId());
				}
			}
		}
		Timestamp now = Timestamp.from(java.time.Instant.now());
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE student_exercise_entries SET entry_status = ?, trash_root_entry_id = ?, trashed_at = ?,
				  updated_at = CURRENT_TIMESTAMP WHERE student_exercise_id = ? AND exercise_entry_id = ?
				""")) {
			for (long member : members) {
				statement.setString(1, member == entryId ? "trashed" : "active");
				statement.setLong(2, entryId);
				statement.setTimestamp(3, now);
				statement.setLong(4, exerciseId);
				statement.setLong(5, member);
				requireOne(statement.executeUpdate());
			}
		}
		updateScope(connection, exerciseId, version, scope.state().getValue(), scope.saved() ? "saved" : "unsaved");
		return new ExerciseSaveResult(exerciseId, entryId, version + 1);
	}

	public ExerciseSaveResult restore(Connection connection, long userId, long exerciseId, long entryId,
			long version, boolean destinationSpecified, Long parentId, String name) throws SQLException {
		return restoreEntry(connection, userId, exerciseId, entryId, version, destinationSpecified,
				parentId, name, true);
	}

	private ExerciseSaveResult restoreEntry(Connection connection, long userId, long exerciseId, long entryId,
			long version, boolean destinationSpecified, Long parentId, String name, boolean updateVersion)
			throws SQLException {
		Scope scope = requireMutableScope(connection, userId, exerciseId, version);
		var entries = findEntries(connection, exerciseId);
		var index = index(entries);
		var entry = index.get(entryId);
		if (entry == null || entry.status() != Status.TRASHED) throw new ExerciseNotFoundException();
		var visited = new HashSet<Long>();
		boolean unrecoverableParent = false;
		for (Long ancestorId = entry.parentEntryId(); ancestorId != null;) {
			if (!visited.add(ancestorId)) throw new SQLException("Cyclic exercise hierarchy.");
			var ancestor = index.get(ancestorId);
			if (ancestor == null) {
				unrecoverableParent = true;
				break;
			}
			if (ancestor.status() == Status.TRASHED) {
				throw new IllegalArgumentException("元の親フォルダがごみ箱にあります。先に親フォルダを復元してください。");
			}
			unrecoverableParent |= ancestor.status() == Status.DELETED;
			ancestorId = ancestor.parentEntryId();
		}
		if (unrecoverableParent && !destinationSpecified) {
			throw new IllegalArgumentException("元の親フォルダは復元できません。復元先のフォルダまたはルートを選んでください。");
		}
		if (destinationSpecified && !unrecoverableParent) {
			throw new IllegalArgumentException("元の場所へ復元してください。同名がある場合は別の名前を入力してください。");
		}
		Long targetParent = destinationSpecified ? parentId : entry.parentEntryId();
		String parentPath = null;
		if (targetParent != null) {
			var parent = requireActiveEntry(entries, targetParent);
			if (parent.type() != Type.FOLDER) throw new IllegalArgumentException("復元先はフォルダを指定してください。");
			parentPath = parent.path();
		}
		String targetName = name == null || name.equals(entry.name()) ? entry.name()
				: new StudentExerciseInput(entry.type(), name).name();
		updateHierarchy(connection, exerciseId, entries, entry, targetParent, targetName, parentPath, entryId);
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE student_exercise_entries SET trash_root_entry_id = NULL, trashed_at = NULL,
				  entry_status = CASE WHEN exercise_entry_id = ? THEN 'active' ELSE entry_status END,
				  updated_at = CURRENT_TIMESTAMP
				WHERE student_exercise_id = ? AND (exercise_entry_id = ?
				  OR (trash_root_entry_id = ? AND entry_status = 'active'))
				""")) {
			statement.setLong(1, entryId);
			statement.setLong(2, exerciseId);
			statement.setLong(3, entryId);
			statement.setLong(4, entryId);
			if (statement.executeUpdate() < 1) throw new SQLException("Exercise restore affected no entries.");
		}
		if (updateVersion) {
			updateScope(connection, exerciseId, version, scope.state().getValue(), scope.saved() ? "saved" : "unsaved");
		}
		return new ExerciseSaveResult(exerciseId, entryId, version + (updateVersion ? 1 : 0));
	}

	private List<StudentExerciseEntry> normalizeRoots(List<StudentExerciseEntry> entries, List<Long> selectedIds,
			Status expectedStatus) throws SQLException {
		if (selectedIds == null || selectedIds.isEmpty()) {
			throw new IllegalArgumentException("操作対象を選択してください。");
		}
		var unique = new HashSet<Long>();
		Map<Long, StudentExerciseEntry> byId = index(entries);
		var selected = new ArrayList<StudentExerciseEntry>();
		for (Long id : selectedIds) {
			if (id == null || id <= 0 || !unique.add(id)) {
				throw new IllegalArgumentException("操作対象の指定が重複または不正です。");
			}
			var entry = byId.get(id);
			if (entry == null) throw new ExerciseNotFoundException();
			if (expectedStatus == Status.ACTIVE) {
				requireActiveEntry(entries, id);
			} else if (entry.status() != expectedStatus) {
				throw new ExerciseNotFoundException();
			}
			selected.add(entry);
		}
		var roots = new ArrayList<StudentExerciseEntry>();
		for (var entry : selected) {
			boolean covered = false;
			var visited = new HashSet<Long>();
			Long parentId = entry.parentEntryId();
			while (parentId != null) {
				if (!visited.add(parentId)) throw new SQLException("Cyclic exercise hierarchy.");
				var parent = byId.get(parentId);
				if (parent == null || parent.type() != Type.FOLDER || parent.exerciseId() != entry.exerciseId()) {
					throw new SQLException("The exercise hierarchy is inconsistent.");
				}
				if (unique.contains(parentId)) {
					covered = true;
					break;
				}
				parentId = parent.parentEntryId();
			}
			if (!covered) roots.add(entry);
		}
		return List.copyOf(roots);
	}

	private static boolean isWithin(StudentExerciseEntry entry, long ancestorId,
			Map<Long, StudentExerciseEntry> byId) throws SQLException {
		var visited = new HashSet<Long>();
		Long currentId = entry.entryId();
		while (currentId != null) {
			if (!visited.add(currentId)) throw new SQLException("Cyclic exercise hierarchy.");
			if (currentId == ancestorId) return true;
			var current = byId.get(currentId);
			if (current == null) throw new SQLException("The exercise hierarchy is inconsistent.");
			currentId = current.parentEntryId();
		}
		return false;
	}

	private static int ancestorDepth(StudentExerciseEntry entry, Map<Long, StudentExerciseEntry> index)
			throws SQLException {
		int depth = 0;
		var visited = new HashSet<Long>();
		for (Long parentId = entry.parentEntryId(); parentId != null;) {
			if (!visited.add(parentId)) throw new SQLException("Cyclic exercise hierarchy.");
			var parent = index.get(parentId);
			if (parent == null || parent.type() != Type.FOLDER) {
				throw new SQLException("The exercise hierarchy is inconsistent.");
			}
			depth++;
			parentId = parent.parentEntryId();
		}
		return depth;
	}

	private static StudentExerciseEntry withPath(StudentExerciseEntry entry, String path) {
		return new StudentExerciseEntry(entry.entryId(), entry.exerciseId(), entry.parentEntryId(), entry.type(),
				entry.name(), path, entry.description(), entry.content(), entry.status(), entry.updatedAt(),
				entry.trashRootEntryId(), entry.trashedAt());
	}

	private List<UploadNode> uploadNodes(Connection connection, entity.ExerciseUpload upload) throws SQLException {
		var nodes = new LinkedHashMap<String, UploadNode>();
		for (var file : upload.files()) {
			String[] parts = file.path().split("/", -1);
			String prefix = "";
			for (int i = 0; i < parts.length - 1; i++) {
				prefix = prefix.isEmpty() ? parts[i] : prefix + "/" + parts[i];
				addUploadNode(connection, nodes, new UploadNode(prefix, Type.FOLDER));
			}
			addUploadNode(connection, nodes, new UploadNode(file.path(), Type.FILE));
		}
		return List.copyOf(nodes.values());
	}

	private void addUploadNode(Connection connection, Map<String, UploadNode> nodes, UploadNode node)
			throws SQLException {
		String key = databasePathKey(connection, node.path());
		UploadNode existing = nodes.get(key);
		if (existing == null) {
			nodes.put(key, node);
			return;
		}
		if (existing.type() != Type.FOLDER || node.type() != Type.FOLDER
				|| !existing.path().equals(node.path())) {
			throw new IllegalArgumentException("同じ送信内で同名または曖昧なアップロードパスがあります。");
		}
	}

	private static boolean isSkipped(String sourcePath, HashSet<String> skippedFolders) {
		for (String skipped : skippedFolders) {
			if (sourcePath.equals(skipped) || sourcePath.startsWith(skipped + "/")) return true;
		}
		return false;
	}

	private static ExerciseConflictException uploadNameConflict(String message) {
		return new ExerciseConflictException(message, ExerciseConflictException.Reason.NAME);
	}

	private static String parentPath(List<StudentExerciseEntry> entries, Long parentId) throws SQLException {
		if (parentId == null) return null;
		var parent = entries.stream().filter(entry -> entry.entryId() == parentId).findFirst()
				.orElseThrow(ExerciseNotFoundException::new);
		return parent.path();
	}

	private String suggestName(Connection connection, long exerciseId, String parentPath, String sourceName,
			Type type, List<String> reservedPaths) throws SQLException {
		String stem = sourceName;
		String extension = "";
		if (type == Type.FILE && sourceName.toLowerCase(java.util.Locale.ROOT).endsWith(".py")) {
			stem = sourceName.substring(0, sourceName.length() - 3);
			extension = sourceName.substring(sourceName.length() - 3);
		}
		var reserved = new HashSet<String>();
		for (String path : reservedPaths) reserved.add(databasePathKey(connection, path));
		for (int suffix = 2; suffix < 10000; suffix++) {
			String candidate = cloneName(type, stem + " (" + suffix + ")" + extension);
			String path = checkedPath(parentPath, candidate);
			if (entryAtPath(connection, exerciseId, path) == null
					&& !reserved.contains(databasePathKey(connection, path))) return candidate;
		}
		throw new IllegalArgumentException("空いている別名を作成できません。別の名前を入力してください。");
	}

	private static String cloneName(Type type, String name) {
		if (type == Type.FILE) return new StudentExerciseInput(type, name).name();
		StudentExerciseInput.validateName(name);
		return name;
	}

	private static void requireNamesForRoots(Map<Long, String> names,
			List<StudentExerciseEntry> roots) {
		var rootIds = roots.stream().map(StudentExerciseEntry::entryId)
				.collect(java.util.stream.Collectors.toSet());
		if (!rootIds.containsAll(names.keySet())) {
			throw new IllegalArgumentException("別名は親子整理後の操作対象にだけ指定できます。");
		}
	}

	private boolean hasDuplicateDatabasePaths(Connection connection, Iterable<String> paths) throws SQLException {
		var seen = new HashSet<String>();
		for (String path : paths) {
			if (!seen.add(databasePathKey(connection, path))) return true;
		}
		return false;
	}

	private String databasePathKey(Connection connection, String path) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT HEX(WEIGHT_STRING(CONVERT(? USING utf8mb4) COLLATE utf8mb4_0900_ai_ci))")) {
			statement.setString(1, path);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) throw new SQLException("Database path comparison failed.");
				return rows.getString(1);
			}
		}
	}

	private void stagePaths(Connection connection, long exerciseId, Iterable<Long> entryIds) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE student_exercise_entries SET path = ?
				WHERE student_exercise_id = ? AND exercise_entry_id = ?
				""")) {
			for (long entryId : entryIds) {
				statement.setString(1, "\u0001batch_" + UUID.randomUUID() + "_" + entryId);
				statement.setLong(2, exerciseId);
				statement.setLong(3, entryId);
				requireOne(statement.executeUpdate());
			}
		}
	}

	private Scope requireMutableScope(Connection connection, long userId, long id, long version) throws SQLException {
		Scope scope = requireScope(connection, userId, id, true);
		if (!scope.state().isEditable()) {
			throw new IllegalArgumentException("この状態の演習は編集できません。");
		}
		if (scope.version() != version) {
			throw new ExerciseConflictException("演習が更新されています。入力を控えて画面を再読み込みしてください。");
		}
		return scope;
	}

	private long insertScope(Connection connection, long userId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO student_exercises (student_user_id, exercise_origin, scope_name,
				  exercise_status, save_status, started_at, created_at)
				VALUES (?, 'student_created', '授業演習', 'in_progress', 'unsaved', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, userId);
			statement.executeUpdate();
			return generatedId(statement);
		}
	}

	private void updateScope(Connection connection, long id, long version, String state, String saveStatus)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE student_exercises SET exercise_status = ?, save_status = ?, version = version + 1,
				  started_at = COALESCE(started_at, CURRENT_TIMESTAMP), updated_at = CURRENT_TIMESTAMP
				WHERE student_exercise_id = ? AND version = ?
				""")) {
			statement.setString(1, state);
			statement.setString(2, saveStatus);
			statement.setLong(3, id);
			statement.setLong(4, version);
			requireOne(statement.executeUpdate());
		}
	}

	private static Map<Long, StudentExerciseEntry> index(List<StudentExerciseEntry> entries) {
		Map<Long, StudentExerciseEntry> index = new HashMap<>();
		for (StudentExerciseEntry entry : entries) index.put(entry.entryId(), entry);
		return index;
	}

	private static boolean hasAncestorStatus(StudentExerciseEntry entry,
			Map<Long, StudentExerciseEntry> index, Status status) throws SQLException {
		boolean matched = false;
		var visited = new HashSet<Long>();
		visited.add(entry.entryId());
		Long parentId = entry.parentEntryId();
		while (parentId != null) {
			StudentExerciseEntry parent = index.get(parentId);
			if (parent == null || parent.exerciseId() != entry.exerciseId()
					|| parent.type() != Type.FOLDER || !visited.add(parentId)) {
				throw new SQLException("The exercise hierarchy is inconsistent.");
			}
			matched |= parent.status() == status;
			parentId = parent.parentEntryId();
		}
		return matched;
	}

	private static Scope mapScope(ResultSet rows) throws SQLException {
		return new Scope(rows.getLong("student_exercise_id"), rows.getString("scope_name"),
				rows.getString("exercise_origin"), State.fromValue(rows.getString("exercise_status")),
				"saved".equals(rows.getString("save_status")), rows.getLong("version"));
	}

	private static long generatedId(PreparedStatement statement) throws SQLException {
		try (ResultSet keys = statement.getGeneratedKeys()) {
			if (!keys.next()) throw new SQLException("Exercise insert did not return an identifier.");
			return keys.getLong(1);
		}
	}

	private static void requireOne(int count) throws SQLException {
		if (count != 1) throw new SQLException("Exercise update affected an unexpected row count.");
	}

	private record UploadNode(String path, Type type) {}
	private record MappedUploadFolder(long entryId, String path) {}
	private record ClonePlan(long rootId, StudentExerciseEntry source, String path, Long parentEntryId) {}
}
