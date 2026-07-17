package com.keykomi.jack.processing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keykomi.jack.processing.config.ProcessingProperties;
import com.keykomi.jack.processing.domain.StoredArtifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ArtifactStorageService {

	private final ProcessingProperties processingProperties;
	private final ObjectMapper objectMapper;
	private final ProcessingStateStore stateStore;
	private final ProcessingResourceBudgetService resourceBudgets;

	public ArtifactStorageService(
		ProcessingProperties processingProperties,
		ObjectMapper objectMapper,
		ProcessingStateStore stateStore,
		ProcessingResourceBudgetService resourceBudgets
	) throws IOException {
		this.processingProperties = processingProperties;
		this.objectMapper = objectMapper;
		this.stateStore = stateStore;
		this.resourceBudgets = resourceBudgets;
		Files.createDirectories(processingProperties.artifactsDirectory());
	}

	public StoredArtifact storeJsonArtifact(UUID jobId, String kind, String fileName, Object payload) {
		return storeJsonArtifact(jobId, kind, fileName, payload, true);
	}

	StoredArtifact storeTransientJsonArtifact(UUID jobId, String kind, String fileName, Object payload) {
		return storeJsonArtifact(jobId, kind, fileName, payload, false);
	}

	private StoredArtifact storeJsonArtifact(UUID jobId, String kind, String fileName, Object payload, boolean durable) {
		var artifactId = UUID.randomUUID();
		var artifactDirectory = this.processingProperties.artifactsDirectory().resolve(jobId.toString());
		var storagePath = artifactDirectory.resolve(artifactId + "-" + fileName);

		try {
			Files.createDirectories(artifactDirectory);
			this.objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), payload);

			return registerArtifact(jobId, artifactId, kind, fileName, "application/json", storagePath, durable);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить job artifact.", exception);
		}
	}

	public StoredArtifact storeFileArtifact(UUID jobId, String kind, String fileName, String mediaType, java.nio.file.Path sourcePath) {
		return storeFileArtifact(jobId, kind, fileName, mediaType, sourcePath, true);
	}

	StoredArtifact storeTransientFileArtifact(UUID jobId, String kind, String fileName, String mediaType, Path sourcePath) {
		return storeFileArtifact(jobId, kind, fileName, mediaType, sourcePath, false);
	}

	private StoredArtifact storeFileArtifact(
		UUID jobId,
		String kind,
		String fileName,
		String mediaType,
		Path sourcePath,
		boolean durable
	) {
		var artifactId = UUID.randomUUID();
		var artifactDirectory = this.processingProperties.artifactsDirectory().resolve(jobId.toString());
		var storagePath = artifactDirectory.resolve(artifactId + "-" + fileName);

		try {
			Files.createDirectories(artifactDirectory);
			Files.copy(sourcePath, storagePath);

			return registerArtifact(jobId, artifactId, kind, fileName, mediaType, storagePath, durable);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить binary artifact.", exception);
		}
	}

	public StoredArtifact storeBytesArtifact(UUID jobId, String kind, String fileName, String mediaType, byte[] bytes) {
		var artifactId = UUID.randomUUID();
		var artifactDirectory = this.processingProperties.artifactsDirectory().resolve(jobId.toString());
		var storagePath = artifactDirectory.resolve(artifactId + "-" + fileName);

		try {
			Files.createDirectories(artifactDirectory);
			Files.write(storagePath, bytes);

			return registerArtifact(jobId, artifactId, kind, fileName, mediaType, storagePath, true);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить in-memory artifact.", exception);
		}
	}

	private StoredArtifact registerArtifact(
		UUID jobId,
		UUID artifactId,
		String kind,
		String fileName,
		String mediaType,
		Path storagePath,
		boolean durable
	) throws IOException {
		var createdAt = Instant.now();
		var sizeBytes = Files.size(storagePath);
		try {
			this.resourceBudgets.verifyResultSize(sizeBytes);
		}
		catch (RuntimeException exception) {
			Files.deleteIfExists(storagePath);
			throw exception;
		}
		var artifact = new StoredArtifact(
			artifactId,
			jobId,
			kind,
			fileName,
			mediaType,
			sizeBytes,
			sha256(storagePath),
			createdAt,
			createdAt.plus(this.processingProperties.getArtifactRetentionHours(), ChronoUnit.HOURS),
			storagePath
		);
		if (durable) {
			this.stateStore.saveArtifact(jobId, artifact);
		}
		return artifact;
	}

	private String sha256(Path path) throws IOException {
		try {
			var digest = MessageDigest.getInstance("SHA-256");
			try (var input = new DigestInputStream(Files.newInputStream(path), digest)) {
				input.transferTo(java.io.OutputStream.nullOutputStream());
			}
			return HexFormat.of().formatHex(digest.digest());
		}
		catch (java.security.NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 должен быть доступен в стандартной JDK.", exception);
		}
	}

	public int purgeExpired(Instant cutoff, Set<UUID> retainedJobIds) {
		var retainedIds = retainedJobIds == null ? Set.<UUID>of() : Set.copyOf(retainedJobIds);
		var deletedDirectories = 0;

		try (var artifactDirectories = Files.list(this.processingProperties.artifactsDirectory())) {
			for (var iterator = artifactDirectories.iterator(); iterator.hasNext();) {
				var path = iterator.next();
				if (!Files.isDirectory(path) || !isExpired(path, cutoff)) {
					continue;
				}

				var jobId = extractJobId(path);
				if (jobId != null && retainedIds.contains(jobId)) {
					continue;
				}

				if (deleteRecursively(path)) {
					deletedDirectories++;
				}
			}
		}
		catch (IOException ignored) {
			// Очистка artifacts best-effort: при временной ошибке storage service продолжит работу.
		}

		return deletedDirectories;
	}

	private boolean isExpired(Path path, Instant cutoff) {
		try {
			return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
		}
		catch (IOException ignored) {
			return false;
		}
	}

	private UUID extractJobId(Path path) {
		try {
			return UUID.fromString(path.getFileName().toString());
		}
		catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private boolean deleteRecursively(Path path) {
		try (var walk = Files.walk(path)) {
			for (var iterator = walk.sorted(Comparator.reverseOrder()).iterator(); iterator.hasNext();) {
				Files.deleteIfExists(iterator.next());
			}
			return true;
		}
		catch (IOException ignored) {
			return false;
		}
	}

}
