package com.keykomi.jack.processing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keykomi.jack.processing.config.ProcessingProperties;
import com.keykomi.jack.processing.domain.StoredArtifact;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
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

}
