package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;

import entity.StudentExerciseEntry;
import entity.TeacherClassOption;
import entity.TeacherDistributionInput;
import entity.TeacherDistributionInput.Target;
import entity.TeacherDistributionPage;
import entity.TeacherDistributionPage.DistributionSummary;
import entity.TeacherDistributionPage.HistorySummary;
import entity.TeacherDistributionPage.TargetSummary;
import entity.TeacherDistributionPage.TemplateSummary;
import entity.TeacherSchoolOption;

public final class TeacherDistributionDao {
	private static final Logger LOGGER = Logger.getLogger(TeacherDistributionDao.class.getName());
	private static final Gson JSON = new Gson();
	private final TeacherPermissionDao permissions;

	public TeacherDistributionDao() {
		this(new TeacherPermissionDao());
	}

	public TeacherDistributionDao(TeacherPermissionDao permissions) {
		this.permissions = java.util.Objects.requireNonNull(permissions);
	}

	public record TemplateDefinition(long templateId, int version, String name, String rootName,
			List<TeacherDistributionInput.TemplateItem> items) {
		public TemplateDefinition {
			items = List.copyOf(items);
		}
	}

	private static Map<String, Object> templateChangeDetail(
			TemplateDefinition previous, TeacherDistributionInput input) {
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put("templateName", input.name());
		detail.put("rootName", input.rootName());
		detail.put("itemCount", input.items().size());
		detail.put("itemChanges", diffItems(previous == null ? List.of() : previous.items(), input.items()));
		if (previous != null && !previous.name().equals(input.name())) {
			detail.put("templateNameChange", Map.of("before", previous.name(), "after", input.name()));
		}
		if (previous != null && !previous.rootName().equals(input.rootName())) {
			detail.put("rootNameChange", Map.of("before", previous.rootName(), "after", input.rootName()));
		}
		return detail;
	}

	private static List<Map<String, Object>> diffItems(
			List<TeacherDistributionInput.TemplateItem> before,
			List<TeacherDistributionInput.TemplateItem> after) {
		Map<String, TeacherDistributionInput.TemplateItem> oldItems = new HashMap<>();
		Map<String, TeacherDistributionInput.TemplateItem> newItems = new HashMap<>();
		for (var item : before) oldItems.put(item.path().toLowerCase(Locale.ROOT), item);
		for (var item : after) newItems.put(item.path().toLowerCase(Locale.ROOT), item);
		Set<String> paths = new java.util.TreeSet<>();
		paths.addAll(oldItems.keySet());
		paths.addAll(newItems.keySet());
		List<Map<String, Object>> changes = new ArrayList<>();
		for (String path : paths) {
			var oldItem = oldItems.get(path);
			var newItem = newItems.get(path);
			if (oldItem != null && newItem != null
					&& oldItem.path().equals(newItem.path())
					&& oldItem.type() == newItem.type()
					&& java.util.Objects.equals(oldItem.content(), newItem.content())) {
				continue;
			}
			Map<String, Object> change = new LinkedHashMap<>();
			change.put("path", newItem == null ? oldItem.path() : newItem.path());
			change.put("type", (newItem == null ? oldItem.type() : newItem.type()).getValue());
			change.put("change", oldItem == null ? "追加" : newItem == null ? "削除" : "更新");
			if (oldItem != null && oldItem.type() == StudentExerciseEntry.Type.FILE) {
				change.put("beforeContent", oldItem.content());
			}
			if (newItem != null && newItem.type() == StudentExerciseEntry.Type.FILE) {
				change.put("afterContent", newItem.content());
			}
			changes.add(change);
		}
		return List.copyOf(changes);
	}

	public record ScheduledDistribution(long distributionId, List<Long> immediateTargetIds) {
		public ScheduledDistribution {
			immediateTargetIds = List.copyOf(immediateTargetIds);
		}
	}

	public TeacherDistributionPage loadPage(Connection connection, long teacherId) throws SQLException {
		List<TeacherSchoolOption> schools = permissions.findDistributionAuthorizedSchools(connection, teacherId);
		List<TeacherClassOption> classes = new ArrayList<>();
		for (TeacherSchoolOption school : schools) {
			classes.addAll(permissions.findDistributionAuthorizedClasses(connection, teacherId, school.schoolId()));
		}
		Set<Long> authorizedClassIds = classes.stream().map(TeacherClassOption::classroomId)
				.collect(java.util.stream.Collectors.toSet());
		return new TeacherDistributionPage(schools, classes, findTemplates(connection, teacherId),
				findDistributions(connection, teacherId, authorizedClassIds));
	}

	public void requireCodeDistributionAccess(Connection connection, long teacherId) throws SQLException {
		permissions.requireCodeDistributionAccess(connection, teacherId);
	}

