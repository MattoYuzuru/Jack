package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.config.ProcessingProperties;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UploadStorageService {

	private final ProcessingProperties processingProperties;
	private final Map<UUID, StoredUpload> uploads = new ConcurrentHashMap<>();

	public UploadStorageService(ProcessingProperties processingProperties) throws IOException {
		this.processingProperties = processingProperties;
		Files.createDirectories(processingProperties.uploadsDirectory());
	}

	public StoredUpload store(MultipartFile file) {
		if (file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Пустой файл нельзя загрузить для обработки.");
		}

		if (file.getSize() > this.processingProperties.getMaxUploadSizeBytes()) {
			throw new ResponseStatusException(
				HttpStatus.PAYLOAD_TOO_LARGE,
				"Файл превышает допустимый размер загрузки для текущего сервиса."
			);
		}

		var uploadId = UUID.randomUUID();
		var originalFileName = sanitizeFileName(file.getOriginalFilename());
		var extension = detectExtension(originalFileName);
		var mediaType = StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
		var storagePath = this.processingProperties.uploadsDirectory().resolve(uploadId + "-" + originalFileName);
		var createdAt = Instant.now();

		try {
			var digest = MessageDigest.getInstance("SHA-256");
			try (
				InputStream inputStream = file.getInputStream();
				DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)
			) {
				// Хеш считаем в тот же проход, что и запись на диск, чтобы foundation
				// сразу умел строить dedup/cache-friendly идентификатор без второго чтения файла.
				Files.copy(digestInputStream, storagePath);
			}

			var storedUpload = new StoredUpload(
				uploadId,
				originalFileName,
				mediaType,
				extension,
				Files.size(storagePath),
				HexFormat.of().formatHex(digest.digest()),
				createdAt,
				storagePath
			);
			this.uploads.put(uploadId, storedUpload);
			return storedUpload;
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить upload во временное хранилище.", exception);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 должен быть доступен в стандартной JDK.", exception);
		}
	}

	public StoredUpload getRequiredUpload(UUID uploadId) {
		return findUpload(uploadId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Файл загрузки не найден."));
	}

	public Optional<StoredUpload> findUpload(UUID uploadId) {
		return Optional.ofNullable(this.uploads.get(uploadId));
	}

	public int purgeExpired(Instant cutoff, Set<UUID> retainedUploadIds) {
		var retainedIds = retainedUploadIds == null ? Set.<UUID>of() : Set.copyOf(retainedUploadIds);
		var deletedUploads = 0;

		for (var entry : this.uploads.entrySet()) {
			var upload = entry.getValue();
			if (retainedIds.contains(upload.id()) || !upload.createdAt().isBefore(cutoff)) {
				continue;
			}
			if (!deletePath(upload.storagePath())) {
				continue;
			}
			if (this.uploads.remove(entry.getKey(), upload)) {
				deletedUploads++;
			}
		}

		try (var storedFiles = Files.list(this.processingProperties.uploadsDirectory())) {
			for (var iterator = storedFiles.iterator(); iterator.hasNext();) {
				var path = iterator.next();
				if (!Files.isRegularFile(path) || !isExpired(path, cutoff)) {
					continue;
				}

				var uploadId = extractUploadId(path);
				if (uploadId != null && (retainedIds.contains(uploadId) || this.uploads.containsKey(uploadId))) {
					continue;
				}

				if (deletePath(path)) {
					deletedUploads++;
				}
			}
		}
		catch (IOException ignored) {
			// Очистка не должна срывать runtime: следующий TTL-проход попробует удалить хвосты повторно.
		}

		return deletedUploads;
	}

	private String sanitizeFileName(String originalFileName) {
		var candidate = StringUtils.hasText(originalFileName) ? originalFileName : "upload.bin";
		var normalized = candidate.replace("\\", "/");
		var slashIndex = normalized.lastIndexOf('/');
		var fileName = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
		return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
	}

	private String detectExtension(String fileName) {
		var dotIndex = fileName.lastIndexOf('.');
		if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
			return "";
		}

		return fileName.substring(dotIndex + 1).toLowerCase();
	}

	private boolean isExpired(Path path, Instant cutoff) {
		try {
			return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
		}
		catch (IOException ignored) {
			return false;
		}
	}

	private UUID extractUploadId(Path path) {
		var fileName = path.getFileName().toString();
		if (fileName.length() < 37 || fileName.charAt(36) != '-') {
			return null;
		}

		try {
			return UUID.fromString(fileName.substring(0, 36));
		}
		catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private boolean deletePath(Path path) {
		try {
			Files.deleteIfExists(path);
			return true;
		}
		catch (IOException ignored) {
			return false;
		}
	}

}
