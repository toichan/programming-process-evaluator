package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import entity.StandardRubric;
import entity.StandardRubric.Criterion;
import entity.StandardRubric.Dimension;
import entity.StandardRubric.Level;
import lib.mysql.Client;

public final class StandardRubricDao {
	public long requireActiveId(Connection connection) throws SQLException {
		try {
			if (find(connection).isEmpty()) {
				throw new SQLException("The active standard rubric is not registered.");
			}
		} catch (IllegalArgumentException invalidRubric) {
			throw new SQLException("The active standard rubric is invalid.", invalidRubric);
		}
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT rubric_id
				FROM rubrics
				WHERE title = ? AND version = ? AND rubric_status = 'active'
				""")) {
			statement.setString(1, StandardRubric.TITLE);
			statement.setString(2, StandardRubric.VERSION);
			try (ResultSet result = statement.executeQuery()) {
				if (!result.next()) {
					throw new SQLException("The validated standard rubric disappeared during lookup.");
				}
				long rubricId = result.getLong("rubric_id");
				if (result.next()) {
					throw new SQLException("Multiple active standard rubric rows were found.");
				}
				return rubricId;
			}
		}
	}

	public Optional<StandardRubric> find() throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				Optional<StandardRubric> result = find(connection);
				connection.commit();
				return result;
			} catch (SQLException | IllegalArgumentException e) {
				connection.rollback();
				throw e;
			}
		}
	}

	public Optional<StandardRubric> find(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT rubric_id FROM rubrics WHERE title = ? AND version = ? AND rubric_status = 'active'
				""")) {
			statement.setString(1, StandardRubric.TITLE);
			statement.setString(2, StandardRubric.VERSION);
			try (ResultSet result = statement.executeQuery()) {
				if (!result.next()) return Optional.empty();
				long id = result.getLong(1);
				List<Dimension> dimensions = new ArrayList<>();
				try (PreparedStatement query = connection.prepareStatement("""
						SELECT dimension_id, dimension_code, label FROM rubric_dimensions
						WHERE rubric_id = ? ORDER BY sort_order, dimension_id
						""")) {
					query.setLong(1, id);
					try (ResultSet rows = query.executeQuery()) {
						while (rows.next()) {
							dimensions.add(new Dimension(rows.getString("dimension_code"), rows.getString("label"),
									criteria(connection, id, rows.getLong("dimension_id"))));
						}
					}
				}
				return Optional.of(new StandardRubric(StandardRubric.TITLE, StandardRubric.VERSION, dimensions));
			}
		}
	}

	private List<Criterion> criteria(Connection connection, long rubricId, long dimensionId) throws SQLException {
		List<Criterion> criteria = new ArrayList<>();
		try (PreparedStatement statement = connection.prepareStatement("""
				SELECT criterion_id, name FROM rubric_criteria WHERE rubric_id = ? AND dimension_id = ?
				ORDER BY sort_order, criterion_id
				""")) {
			statement.setLong(1, rubricId);
			statement.setLong(2, dimensionId);
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					List<Level> levels = new ArrayList<>();
					try (PreparedStatement query = connection.prepareStatement("""
							SELECT level_value, level_label, level_description FROM criterion_levels
							WHERE criterion_id = ? ORDER BY level_value DESC
							""")) {
						query.setLong(1, result.getLong("criterion_id"));
						try (ResultSet rows = query.executeQuery()) {
							while (rows.next()) levels.add(new Level(rows.getInt(1), rows.getString(2), rows.getString(3)));
						}
					}
					criteria.add(new Criterion(result.getString("name"), levels));
				}
			}
		}
		return criteria;
	}

	public boolean register(StandardRubric rubric) throws SQLException {
		try (Connection connection = Client.createConnection()) {
			connection.setAutoCommit(false);
			try {
				boolean exists;
				try (PreparedStatement query = connection.prepareStatement(
						"SELECT rubric_id FROM rubrics WHERE title = ? AND version = ? FOR UPDATE")) {
					query.setString(1, rubric.title()); query.setString(2, rubric.version());
					try (ResultSet result = query.executeQuery()) { exists = result.next(); }
				}
				if (exists) {
					if (!find(connection).filter(rubric::equals).isPresent()) {
						throw new IllegalStateException("登録済み標準版の内容または状態が異なります。上書きしません。");
					}
					connection.commit();
					return false;
				}
				long rubricId = insert(connection, """
						INSERT INTO rubrics (created_by_user_id,title,version,rubric_status,created_at)
						VALUES (NULL,?,?,'active',CURRENT_TIMESTAMP)
						""", rubric.title(), rubric.version());
				for (int d = 0; d < rubric.dimensions().size(); d++) {
					Dimension dimension = rubric.dimensions().get(d);
					long dimensionId = insert(connection, """
							INSERT INTO rubric_dimensions (rubric_id,dimension_code,label,scale_minimum,
							  scale_maximum,scale_label,sort_order) VALUES (?,?,?,1,5,'5段階評価',?)
							""", rubricId, dimension.code(), dimension.label(), d);
					for (int c = 0; c < dimension.criteria().size(); c++) {
						Criterion criterion = dimension.criteria().get(c);
						long criterionId = insert(connection, """
								INSERT INTO rubric_criteria (rubric_id,dimension_id,criterion_code,name,weight,sort_order)
								VALUES (?,?,?,?,1,?)
								""", rubricId, dimensionId, dimension.code() + "-" + (c + 1), criterion.name(), c);
						for (Level level : criterion.levels()) {
							insert(connection, """
									INSERT INTO criterion_levels (criterion_id,level_value,level_label,level_description)
									VALUES (?,?,?,?)
									""", criterionId, level.value(), level.label(), level.description());
						}
					}
				}
				connection.commit();
				return true;
			} catch (SQLException | RuntimeException e) {
				connection.rollback();
				throw e;
			}
		}
	}

	private static long insert(Connection connection, String sql, Object... values) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
			statement.executeUpdate();
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next()) throw new SQLException("登録IDを取得できませんでした。");
				return keys.getLong(1);
			}
		}
	}
}