	public TemplateDefinition findTemplate(Connection connection, long teacherId, long templateId)
			throws SQLException {
		permissions.requireCodeDistributionAccess(connection, teacherId);
		String name;
		String rootName;
		int version;
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT template_name, COALESCE(root_path, template_name) AS root_path, template_version
				FROM distribution_templates
				WHERE distribution_template_id = ? AND created_by_user_id = ?
				  AND template_status = 'active'
				FOR UPDATE
				""")) {
			statement.setLong(1, templateId);
			statement.setLong(2, teacherId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) throw new IllegalArgumentException("配信テンプレートが見つかりません。");
				name = rows.getString("template_name");
				rootName = rows.getString("root_path");
				version = rows.getInt("template_version");
			}
		}
		return new TemplateDefinition(templateId, version, name, rootName, findTemplateItems(connection, templateId));
	}

	public TemplateSummary saveDraft(Connection connection, long teacherId, TeacherDistributionInput input)
			throws SQLException {
		permissions.requireCodeDistributionAccess(connection, teacherId);
		TemplateDefinition previous = input.templateId() == 0
				? null : findTemplate(connection, teacherId, input.templateId());
		long templateId = saveTemplate(connection, teacherId, input, "draft");
		long distributionId = findOrCreateDraftDistribution(connection, teacherId, templateId);
		refreshSnapshot(connection, distributionId, input.name(), input.rootName(), input.items());
		Map<String, Object> detail = templateChangeDetail(previous, input);
		detail.put("templateId", templateId);
		recordHistory(connection, distributionId, teacherId,
				previous == null ? "テンプレート作成" : "テンプレート編集", detail, true);
		return findTemplateSummary(connection, teacherId, templateId);
	}

	public ScheduledDistribution schedule(Connection connection, long teacherId, TeacherDistributionInput input)
			throws SQLException {
		permissions.requireCodeDistributionAccess(connection, teacherId);
		if (input.targets().isEmpty()) throw new IllegalArgumentException("配信先のクラスを選択してください。");
		TemplateDefinition previous = input.templateId() == 0
				? null : findTemplate(connection, teacherId, input.templateId());
		Long distributionSchoolId = null;
		for (Target target : input.targets()) {
			long schoolId = findSchoolForClass(connection, target.classroomId());
			if (distributionSchoolId != null && distributionSchoolId.longValue() != schoolId) {
				throw new IllegalArgumentException("1回の配信で選択できる学校は1校のみです。");
			}
			distributionSchoolId = schoolId;
			permissions.requireDistributionAuthorizedClass(connection, teacherId, target.classroomId(),
					schoolId);
		}
		long existingDistribution = findDistributionByRequestToken(connection, teacherId, input.requestToken());
		if (existingDistribution > 0) {
			return new ScheduledDistribution(existingDistribution,
					findImmediateTargetIds(connection, existingDistribution));
		}
		long templateId = saveTemplate(connection, teacherId, input, "saved");
		boolean hasPreviousDelivery = hasPreviousDelivery(connection, templateId);
		String initialStatus = input.targets().stream().anyMatch(target -> target.scheduledAt() != null)
				? "scheduled" : "in_progress";
		long distributionId = insertDistribution(connection, teacherId, templateId,
				input.name(), input.rootName(), initialStatus, input.requestToken());
		insertSnapshotFiles(connection, distributionId, input.items());
		List<Long> immediateTargetIds = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO distribution_targets
				  (distribution_id, classroom_id, target_status, scheduled_at)
				VALUES (?, ?, ?, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			for (Target target : input.targets()) {
				statement.setLong(1, distributionId);
				statement.setLong(2, target.classroomId());
				statement.setString(3, target.scheduledAt() == null ? "not_distributed" : "scheduled");
				statement.setObject(4, target.scheduledAt());
				statement.executeUpdate();
				try (ResultSet keys = statement.getGeneratedKeys()) {
					if (!keys.next()) throw new SQLException("Distribution target ID was not generated.");
					if (target.scheduledAt() == null) immediateTargetIds.add(keys.getLong(1));
				}
			}
		}
		Map<String, Object> detail = templateChangeDetail(previous, input);
		detail.put("templateId", templateId);
		detail.put("distributionId", distributionId);
		detail.put("targets", input.targets().stream().map(target -> Map.of(
				"classroomId", target.classroomId(),
				"scheduledAt", target.scheduledAt() == null ? "immediate" : target.scheduledAt().toString())).toList());
		recordHistory(connection, distributionId, teacherId, hasPreviousDelivery ? "再配信設定" : "配信設定", detail, true);
		return new ScheduledDistribution(distributionId, immediateTargetIds);
	}

	public void archive(Connection connection, long teacherId, long templateId, int expectedVersion)
			throws SQLException {
		permissions.requireCodeDistributionAccess(connection, teacherId);
		long distributionId = findLatestDistribution(connection, teacherId, templateId);
		if (distributionId == 0) {
			distributionId = findOrCreateDraftDistribution(connection, teacherId, templateId);
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE distribution_templates
				SET template_status = 'archived', template_version = template_version + 1,
				    updated_by_user_id = ?, updated_at = CURRENT_TIMESTAMP
				WHERE distribution_template_id = ? AND created_by_user_id = ?
				  AND template_status = 'active' AND template_version = ?
				""")) {
			statement.setLong(1, teacherId);
			statement.setLong(2, templateId);
			statement.setLong(3, teacherId);
			statement.setInt(4, expectedVersion);
			if (statement.executeUpdate() != 1) throw new IllegalArgumentException("テンプレートが更新されています。再読み込みしてください。");
		}
		recordHistory(connection, distributionId, teacherId, "アーカイブ",
				Map.of("templateId", templateId), true);
	}

	public void stop(Connection connection, long teacherId, long distributionId) throws SQLException {
		permissions.requireCodeDistributionAccess(connection, teacherId);
		requireOwnedDistribution(connection, teacherId, distributionId, true);
		List<TargetRow> targets = findTargets(connection, distributionId, true);
		for (TargetRow target : targets) {
			permissions.requireDistributionAuthorizedClass(connection, teacherId, target.classroomId(),
					target.schoolId());
		}
		int changed = 0;
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE distribution_targets
				SET target_status = 'stopped'
				WHERE distribution_id = ? AND target_status IN ('not_distributed','scheduled')
				""")) {
			statement.setLong(1, distributionId);
			changed = statement.executeUpdate();
		}
		refreshDistributionStatus(connection, distributionId);
		recordHistory(connection, distributionId, teacherId, "停止",
				Map.of("changedTargets", changed), true);
	}

	public void resume(Connection connection, long teacherId, long distributionId) throws SQLException {
		permissions.requireCodeDistributionAccess(connection, teacherId);
		requireOwnedDistribution(connection, teacherId, distributionId, true);
		List<TargetRow> targets = findTargets(connection, distributionId, true);
		for (TargetRow target : targets) {
			permissions.requireDistributionAuthorizedClass(connection, teacherId, target.classroomId(),
					target.schoolId());
		}
		int changed = 0;
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE distribution_targets
				SET target_status = CASE
				      WHEN scheduled_at IS NULL THEN 'not_distributed' ELSE 'scheduled' END,
				    execution_result = NULL
				WHERE distribution_id = ? AND target_status = 'stopped'
				""")) {
			statement.setLong(1, distributionId);
			changed = statement.executeUpdate();
		}
		refreshDistributionStatus(connection, distributionId);
		recordHistory(connection, distributionId, teacherId, "再開",
				Map.of("changedTargets", changed), true);
	}

	public void stopTarget(Connection connection, long teacherId, long targetId) throws SQLException {
		permissions.requireCodeDistributionAccess(connection, teacherId);
		DeliveryTarget target = lockDeliveryTarget(connection, targetId);
		if (target == null) throw new IllegalArgumentException("配信対象が見つかりません。");
		requireOwnedDistribution(connection, teacherId, target.distributionId(), false);
		permissions.requireDistributionAuthorizedClass(connection, teacherId, target.classroomId(), target.schoolId());
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE distribution_targets SET target_status = 'stopped'
				WHERE distribution_target_id = ? AND target_status IN ('not_distributed','scheduled')
				""")) {
			statement.setLong(1, targetId);
			if (statement.executeUpdate() != 1) throw new IllegalArgumentException("このクラスは停止できない状態です。");
		}
		refreshDistributionStatus(connection, target.distributionId());
		recordHistory(connection, target.distributionId(), teacherId, "クラス停止",
				Map.of("classroomId", target.classroomId()), true);
	}

	public void resumeTarget(Connection connection, long teacherId, long targetId) throws SQLException {
		permissions.requireCodeDistributionAccess(connection, teacherId);
		DeliveryTarget target = lockDeliveryTarget(connection, targetId);
		if (target == null) throw new IllegalArgumentException("配信対象が見つかりません。");
		requireOwnedDistribution(connection, teacherId, target.distributionId(), false);
		permissions.requireDistributionAuthorizedClass(connection, teacherId, target.classroomId(), target.schoolId());
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE distribution_targets
				SET target_status = CASE
				      WHEN scheduled_at IS NULL THEN 'not_distributed' ELSE 'scheduled' END,
				    execution_result = NULL
				WHERE distribution_target_id = ? AND target_status = 'stopped'
				""")) {
			statement.setLong(1, targetId);
			if (statement.executeUpdate() != 1) throw new IllegalArgumentException("このクラスは再開できない状態です。");
		}
		refreshDistributionStatus(connection, target.distributionId());
		recordHistory(connection, target.distributionId(), teacherId, "クラス再開",
				Map.of("classroomId", target.classroomId()), true);
	}

	public void rescheduleTarget(Connection connection, long teacherId, long targetId,
			LocalDateTime scheduledAt) throws SQLException {
		permissions.requireCodeDistributionAccess(connection, teacherId);
		if (scheduledAt == null || !scheduledAt.isAfter(LocalDateTime.now())) {
			throw new IllegalArgumentException("配信日時は現在より後に設定してください。");
		}
		DeliveryTarget target = lockDeliveryTarget(connection, targetId);
		if (target == null) throw new IllegalArgumentException("配信対象が見つかりません。");
		requireOwnedDistribution(connection, teacherId, target.distributionId(), false);
		permissions.requireDistributionAuthorizedClass(connection, teacherId, target.classroomId(), target.schoolId());
		if (!"scheduled".equals(target.status()) || target.scheduledAt() == null) {
			throw new IllegalArgumentException("予約中のクラスだけ配信日時を変更できます。");
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE distribution_targets
				SET scheduled_at = ?, execution_result = NULL
				WHERE distribution_target_id = ? AND target_status = 'scheduled'
				""")) {
			statement.setObject(1, scheduledAt);
			statement.setLong(2, targetId);
			if (statement.executeUpdate() != 1) throw new IllegalArgumentException("配信状態が更新されています。再読み込みしてください。");
		}
		recordHistory(connection, target.distributionId(), teacherId, "配信日時変更",
				Map.of("classroomId", target.classroomId(),
						"before", target.scheduledAt().toString(), "after", scheduledAt.toString()), true);
	}

	public int processDueTargets() throws SQLException {
		List<Long> targetIds = new ArrayList<>();
		try (Connection connection = lib.mysql.Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT dt.distribution_target_id
						FROM distribution_targets dt
						JOIN distributions d ON d.distribution_id = dt.distribution_id
						WHERE dt.target_status IN ('not_distributed','scheduled')
						  AND (dt.scheduled_at IS NULL OR dt.scheduled_at <= CURRENT_TIMESTAMP)
						  AND d.distribution_status <> 'stopped'
						ORDER BY COALESCE(dt.scheduled_at, d.created_at), dt.distribution_target_id
						LIMIT 100
						""");
				ResultSet rows = statement.executeQuery()) {
			while (rows.next()) targetIds.add(rows.getLong(1));
		}
		int completed = 0;
		for (long targetId : targetIds) {
			if (processTarget(targetId)) completed++;
		}
		return completed;
	}

	public int processDueTargets(long distributionId) throws SQLException {
		List<Long> targetIds = new ArrayList<>();
		try (Connection connection = lib.mysql.Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT distribution_target_id FROM distribution_targets
						WHERE distribution_id = ? AND target_status = 'not_distributed'
						  AND scheduled_at IS NULL ORDER BY distribution_target_id
						""")) {
			statement.setLong(1, distributionId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) targetIds.add(rows.getLong(1));
			}
		}
		int completed = 0;
		for (long targetId : targetIds) {
			if (processTarget(targetId)) completed++;
		}
		return completed;
	}

	private boolean processTarget(long targetId) throws SQLException {
		try (Connection connection = lib.mysql.Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				DeliveryTarget target = lockDeliveryTarget(connection, targetId);
				if (target == null || !isDue(target)) {
					connection.rollback();
					return false;
				}
				if ("distributed".equals(target.status()) || "stopped".equals(target.status())
						|| "stopped".equals(target.distributionStatus())) {
					connection.rollback();
					return false;
				}
				permissions.requireDistributionAuthorizedClass(connection, target.teacherId(),
						target.classroomId(), target.schoolId());
				List<TeacherDistributionInput.TemplateItem> items = findSnapshotItems(connection, target.distributionId());
				List<Long> students = findActiveStudents(connection, target.classroomId());
				int created = 0;
				int skipped = 0;
				for (long studentId : students) {
					if (hasDeliveredTarget(connection, studentId, target.targetId())
							|| hasDeliveredDistribution(connection, studentId, target.distributionId())) {
						skipped++;
						continue;
					}
					String rootName = deliveryRootName(connection, studentId, target);
					insertStudentExercise(connection, studentId, target.targetId(), target.templateName(), rootName, items);
					created++;
				}
				String result = JSON.toJson(Map.of("status", "success", "studentsCreated", created,
						"studentsAlreadyDelivered", skipped));
				try (PreparedStatement statement = connection.prepareStatement("""
						UPDATE distribution_targets
						SET target_status = 'distributed', distributed_at = CURRENT_TIMESTAMP, execution_result = ?
						WHERE distribution_target_id = ? AND target_status IN ('not_distributed','scheduled')
						""")) {
					statement.setString(1, result);
					statement.setLong(2, target.targetId());
					if (statement.executeUpdate() != 1) throw new SQLException("Distribution target changed while being processed.");
				}
				refreshDistributionStatus(connection, target.distributionId());
				recordHistory(connection, target.distributionId(), target.teacherId(), "クラス配信完了",
						Map.of("classroomId", target.classroomId(), "studentsCreated", created,
								"studentsAlreadyDelivered", skipped), true);
				connection.commit();
				return true;
			} catch (SecurityException | IllegalArgumentException failure) {
				rollback(connection, failure);
				recordTerminalFailure(targetId, failure.getMessage());
				return false;
			} catch (SQLException | RuntimeException failure) {
				rollback(connection, failure);
				recordRetryableFailure(targetId, failure.getMessage());
				if (failure instanceof SQLException sqlFailure) throw sqlFailure;
				throw failure;
			}
		}
	}

	private void recordTerminalFailure(long targetId, String message) {
		try (Connection connection = lib.mysql.Client.createConnection()) {
			connection.setAutoCommit(false);
			DeliveryTarget target = lockDeliveryTarget(connection, targetId);
			if (target == null || "distributed".equals(target.status())) {
				connection.rollback();
				return;
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					UPDATE distribution_targets SET target_status = 'stopped', execution_result = ?
					WHERE distribution_target_id = ? AND target_status IN ('not_distributed','scheduled')
					""")) {
				statement.setString(1, failureResult(message));
				statement.setLong(2, targetId);
				statement.executeUpdate();
			}
			refreshDistributionStatus(connection, target.distributionId());
			recordHistory(connection, target.distributionId(), target.teacherId(), "クラス配信失敗",
					Map.of("classroomId", target.classroomId(), "message", safeMessage(message)), false);
			connection.commit();
		} catch (SQLException | RuntimeException failure) {
			LOGGER.log(Level.SEVERE, "Could not persist terminal distribution failure.", failure);
		}
	}

	private void recordRetryableFailure(long targetId, String message) {
		try (Connection connection = lib.mysql.Client.createConnection()) {
			connection.setAutoCommit(false);
			DeliveryTarget target = lockDeliveryTarget(connection, targetId);
			if (target == null || "distributed".equals(target.status()) || "stopped".equals(target.status())) {
				connection.rollback();
				return;
			}
			try (PreparedStatement statement = connection.prepareStatement("""
					UPDATE distribution_targets SET execution_result = ?
					WHERE distribution_target_id = ?
					""")) {
				statement.setString(1, failureResult(message));
				statement.setLong(2, targetId);
				statement.executeUpdate();
			}
			recordHistory(connection, target.distributionId(), target.teacherId(), "クラス配信失敗",
					Map.of("classroomId", target.classroomId(), "message", safeMessage(message)), false);
			connection.commit();
		} catch (SQLException | RuntimeException failure) {
			LOGGER.log(Level.SEVERE, "Could not persist retryable distribution failure.", failure);
		}
	}

	private long saveTemplate(Connection connection, long teacherId, TeacherDistributionInput input, String saveStatus)
			throws SQLException {
		long templateId;
		if (input.templateId() == 0) {
			try (PreparedStatement statement = connection.prepareStatement("""
					INSERT INTO distribution_templates
					  (created_by_user_id, updated_by_user_id, template_name, root_path,
					   save_status, template_status, overwrite_policy, created_at, updated_at)
					VALUES (?, ?, ?, ?, ?, 'active', 'append', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
					""", Statement.RETURN_GENERATED_KEYS)) {
				statement.setLong(1, teacherId);
				statement.setLong(2, teacherId);
				statement.setString(3, input.name());
				statement.setString(4, input.rootName());
				statement.setString(5, saveStatus);
				statement.executeUpdate();
				try (ResultSet keys = statement.getGeneratedKeys()) {
					if (!keys.next()) throw new SQLException("Distribution template ID was not generated.");
					templateId = keys.getLong(1);
				}
			}
		} else {
			try (PreparedStatement statement = connection.prepareStatement("""
					UPDATE distribution_templates
					SET updated_by_user_id = ?, template_name = ?, root_path = ?, save_status = ?,
					    template_version = template_version + 1, updated_at = CURRENT_TIMESTAMP
					WHERE distribution_template_id = ? AND created_by_user_id = ?
					  AND template_status = 'active' AND template_version = ?
					""")) {
				statement.setLong(1, teacherId);
				statement.setString(2, input.name());
				statement.setString(3, input.rootName());
				statement.setString(4, saveStatus);
				statement.setLong(5, input.templateId());
				statement.setLong(6, teacherId);
				statement.setInt(7, input.expectedVersion());
				if (statement.executeUpdate() != 1) {
					throw new IllegalArgumentException("テンプレートが更新またはアーカイブされています。再読み込みしてください。");
				}
			}
			templateId = input.templateId();
			try (PreparedStatement statement = connection.prepareStatement(
					"DELETE FROM distribution_template_files WHERE distribution_template_id = ?")) {
				statement.setLong(1, templateId);
				statement.executeUpdate();
			}
		}
		insertTemplateFiles(connection, templateId, input.items());
		return templateId;
	}

	private void insertTemplateFiles(Connection connection, long templateId,
			List<TeacherDistributionInput.TemplateItem> items) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO distribution_template_files
				  (distribution_template_id, path, entry_type, initial_content, entry_status)
				VALUES (?, ?, ?, ?, 'active')
				""")) {
			for (var item : items) {
				statement.setLong(1, templateId);
				statement.setString(2, item.path());
				statement.setString(3, item.type().getValue());
				statement.setString(4, item.content());
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	private long findOrCreateDraftDistribution(Connection connection, long teacherId, long templateId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT distribution_id FROM distributions
				WHERE distribution_template_id = ? AND executed_by_user_id = ? AND distribution_status = 'draft'
				ORDER BY distribution_id DESC LIMIT 1 FOR UPDATE
				""")) {
			statement.setLong(1, templateId);
			statement.setLong(2, teacherId);
			try (ResultSet rows = statement.executeQuery()) {
				if (rows.next()) return rows.getLong(1);
			}
		}
		TemplateDefinition template = findTemplate(connection, teacherId, templateId);
		long distributionId = insertDistribution(connection, teacherId, templateId,
				template.name(), template.rootName(), "draft", null);
		insertSnapshotFiles(connection, distributionId, template.items());
		return distributionId;
	}

	private long insertDistribution(Connection connection, long teacherId, long templateId,
			String name, String rootName, String status, String requestToken) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO distributions
				  (distribution_template_id, executed_by_user_id, distribution_status,
				   template_name_snapshot, root_path_snapshot, request_token, created_at)
				VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, templateId);
			statement.setLong(2, teacherId);
			statement.setString(3, status);
			statement.setString(4, name);
			statement.setString(5, rootName);
			statement.setString(6, requestToken);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) throw new SQLException("Distribution ID was not generated.");
				return keys.getLong(1);
			}
		}
	}

	private long findDistributionByRequestToken(Connection connection, long teacherId, String requestToken)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT distribution_id FROM distributions
				WHERE executed_by_user_id = ? AND request_token = ?
				FOR UPDATE
				""")) {
			statement.setLong(1, teacherId);
			statement.setString(2, requestToken);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? rows.getLong(1) : 0;
			}
		}
	}

	private List<Long> findImmediateTargetIds(Connection connection, long distributionId) throws SQLException {
		List<Long> ids = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT distribution_target_id FROM distribution_targets
				WHERE distribution_id = ? AND target_status = 'not_distributed'
				  AND scheduled_at IS NULL
				ORDER BY distribution_target_id
				""")) {
			statement.setLong(1, distributionId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) ids.add(rows.getLong(1));
			}
		}
		return List.copyOf(ids);
	}

	private void refreshSnapshot(Connection connection, long distributionId, String name, String rootName,
			List<TeacherDistributionInput.TemplateItem> items) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE distributions SET template_name_snapshot = ?, root_path_snapshot = ?
				WHERE distribution_id = ? AND distribution_status = 'draft'
				""")) {
			statement.setString(1, name);
			statement.setString(2, rootName);
			statement.setLong(3, distributionId);
			if (statement.executeUpdate() != 1) throw new IllegalArgumentException("配信済みテンプレートは下書きとして編集できません。");
		}
		try (PreparedStatement statement = connection.prepareStatement(
				"DELETE FROM distribution_snapshot_files WHERE distribution_id = ?")) {
			statement.setLong(1, distributionId);
			statement.executeUpdate();
		}
		insertSnapshotFiles(connection, distributionId, items);
	}

	private void insertSnapshotFiles(Connection connection, long distributionId,
			List<TeacherDistributionInput.TemplateItem> items) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO distribution_snapshot_files
				  (distribution_id, path, entry_type, initial_content)
				VALUES (?, ?, ?, ?)
				""")) {
			for (var item : items) {
				statement.setLong(1, distributionId);
				statement.setString(2, item.path());
				statement.setString(3, item.type().getValue());
				statement.setString(4, item.content());
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}

	private List<TeacherDistributionInput.TemplateItem> findTemplateItems(Connection connection, long templateId)
			throws SQLException {
		return findItems(connection, """
				SELECT path, entry_type, initial_content
				FROM distribution_template_files
				WHERE distribution_template_id = ? AND entry_status = 'active'
				ORDER BY path
				""", templateId);
	}

	private List<TeacherDistributionInput.TemplateItem> findSnapshotItems(Connection connection, long distributionId)
			throws SQLException {
		return findItems(connection, """
				SELECT path, entry_type, initial_content
				FROM distribution_snapshot_files WHERE distribution_id = ? ORDER BY path
				""", distributionId);
	}

	private List<TeacherDistributionInput.TemplateItem> findItems(Connection connection, String sql, long parentId)
			throws SQLException {
		List<TeacherDistributionInput.TemplateItem> items = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, parentId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					items.add(new TeacherDistributionInput.TemplateItem(rows.getString("path"),
							StudentExerciseEntry.Type.fromValue(rows.getString("entry_type")),
							rows.getString("initial_content")));
				}
			}
		}
		return List.copyOf(items);
	}

	private List<TemplateSummary> findTemplates(Connection connection, long teacherId) throws SQLException {
		List<TemplateSummary> templates = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT distribution_template_id, template_name, root_path, template_version,
				       save_status, template_status
				FROM distribution_templates
				WHERE created_by_user_id = ? AND template_status = 'active'
				ORDER BY updated_at DESC, distribution_template_id DESC
				""")) {
			statement.setLong(1, teacherId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					templates.add(new TemplateSummary(rows.getLong("distribution_template_id"),
							rows.getString("template_name"), rows.getString("root_path"),
							rows.getInt("template_version"), rows.getString("save_status"),
							rows.getString("template_status")));
				}
			}
		}
		return List.copyOf(templates);
	}

	private TemplateSummary findTemplateSummary(Connection connection, long teacherId, long templateId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT distribution_template_id, template_name, root_path, template_version,
				       save_status, template_status
				FROM distribution_templates
				WHERE distribution_template_id = ? AND created_by_user_id = ?
				""")) {
			statement.setLong(1, templateId);
			statement.setLong(2, teacherId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) throw new IllegalArgumentException("配信テンプレートが見つかりません。");
				return new TemplateSummary(rows.getLong("distribution_template_id"), rows.getString("template_name"),
						rows.getString("root_path"), rows.getInt("template_version"),
						rows.getString("save_status"), rows.getString("template_status"));
			}
		}
	}

	private List<DistributionSummary> findDistributions(
			Connection connection, long teacherId, Set<Long> authorizedClassIds) throws SQLException {
		List<DistributionSummary> distributions = new ArrayList<>();
		String allowedTargets = authorizedClassIds.isEmpty()
				? """
				  NOT EXISTS (
				    SELECT 1 FROM distribution_targets scoped_target
				    WHERE scoped_target.distribution_id = d.distribution_id)
				  """
				: """
				  NOT EXISTS (
				    SELECT 1 FROM distribution_targets scoped_target
				    WHERE scoped_target.distribution_id = d.distribution_id
				      AND scoped_target.classroom_id NOT IN (%s))
				  """.formatted(String.join(",", java.util.Collections.nCopies(authorizedClassIds.size(), "?")));
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT d.distribution_id, d.distribution_template_id, d.template_name_snapshot,
				       d.distribution_status,
				       DATE_FORMAT(d.created_at, '%Y-%m-%d %H:%i') AS created_at
				FROM distributions d
				WHERE d.executed_by_user_id = ? AND __ALLOWED_TARGETS__
				ORDER BY d.created_at DESC, d.distribution_id DESC
				LIMIT 100
				""".replace("__ALLOWED_TARGETS__", allowedTargets))) {
			statement.setLong(1, teacherId);
			int parameter = 2;
			for (long classroomId : authorizedClassIds) statement.setLong(parameter++, classroomId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					long distributionId = rows.getLong("distribution_id");
					List<TargetSummary> targets = findTargetSummaries(connection, distributionId);
					String schoolNames = targets.stream().map(TargetSummary::schoolName).distinct()
							.collect(java.util.stream.Collectors.joining(", "));
					String classroomNames = targets.stream().map(TargetSummary::classroomName)
							.collect(java.util.stream.Collectors.joining(", "));
					distributions.add(new DistributionSummary(distributionId,
							rows.getString("template_name_snapshot"), rows.getString("distribution_status"),
							rows.getLong("distribution_template_id"), rows.getString("created_at"),
							schoolNames, classroomNames, targets, findHistorySummaries(connection, distributionId)));
				}
			}
		}
		return List.copyOf(distributions);
	}

	private List<TargetSummary> findTargetSummaries(Connection connection, long distributionId) throws SQLException {
		List<TargetSummary> targets = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT dt.distribution_target_id, dt.classroom_id, s.name AS school_name,
				       CONCAT(COALESCE(c.grade_name, ''), CASE WHEN c.grade_name IS NULL OR c.grade_name = '' THEN '' ELSE ' ' END, c.name)
				         AS classroom_name,
				       dt.target_status,
				       DATE_FORMAT(dt.scheduled_at, '%Y-%m-%d %H:%i') AS scheduled_at,
				       DATE_FORMAT(dt.scheduled_at, '%Y-%m-%dT%H:%i') AS scheduled_input,
				       DATE_FORMAT(dt.distributed_at, '%Y-%m-%d %H:%i') AS distributed_at,
				       dt.execution_result
				FROM distribution_targets dt
				JOIN classrooms c ON c.classroom_id = dt.classroom_id
				JOIN schools s ON s.school_id = c.school_id
				WHERE dt.distribution_id = ?
				ORDER BY c.grade_name, c.name, dt.distribution_target_id
				""")) {
			statement.setLong(1, distributionId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					targets.add(new TargetSummary(rows.getLong("distribution_target_id"),
							rows.getLong("classroom_id"), rows.getString("school_name"),
							rows.getString("classroom_name"),
							rows.getString("target_status"), rows.getString("scheduled_at"),
							rows.getString("scheduled_input"),
							rows.getString("distributed_at"), rows.getString("execution_result")));
				}
			}
		}
		return List.copyOf(targets);
	}

	private List<HistorySummary> findHistorySummaries(Connection connection, long distributionId)
			throws SQLException {
		List<HistorySummary> history = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT h.action_type, u.user_id AS actor_user_id, u.login_id,
				       DATE_FORMAT(h.occurred_at, '%Y-%m-%d %H:%i:%s') AS occurred_at,
				       h.execution_status, h.change_detail
				FROM distribution_histories h
				JOIN users u ON u.user_id = h.actor_user_id
				WHERE h.distribution_id = ?
				ORDER BY h.occurred_at DESC, h.distribution_history_id DESC
				LIMIT 100
				""")) {
			statement.setLong(1, distributionId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					history.add(new HistorySummary(rows.getString("action_type"),
							rows.getLong("actor_user_id"), rows.getString("login_id"),
							rows.getString("occurred_at"), rows.getString("execution_status"),
							rows.getString("change_detail")));
				}
			}
		}
		return List.copyOf(history);
	}

	private long findSchoolForClass(Connection connection, long classroomId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
				"SELECT school_id FROM classrooms WHERE classroom_id = ? FOR UPDATE")) {
			statement.setLong(1, classroomId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) throw new SecurityException("Class access is not enabled.");
				return rows.getLong(1);
			}
		}
	}

	private boolean hasPreviousDelivery(Connection connection, long templateId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT 1
				FROM distributions d
				JOIN distribution_targets dt ON dt.distribution_id = d.distribution_id
				WHERE d.distribution_template_id = ? AND dt.target_status = 'distributed'
				LIMIT 1
				""")) {
			statement.setLong(1, templateId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next();
			}
		}
	}

	private long findLatestDistribution(Connection connection, long teacherId, long templateId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT distribution_id FROM distributions
				WHERE distribution_template_id = ? AND executed_by_user_id = ?
				ORDER BY distribution_id DESC LIMIT 1
				""")) {
			statement.setLong(1, templateId);
			statement.setLong(2, teacherId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next() ? rows.getLong(1) : 0;
			}
		}
	}

	private void requireOwnedDistribution(Connection connection, long teacherId, long distributionId, boolean lock)
			throws SQLException {
		String sql = """
				SELECT distribution_id FROM distributions
				WHERE distribution_id = ? AND executed_by_user_id = ?
				""" + (lock ? " FOR UPDATE" : "");
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, distributionId);
			statement.setLong(2, teacherId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) throw new IllegalArgumentException("配信履歴が見つかりません。");
			}
		}
	}

	private record TargetRow(long classroomId, long schoolId, String status) {}

	private List<TargetRow> findTargets(Connection connection, long distributionId, boolean lock)
			throws SQLException {
		String sql = """
				SELECT dt.classroom_id, c.school_id, dt.target_status
				FROM distribution_targets dt
				JOIN classrooms c ON c.classroom_id = dt.classroom_id
				WHERE dt.distribution_id = ?
				ORDER BY dt.distribution_target_id
				""" + (lock ? " FOR UPDATE" : "");
		List<TargetRow> targets = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, distributionId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					targets.add(new TargetRow(rows.getLong("classroom_id"), rows.getLong("school_id"),
							rows.getString("target_status")));
				}
			}
		}
		return List.copyOf(targets);
	}

	private record DeliveryTarget(long targetId, long distributionId, long teacherId, long templateId,
			long classroomId, long schoolId, String status, String distributionStatus, LocalDateTime scheduledAt,
			String templateName, String rootName) {}

	private DeliveryTarget lockDeliveryTarget(Connection connection, long targetId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT dt.distribution_target_id, dt.distribution_id, dt.classroom_id, dt.target_status,
				       dt.scheduled_at, c.school_id, d.executed_by_user_id, d.distribution_template_id,
				       d.distribution_status, d.template_name_snapshot, d.root_path_snapshot
				FROM distribution_targets dt
				JOIN distributions d ON d.distribution_id = dt.distribution_id
				JOIN classrooms c ON c.classroom_id = dt.classroom_id
				WHERE dt.distribution_target_id = ?
				FOR UPDATE
				""")) {
			statement.setLong(1, targetId);
			try (ResultSet rows = statement.executeQuery()) {
				if (!rows.next()) return null;
				java.sql.Timestamp time = rows.getTimestamp("scheduled_at");
				return new DeliveryTarget(rows.getLong("distribution_target_id"),
						rows.getLong("distribution_id"), rows.getLong("executed_by_user_id"),
						rows.getLong("distribution_template_id"), rows.getLong("classroom_id"),
						rows.getLong("school_id"), rows.getString("target_status"),
						rows.getString("distribution_status"), time == null ? null : time.toLocalDateTime(),
						rows.getString("template_name_snapshot"), rows.getString("root_path_snapshot"));
			}
		}
	}

	private static boolean isDue(DeliveryTarget target) {
		return target.scheduledAt() == null || !target.scheduledAt().isAfter(LocalDateTime.now());
	}

	private List<Long> findActiveStudents(Connection connection, long classroomId) throws SQLException {
		List<Long> students = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT u.user_id
				FROM users u
				WHERE u.user_type = 'student' AND u.account_status = 'active' AND u.deleted_at IS NULL
				  AND EXISTS (
				    SELECT 1 FROM student_class_memberships m
				    WHERE m.student_user_id = u.user_id AND m.classroom_id = ?
				      AND m.membership_status = 'active')
				ORDER BY u.user_id
				FOR UPDATE
				""")) {
			statement.setLong(1, classroomId);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) students.add(rows.getLong(1));
			}
		}
		return List.copyOf(students);
	}

	private boolean hasDeliveredTarget(Connection connection, long studentId, long targetId) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT student_exercise_id FROM student_exercises
				WHERE student_user_id = ? AND distribution_target_id = ?
				FOR UPDATE
				""")) {
			statement.setLong(1, studentId);
			statement.setLong(2, targetId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next();
			}
		}
	}

	private boolean hasDeliveredDistribution(Connection connection, long studentId, long distributionId)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT 1
				FROM student_exercises se
				JOIN distribution_targets dt ON dt.distribution_target_id = se.distribution_target_id
				WHERE se.student_user_id = ? AND dt.distribution_id = ?
				LIMIT 1
				""")) {
			statement.setLong(1, studentId);
			statement.setLong(2, distributionId);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next();
			}
		}
	}

	private String deliveryRootName(Connection connection, long studentId, DeliveryTarget target)
			throws SQLException {
		int priorDeliveries = 0;
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT COUNT(DISTINCT old_d.distribution_id)
				FROM student_exercises se
				JOIN distribution_targets old_dt ON old_dt.distribution_target_id = se.distribution_target_id
				JOIN distributions old_d ON old_d.distribution_id = old_dt.distribution_id
				WHERE se.student_user_id = ? AND old_d.distribution_template_id = ?
				  AND old_dt.classroom_id = ? AND old_dt.target_status = 'distributed'
				""")) {
			statement.setLong(1, studentId);
			statement.setLong(2, target.templateId());
			statement.setLong(3, target.classroomId());
			try (ResultSet rows = statement.executeQuery()) {
				if (rows.next()) priorDeliveries = rows.getInt(1);
			}
		}
		for (int attempt = priorDeliveries; attempt < 100_000; attempt++) {
			String candidate = attempt == 0 ? target.rootName()
					: target.rootName() + "（再配信" + (attempt == 1 ? "" : attempt) + "）";
			if (candidate.codePointCount(0, candidate.length()) > 255) {
				throw new IllegalArgumentException("再配信用フォルダ名が長すぎます。テンプレートのルート名を短くしてください。");
			}
			if (!rootNameExists(connection, studentId, candidate)) return candidate;
		}
		throw new SQLException("A unique delivery folder name could not be allocated.");
	}

	private boolean rootNameExists(Connection connection, long studentId, String name) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT 1
				FROM student_exercises se
				JOIN student_exercise_entries e ON e.student_exercise_id = se.student_exercise_id
				WHERE se.student_user_id = ? AND se.deleted_at IS NULL AND se.exercise_status <> 'archived'
				  AND e.parent_entry_id IS NULL AND e.entry_type = 'folder'
				  AND e.entry_status = 'active' AND e.trash_root_entry_id IS NULL AND e.name = ?
				LIMIT 1
				""")) {
			statement.setLong(1, studentId);
			statement.setString(2, name);
			try (ResultSet rows = statement.executeQuery()) {
				return rows.next();
			}
		}
	}

	private void insertStudentExercise(Connection connection, long studentId, long targetId,
			String templateName, String rootName, List<TeacherDistributionInput.TemplateItem> items)
			throws SQLException {
		long exerciseId;
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO student_exercises
				  (student_user_id, distribution_target_id, exercise_origin, scope_name,
				   exercise_status, save_status, created_at)
				VALUES (?, ?, 'distribution', ?, 'not_started', 'saved', CURRENT_TIMESTAMP)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, studentId);
			statement.setLong(2, targetId);
			statement.setString(3, rootName.isBlank() ? templateName : rootName);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) throw new SQLException("Student exercise ID was not generated.");
				exerciseId = keys.getLong(1);
			}
		}
		Map<String, Long> folders = new HashMap<>();
		long rootEntryId = insertStudentEntry(connection, exerciseId, null, rootName, rootName,
				StudentExerciseEntry.Type.FOLDER, null);
		folders.put(rootName.toLowerCase(Locale.ROOT), rootEntryId);
		for (var item : items) {
			String[] segments = item.path().split("/");
			String parentPath = rootName;
			Long parentId = rootEntryId;
			for (int index = 0; index < segments.length; index++) {
				String segment = segments[index];
				boolean file = index == segments.length - 1
						&& item.type() == StudentExerciseEntry.Type.FILE;
				String path = parentPath + "/" + segment;
				if (file) {
					if (path.codePointCount(0, path.length()) > 1000) {
						throw new IllegalArgumentException("配信後のファイルパスは1000文字以内にしてください。");
					}
					insertStudentEntry(connection, exerciseId, parentId, segment, path,
							StudentExerciseEntry.Type.FILE, item.content());
				} else {
					String key = path.toLowerCase(Locale.ROOT);
					Long existing = folders.get(key);
					if (existing == null) {
						if (path.codePointCount(0, path.length()) > 1000) {
							throw new IllegalArgumentException("配信後のフォルダパスは1000文字以内にしてください。");
						}
						existing = insertStudentEntry(connection, exerciseId, parentId, segment, path,
								StudentExerciseEntry.Type.FOLDER, null);
						folders.put(key, existing);
					}
					parentId = existing;
				}
				parentPath = path;
			}
		}
	}

	private long insertStudentEntry(Connection connection, long exerciseId, Long parentId,
			String name, String path, StudentExerciseEntry.Type type, String content) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO student_exercise_entries
				  (student_exercise_id, parent_entry_id, entry_type, name, path, language,
				   current_content, entry_status, created_at, source_exercise_id)
				VALUES (?, ?, ?, ?, ?, ?, ?, 'active', CURRENT_TIMESTAMP, ?)
				""", Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, exerciseId);
			statement.setObject(2, parentId);
			statement.setString(3, type.getValue());
			statement.setString(4, name);
			statement.setString(5, path);
			statement.setString(6, type == StudentExerciseEntry.Type.FILE ? "python" : null);
			statement.setString(7, content);
			statement.setLong(8, exerciseId);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) throw new SQLException("Student exercise entry ID was not generated.");
				return keys.getLong(1);
			}
		}
	}

	private void refreshDistributionStatus(Connection connection, long distributionId) throws SQLException {
		int total = 0;
		int distributed = 0;
		int scheduled = 0;
		int pending = 0;
		int stopped = 0;
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT COUNT(*) AS total_count,
				       SUM(target_status = 'distributed') AS distributed_count,
				       SUM(target_status = 'scheduled') AS scheduled_count,
				       SUM(target_status = 'not_distributed') AS pending_count,
				       SUM(target_status = 'stopped') AS stopped_count
				FROM distribution_targets WHERE distribution_id = ?
				""")) {
			statement.setLong(1, distributionId);
			try (ResultSet rows = statement.executeQuery()) {
				if (rows.next()) {
					total = rows.getInt("total_count");
					distributed = rows.getInt("distributed_count");
					scheduled = rows.getInt("scheduled_count");
					pending = rows.getInt("pending_count");
					stopped = rows.getInt("stopped_count");
				}
			}
		}
		String status;
		boolean complete = total > 0 && distributed == total;
		if (complete) status = "completed";
		else if (total == 0) status = "draft";
		else if (scheduled == 0 && pending == 0 && stopped > 0) status = "stopped";
		else if (distributed > 0) status = "in_progress";
		else if (scheduled > 0) status = "scheduled";
		else status = "in_progress";
		try (PreparedStatement statement = connection.prepareStatement("""
				UPDATE distributions
				SET distribution_status = ?,
				    completed_at = CASE WHEN ? THEN COALESCE(completed_at, CURRENT_TIMESTAMP) ELSE NULL END
				WHERE distribution_id = ?
				""")) {
			statement.setString(1, status);
			statement.setBoolean(2, complete);
			statement.setLong(3, distributionId);
			statement.executeUpdate();
		}
	}

	private void recordHistory(Connection connection, long distributionId, long actorId,
			String action, Object detail, boolean success) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				INSERT INTO distribution_histories
				  (distribution_id, actor_user_id, action_type, change_detail, execution_status, occurred_at)
				VALUES (?, ?, ?, CAST(? AS JSON), ?, CURRENT_TIMESTAMP)
				""")) {
			statement.setLong(1, distributionId);
			statement.setLong(2, actorId);
			statement.setString(3, action);
			statement.setString(4, JSON.toJson(detail));
			statement.setString(5, success ? "success" : "failure");
			statement.executeUpdate();
		}
	}

	private static String failureResult(String message) {
		return JSON.toJson(Map.of("status", "failure", "message", safeMessage(message)));
	}

	private static String safeMessage(String message) {
		if (message == null || message.isBlank()) return "配信処理に失敗しました。";
		return message.length() > 500 ? message.substring(0, 500) : message;
	}

	private static void rollback(Connection connection, Throwable failure) {
		try {
			connection.rollback();
		} catch (SQLException rollbackFailure) {
			failure.addSuppressed(rollbackFailure);
			LOGGER.log(Level.SEVERE, "Failed to rollback teacher distribution transaction.", rollbackFailure);
		}
	}
}
