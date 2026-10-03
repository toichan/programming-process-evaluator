package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import entity.EditorPreferences;
import entity.EditorPreferences.Theme;
import lib.mysql.Client;

public final class StudentEditorPreferencesDao {
	public EditorPreferences findByUserId(long userId) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						SELECT font_size_px, line_wrapping, indent_width, editor_theme
						FROM user_editor_preferences
						WHERE user_id = ?
						""")) {
			statement.setLong(1, userId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return EditorPreferences.defaults();
				}
				return new EditorPreferences(
						resultSet.getInt("font_size_px"),
						resultSet.getBoolean("line_wrapping"),
						resultSet.getInt("indent_width"),
						Theme.fromValue(resultSet.getString("editor_theme")));
			}
		}
	}

	public void save(long userId, EditorPreferences preferences) throws SQLException {
		try (Connection connection = Client.createConnection();
				PreparedStatement statement = connection.prepareStatement("""
						INSERT INTO user_editor_preferences (
						    user_id, font_size_px, line_wrapping, indent_width, editor_theme, updated_at
						) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6))
						ON DUPLICATE KEY UPDATE
						    font_size_px = ?,
						    line_wrapping = ?,
						    indent_width = ?,
						    editor_theme = ?,
						    updated_at = CURRENT_TIMESTAMP(6)
						""")) {
			statement.setLong(1, userId);
			statement.setInt(2, preferences.fontSizePx());
			statement.setBoolean(3, preferences.lineWrapping());
			statement.setInt(4, preferences.indentWidth());
			statement.setString(5, preferences.theme().value());
			statement.setInt(6, preferences.fontSizePx());
			statement.setBoolean(7, preferences.lineWrapping());
			statement.setInt(8, preferences.indentWidth());
			statement.setString(9, preferences.theme().value());
			statement.executeUpdate();
		}
	}
}
