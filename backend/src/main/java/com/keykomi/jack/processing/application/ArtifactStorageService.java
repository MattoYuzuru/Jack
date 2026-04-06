package com.keykomi.jack.processing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keykomi.jack.processing.config.ProcessingProperties;
import com.keykomi.jack.processing.domain.StoredArtifact;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ArtifactStorageService {

	private final ProcessingProperties processingProperties;
	private final ObjectMapper objectMapper;

	public ArtifactStorageService(ProcessingProperties processingProperties, ObjectMapper objectMapper) throws IOException {
		this.processingProperties = processingProperties;
		this.objectMapper = objectMapper;
		Files.createDirectories(processingProperties.artifactsDirectory());
	}

	public StoredArtifact storeJsonArtifact(UUID jobId, String kind, String fileName, Object payload) {
		var artifactId = UUID.randomUUID();
		var artifactDirectory = this.processingProperties.artifactsDirectory().resolve(jobId.toString());
		var storagePath = artifactDirectory.resolve(artifactId + "-" + fileName);

		try {
			Files.createDirectories(artifactDirectory);
			this.objectMapper.writerWithDefaultPrettyPrinter().writeValue(storagePath.toFile(), payload);

			return new StoredArtifact(
				artifactId,
				jobId,
				kind,
				fileName,
				"application/json",
				Files.size(storagePath),
				Instant.now(),
				storagePath
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить job artifact.", exception);
		}
	}

	public StoredArtifact storeFileArtifact(UUID jobId, String kind, String fileName, String mediaType, java.nio.file.Path sourcePath) {
		var artifactId = UUID.randomUUID();
		var artifactDirectory = this.processingProperties.artifactsDirectory().resolve(jobId.toString());
		var storagePath = artifactDirectory.resolve(artifactId + "-" + fileName);

		try {
			Files.createDirectories(artifactDirectory);
			Files.copy(sourcePath, storagePath);

			return new StoredArtifact(
				artifactId,
				jobId,
				kind,
				fileName,
				mediaType,
				Files.size(storagePath),
				Instant.now(),
				storagePath
			);
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

			return new StoredArtifact(
				artifactId,
				jobId,
				kind,
				fileName,
				mediaType,
				Files.size(storagePath),
				Instant.now(),
				storagePath
			);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить in-memory artifact.", exception);
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
