package lib.mysql;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class Client {
	private static final Pattern ENVIRONMENT_PLACEHOLDER = Pattern.compile("\\$\\{([A-Z0-9_]+)}");
	private static final Logger LOGGER = Logger.getLogger(Client.class.getName());

	public static Connection createConnection() throws SQLException {
		return DataSourceHolder.DATA_SOURCE.getConnection();
	}

	public static void initialize() {
		int attempt = 0;
		while (true) {
			try (Connection connection = DataSourceHolder.DATA_SOURCE.getConnection()) {
				if (!connection.isValid(1)) {
					throw new SQLException("MySQL connection validation failed.");
				}
				return;
			} catch (SQLException failure) {
				if (!isTransientConnectionFailure(failure)) {
					throw new IllegalStateException("Unable to initialize the MySQL connection pool.", failure);
				}
				attempt++;
				LOGGER.log(Level.WARNING,
						"MySQL is not ready; application initialization will retry (attempt {0}).", attempt);
				try {
					Thread.sleep(Math.min(attempt * 1000L, 10_000L));
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Interrupted while waiting for MySQL initialization.", interrupted);
				}
			}
		}
	}

	static boolean isTransientConnectionFailure(SQLException failure) {
		for (SQLException current = failure; current != null; current = current.getNextException()) {
			String state = current.getSQLState();
			if (state != null && state.startsWith("08")) {
				return true;
			}
			for (Throwable cause = current.getCause(); cause != null; cause = cause.getCause()) {
				if (cause instanceof SQLException sqlFailure) {
					state = sqlFailure.getSQLState();
					if (state != null && state.startsWith("08")) {
						return true;
					}
				}
			}
		}
		return false;
	}

	public static void closeDataSource() {
		DataSourceHolder.DATA_SOURCE.close();
	}

	private static HikariDataSource createDataSource() {
		Properties properties = new Properties();
		try (InputStream input = Client.class.getClassLoader().getResourceAsStream("dataSource.properties")) {
			if (input == null) {
				throw new IllegalStateException("Required classpath resource dataSource.properties was not found.");
			}
			properties.load(input);
		} catch (IOException e) {
			throw new IllegalStateException("Unable to read dataSource.properties.", e);
		}

		Properties resolved = new Properties();
		for (String key : properties.stringPropertyNames()) {
			resolved.setProperty(key, resolveEnvironmentPlaceholders(key, properties.getProperty(key)));
		}
		return new HikariDataSource(new HikariConfig(resolved));
	}

	private static String resolveEnvironmentPlaceholders(String propertyName, String value) {
		Matcher matcher = ENVIRONMENT_PLACEHOLDER.matcher(value);
		StringBuffer resolved = new StringBuffer();
		while (matcher.find()) {
			String name = matcher.group(1);
			String environmentValue = System.getenv(name);
			if (environmentValue == null || environmentValue.isBlank()) {
				throw new IllegalStateException(
						"Required environment variable for data source property '" + propertyName + "' is not set.");
			}
			matcher.appendReplacement(resolved, Matcher.quoteReplacement(environmentValue));
		}
		matcher.appendTail(resolved);
		return resolved.toString();
	}

	private static final class DataSourceHolder {
		private static final HikariDataSource DATA_SOURCE = createDataSource();
	}
}
