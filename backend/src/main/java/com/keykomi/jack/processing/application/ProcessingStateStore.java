package com.keykomi.jack.processing.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keykomi.jack.processing.domain.ProcessingJobStatus;
import com.keykomi.jack.processing.domain.ProcessingJobType;
import com.keykomi.jack.processing.domain.StoredArtifact;
import com.keykomi.jack.processing.domain.StoredProcessingJob;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProcessingStateStore {

	private static final TypeReference<Map<String, Object>> PARAMETERS_TYPE = new TypeReference<>() {};
	private final JdbcTemplate jdbcTemplate;
	private final ObjectMapper objectMapper;

	public ProcessingStateStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
		this.jdbcTemplate = jdbcTemplate;
		this.objectMapper = objectMapper;
	}

	public void saveUpload(UUID ownerId, StoredUpload upload) {
		this.jdbcTemplate.update(
			"""
			INSERT INTO processing_uploads (
			  id, owner_id, original_file_name, media_type, extension, size_bytes, sha256,
			  storage_path, created_at, expires_at, policy_version
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			upload.id(), ownerId, upload.originalFileName(), upload.mediaType(), upload.extension(),
			upload.sizeBytes(), upload.sha256(), upload.storagePath().toString(),
			atOffset(upload.createdAt()), atOffset(upload.expiresAt()), upload.policyVersion()
		);
	}

	public Optional<StoredUpload> findUpload(UUID uploadId) {
		return queryOptional(
			"SELECT * FROM processing_uploads WHERE id = ?",
			(resultSet, rowNumber) -> mapUpload(resultSet),
			uploadId
		);
	}

	public Optional<StoredUpload> findOwnedUpload(UUID uploadId, UUID ownerId, Instant now) {
		return queryOptional(
			"SELECT * FROM processing_uploads WHERE id = ? AND owner_id = ? AND expires_at > ?",
			(resultSet, rowNumber) -> mapUpload(resultSet),
			uploadId,
			ownerId,
			atOffset(now)
		);
	}

	public Optional<StoredUpload> findUploadOwnedByJobOwner(UUID uploadId, UUID jobId, Instant now) {
		return queryOptional(
			"""
			SELECT upload.* FROM processing_uploads upload
			JOIN processing_jobs job ON job.owner_id = upload.owner_id
			WHERE upload.id = ? AND job.id = ? AND upload.expires_at > ?
			""",
			(resultSet, rowNumber) -> mapUpload(resultSet),
			uploadId,
			jobId,
			atOffset(now)
		);
	}

	public long ownerStorageBytes(UUID ownerId, Instant now) {
		var value = this.jdbcTemplate.queryForObject(
			"SELECT COALESCE(SUM(size_bytes), 0) FROM processing_uploads WHERE owner_id = ? AND expires_at > ?",
			Long.class,
			ownerId,
			atOffset(now)
		);
		return value == null ? 0L : value;
	}

	public void createJob(UUID ownerId, StoredProcessingJob job) {
		this.jdbcTemplate.update(
			"""
			INSERT INTO processing_jobs (
			  id, owner_id, upload_id, job_type, parameters_json, status, progress_percent,
			  message, error_code, error_message, correlation_id, created_at, started_at,
			  completed_at, expires_at, policy_version, version
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
			""",
			job.id(), ownerId, job.uploadId(), job.type().name(), writeParameters(job.parameters()),
			job.status().name(), job.progressPercent(), job.message(), job.errorCode(), job.errorMessage(),
			job.correlationId(), atOffset(job.createdAt()), nullableOffset(job.startedAt()),
			nullableOffset(job.completedAt()), atOffset(job.expiresAt()), job.policyVersion()
		);
	}

	public Optional<StoredProcessingJob> findJob(UUID jobId) {
		return queryOptional(
			"SELECT * FROM processing_jobs WHERE id = ?",
			(resultSet, rowNumber) -> mapJob(resultSet),
			jobId
		);
	}

	public Optional<StoredProcessingJob> findOwnedJob(UUID jobId, UUID ownerId, Instant now) {
		return queryOptional(
			"SELECT * FROM processing_jobs WHERE id = ? AND owner_id = ? AND expires_at > ?",
			(resultSet, rowNumber) -> mapJob(resultSet),
			jobId,
			ownerId,
			atOffset(now)
		);
	}

	@Transactional
	public StoredProcessingJob updateJob(UUID jobId, UnaryOperator<StoredProcessingJob> mutation) {
		var current = queryOptional(
			"SELECT * FROM processing_jobs WHERE id = ? FOR UPDATE",
			(resultSet, rowNumber) -> mapJob(resultSet),
			jobId
		).orElse(null);
		if (current == null) {
			return null;
		}

		var updated = mutation.apply(current);
		if (updated == null) {
			return current;
		}

		this.jdbcTemplate.update(
			"""
			UPDATE processing_jobs SET status = ?, progress_percent = ?, message = ?, error_code = ?,
			 error_message = ?, started_at = ?, completed_at = ?, expires_at = ?, version = version + 1
			WHERE id = ?
			""",
			updated.status().name(), updated.progressPercent(), updated.message(), updated.errorCode(),
			updated.errorMessage(), nullableOffset(updated.startedAt()), nullableOffset(updated.completedAt()),
			atOffset(updated.expiresAt()), jobId
		);
		return updated;
	}

	public void saveArtifact(UUID jobId, StoredArtifact artifact) {
		var ownerId = this.jdbcTemplate.queryForObject(
			"SELECT owner_id FROM processing_jobs WHERE id = ?",
			UUID.class,
			jobId
		);
		if (ownerId == null) {
			throw new IllegalStateException("Нельзя зарегистрировать artifact без durable job.");
		}

		this.jdbcTemplate.update(
			"""
			INSERT INTO processing_artifacts (
			 id, owner_id, job_id, kind, file_name, media_type, size_bytes, sha256,
			 storage_path, created_at, expires_at
			) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""",
			artifact.id(), ownerId, jobId, artifact.kind(), artifact.fileName(), artifact.mediaType(),
			artifact.sizeBytes(), artifact.sha256(), artifact.storagePath().toString(),
			atOffset(artifact.createdAt()), atOffset(artifact.expiresAt())
		);
	}

	public Optional<StoredArtifact> findOwnedArtifact(UUID jobId, UUID artifactId, UUID ownerId, Instant now) {
		return queryOptional(
			"""
			SELECT * FROM processing_artifacts
			WHERE id = ? AND job_id = ? AND owner_id = ? AND expires_at > ?
			""",
			(resultSet, rowNumber) -> mapArtifact(resultSet),
			artifactId,
			jobId,
			ownerId,
			atOffset(now)
		);
	}

	public long countActiveJobs(UUID ownerId) {
		var value = this.jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM processing_jobs WHERE owner_id = ? AND status IN ('QUEUED', 'RUNNING')",
			Long.class,
			ownerId
		);
		return value == null ? 0L : value;
	}

	public long countJobsByStatus(ProcessingJobStatus status) {
		var value = this.jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM processing_jobs WHERE status = ?",
			Long.class,
			status.name()
		);
		return value == null ? 0L : value;
	}

	public long countAllJobs() {
		var value = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processing_jobs", Long.class);
		return value == null ? 0L : value;
	}

	public Set<UUID> listActiveJobIds() {
		return Set.copyOf(this.jdbcTemplate.queryForList(
			"SELECT id FROM processing_jobs WHERE status IN ('QUEUED', 'RUNNING')",
			UUID.class
		));
	}

	public Set<UUID> listActiveUploadIds() {
		return Set.copyOf(this.jdbcTemplate.queryForList(
			"SELECT DISTINCT upload_id FROM processing_jobs WHERE status IN ('QUEUED', 'RUNNING')",
			UUID.class
		));
	}

	public List<StoredUpload> listExpiredUploads(Instant cutoff) {
		return this.jdbcTemplate.query(
			"SELECT * FROM processing_uploads WHERE created_at < ?",
			(resultSet, rowNumber) -> mapUpload(resultSet),
			atOffset(cutoff)
		);
	}

	public void deleteUpload(UUID uploadId) {
		this.jdbcTemplate.update("DELETE FROM processing_uploads WHERE id = ?", uploadId);
	}

	public int purgeExpiredTerminalJobs(Instant cutoff) {
		return this.jdbcTemplate.update(
			"DELETE FROM processing_jobs WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED') AND COALESCE(completed_at, created_at) < ?",
			atOffset(cutoff)
		);
	}

	public void deleteJob(UUID jobId) {
		this.jdbcTemplate.update("DELETE FROM processing_jobs WHERE id = ?", jobId);
	}

	public int reconcileInterruptedJobs(Instant now) {
		return this.jdbcTemplate.update(
			"""
			UPDATE processing_jobs SET status = 'FAILED', progress_percent = 0,
			 message = 'Job прерван перезапуском сервиса.', error_code = 'STARTUP_INTERRUPTED',
			 error_message = 'Операцию нужно запустить повторно.', completed_at = ?, version = version + 1
			WHERE status IN ('QUEUED', 'RUNNING')
			""",
			atOffset(now)
		);
	}

	private StoredUpload mapUpload(ResultSet resultSet) throws SQLException {
		return new StoredUpload(
			resultSet.getObject("id", UUID.class),
			resultSet.getString("original_file_name"),
			resultSet.getString("media_type"),
			resultSet.getString("extension"),
			resultSet.getLong("size_bytes"),
			resultSet.getString("sha256"),
			readInstant(resultSet, "created_at"),
			readInstant(resultSet, "expires_at"),
			resultSet.getString("policy_version"),
			Path.of(resultSet.getString("storage_path"))
		);
	}

	private StoredProcessingJob mapJob(ResultSet resultSet) throws SQLException {
		var jobId = resultSet.getObject("id", UUID.class);
		return new StoredProcessingJob(
			jobId,
			resultSet.getObject("upload_id", UUID.class),
			ProcessingJobType.valueOf(resultSet.getString("job_type")),
			readParameters(resultSet.getString("parameters_json")),
			ProcessingJobStatus.valueOf(resultSet.getString("status")),
			resultSet.getInt("progress_percent"),
			resultSet.getString("message"),
			resultSet.getString("error_code"),
			resultSet.getString("error_message"),
			resultSet.getObject("correlation_id", UUID.class),
			readInstant(resultSet, "created_at"),
			readNullableInstant(resultSet, "started_at"),
			readNullableInstant(resultSet, "completed_at"),
			readInstant(resultSet, "expires_at"),
			resultSet.getString("policy_version"),
			this.jdbcTemplate.query(
				"SELECT * FROM processing_artifacts WHERE job_id = ? ORDER BY created_at, id",
				(artifactSet, rowNumber) -> mapArtifact(artifactSet),
				jobId
			)
		);
	}

	private StoredArtifact mapArtifact(ResultSet resultSet) throws SQLException {
		return new StoredArtifact(
			resultSet.getObject("id", UUID.class),
			resultSet.getObject("job_id", UUID.class),
			resultSet.getString("kind"),
			resultSet.getString("file_name"),
			resultSet.getString("media_type"),
			resultSet.getLong("size_bytes"),
			resultSet.getString("sha256"),
			readInstant(resultSet, "created_at"),
			readInstant(resultSet, "expires_at"),
			Path.of(resultSet.getString("storage_path"))
		);
	}

	private Map<String, Object> readParameters(String json) {
		try {
			return Map.copyOf(this.objectMapper.readValue(json, PARAMETERS_TYPE));
		}
		catch (Exception exception) {
			throw new IllegalStateException("Не удалось прочитать durable job parameters.", exception);
		}
	}

	private String writeParameters(Map<String, Object> parameters) {
		try {
			return this.objectMapper.writeValueAsString(parameters);
		}
		catch (Exception exception) {
			throw new IllegalStateException("Не удалось сериализовать durable job parameters.", exception);
		}
	}

	private <T> Optional<T> queryOptional(String sql, org.springframework.jdbc.core.RowMapper<T> mapper, Object... args) {
		return this.jdbcTemplate.query(sql, mapper, args).stream().findFirst();
	}

	private Instant readInstant(ResultSet resultSet, String column) throws SQLException {
		return resultSet.getObject(column, OffsetDateTime.class).toInstant();
	}

	private Instant readNullableInstant(ResultSet resultSet, String column) throws SQLException {
		var value = resultSet.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	private OffsetDateTime atOffset(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private OffsetDateTime nullableOffset(Instant instant) {
		return instant == null ? null : atOffset(instant);
	}
}
