package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.ProcessingException;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SqliteRangeService {

	private static final int MAX_ROWS = 100;
	private static final int MAX_COLUMNS = 64;
	private static final int MAX_CELL_CHARS = 32_768;
	private static final int MAX_OFFSET = 100_000;
	private static final int QUERY_TIMEOUT_SECONDS = 3;
	private final ProcessingResourceBudgetService budgets;

	public SqliteRangeService(ProcessingResourceBudgetService budgets) {
		this.budgets = budgets;
	}

	public DatabaseRange readRange(StoredUpload upload, String tableName, String cursor, Integer requestedOffset, int requestedLimit) {
		if (!"database".equals(upload.parserRoute())) {
			throw new ProcessingException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FORMAT", "Database range доступен только для SQLite upload.");
		}
		var revision = upload.sha256().substring(0, 16) + ":sqlite:" + tableName;
		var offset = cursor == null || cursor.isBlank()
			? Math.max(0, requestedOffset == null ? 0 : requestedOffset)
			: decodeCursor(cursor, revision);
		if (offset > MAX_OFFSET) {
			throw new ProcessingException(HttpStatus.PAYLOAD_TOO_LARGE, "RESOURCE_LIMIT_EXCEEDED", "SQLite offset превышает допустимый budget.");
		}
		var limit = Math.max(1, Math.min(MAX_ROWS, requestedLimit));

		try (var connection = DriverManager.getConnection(readOnlyUrl(upload))) {
			try (var statement = connection.createStatement()) {
				statement.execute("PRAGMA query_only = ON");
				statement.execute("PRAGMA trusted_schema = OFF");
				statement.execute("PRAGMA busy_timeout = 1000");
			}
			var columns = readColumns(connection, tableName);
			if (columns.isEmpty()) {
				throw new ProcessingException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "SQLite table не найдена.");
			}
			if (columns.size() > MAX_COLUMNS) {
				throw new ProcessingException(HttpStatus.PAYLOAD_TOO_LARGE, "RESOURCE_LIMIT_EXCEEDED", "SQLite table превышает column budget.");
			}
			this.budgets.verifyTableShape(limit + 1L, columns.size());
			var query = "SELECT " + columns.stream().map(this::safeProjection).collect(java.util.stream.Collectors.joining(", "))
				+ " FROM " + quote(tableName) + " LIMIT ? OFFSET ?";
			var rows = new ArrayList<List<String>>();
			try (var statement = connection.prepareStatement(query)) {
				statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
				statement.setMaxRows(limit + 1);
				statement.setInt(1, limit + 1);
				statement.setInt(2, offset);
				try (var result = statement.executeQuery()) {
					while (result.next() && rows.size() <= limit) {
						var row = new ArrayList<String>();
						for (int column = 1; column <= columns.size(); column += 1) {
							var value = result.getString(column);
							row.add(value == null ? "" : bounded(value));
						}
						rows.add(List.copyOf(row));
					}
				}
			}
			var hasMore = rows.size() > limit;
			var visibleRows = hasMore ? rows.subList(0, limit) : rows;
			return new DatabaseRange(
				revision,
				tableName,
				columns,
				List.copyOf(visibleRows),
				offset,
				hasMore ? encodeCursor(revision, offset + visibleRows.size()) : null,
				hasMore,
				List.of("SQLite открыт query_only/read-only; BLOB возвращается только как размер, mutation и ATTACH API отсутствуют.")
			);
		}
		catch (ProcessingException exception) {
			throw exception;
		}
		catch (SQLException exception) {
			throw new ProcessingException(HttpStatus.UNPROCESSABLE_ENTITY, "CORRUPT_FILE", "SQLite range не удалось прочитать.", exception);
		}
	}

	private List<String> readColumns(java.sql.Connection connection, String tableName) throws SQLException {
		try (var statement = connection.prepareStatement(
			"SELECT name FROM pragma_table_info(?) ORDER BY cid LIMIT " + (MAX_COLUMNS + 1)
		)) {
			statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
			statement.setString(1, tableName);
			try (var result = statement.executeQuery()) {
				var columns = new ArrayList<String>();
				while (result.next()) {
					columns.add(result.getString(1));
				}
				return List.copyOf(columns);
			}
		}
	}

	private String safeProjection(String column) {
		var quoted = quote(column);
		return "CASE WHEN typeof(" + quoted + ") = 'blob' THEN '<BLOB ' || length(" + quoted
			+ ") || ' bytes>' ELSE substr(CAST(" + quoted + " AS TEXT), 1, " + MAX_CELL_CHARS + ") END AS " + quoted;
	}

	private String quote(String identifier) {
		return '"' + identifier.replace("\"", "\"\"") + '"';
	}

	private String readOnlyUrl(StoredUpload upload) {
		return "jdbc:sqlite:" + upload.storagePath().toAbsolutePath().toUri() + "?mode=ro";
	}

	private String bounded(String value) {
		return value.length() <= MAX_CELL_CHARS ? value : value.substring(0, MAX_CELL_CHARS);
	}

	private String encodeCursor(String revision, int offset) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(
			(revision + ":" + offset).getBytes(java.nio.charset.StandardCharsets.UTF_8)
		);
	}

	private int decodeCursor(String cursor, String revision) {
		try {
			var decoded = new String(Base64.getUrlDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8);
			var separator = decoded.lastIndexOf(':');
			if (separator < 0 || !decoded.substring(0, separator).equals(revision)) {
				throw new IllegalArgumentException();
			}
			return Integer.parseInt(decoded.substring(separator + 1));
		}
		catch (RuntimeException exception) {
			throw new ProcessingException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "SQLite cursor не соответствует revision.");
		}
	}

	public record DatabaseRange(
		String revision,
		String tableName,
		List<String> columns,
		List<List<String>> rows,
		int rowOffset,
		String nextCursor,
		boolean truncated,
		List<String> warnings
	) {
	}
}
