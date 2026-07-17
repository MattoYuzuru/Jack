package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.config.ProcessingProperties;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UploadStorageService {

	private final ProcessingProperties processingProperties;
	private final ProcessingStateStore stateStore;
	private final ProcessingOwnerContext ownerContext;
	private final FileIntakeService fileIntakeService;

	public UploadStorageService(
		ProcessingProperties processingProperties,
		ProcessingStateStore stateStore,
		ProcessingOwnerContext ownerContext,
		FileIntakeService fileIntakeService
	) throws IOException {
		this.processingProperties = processingProperties;
		this.stateStore = stateStore;
		this.ownerContext = ownerContext;
		this.fileIntakeService = fileIntakeService;
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
		var ownerId = this.ownerContext.ownerId();
		var usedBytes = this.stateStore.ownerStorageBytes(ownerId, Instant.now());
		if (usedBytes + file.getSize() > this.processingProperties.getMaxStorageBytesPerSession()) {
			throw new ResponseStatusException(
				HttpStatus.PAYLOAD_TOO_LARGE,
				"Session storage quota исчерпана. Удали старые результаты или повтори после TTL cleanup."
			);
		}

		var uploadId = UUID.randomUUID();
		var temporaryPath = this.processingProperties.uploadsDirectory().resolve(uploadId + ".uploading");
		Path storagePath = null;
		var createdAt = Instant.now();
		var expiresAt = createdAt.plus(this.processingProperties.getUploadRetentionHours(), ChronoUnit.HOURS);
		var stored = false;

		try {
			var digest = MessageDigest.getInstance("SHA-256");
			try (
				InputStream inputStream = file.getInputStream();
				DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest);
				OutputStream outputStream = Files.newOutputStream(temporaryPath)
			) {
				// Хеш считаем в тот же проход, что и запись на диск, чтобы foundation
				// сразу умел строить dedup/cache-friendly идентификатор без второго чтения файла.
				copyBounded(digestInputStream, outputStream, this.processingProperties.getMaxUploadSizeBytes());
			}
			var actualSize = Files.size(temporaryPath);
			if (usedBytes + actualSize > this.processingProperties.getMaxStorageBytesPerSession()) {
				throw new ResponseStatusException(
					HttpStatus.PAYLOAD_TOO_LARGE,
					"Session storage quota исчерпана. Удали старые результаты или повтори после TTL cleanup."
				);
			}
			var intake = this.fileIntakeService.inspect(
				temporaryPath,
				file.getOriginalFilename(),
				file.getContentType()
			);
			storagePath = this.processingProperties.uploadsDirectory().resolve(uploadId + "-" + intake.fileName());
			Files.move(temporaryPath, storagePath, StandardCopyOption.ATOMIC_MOVE);

			var storedUpload = new StoredUpload(
				uploadId,
				intake.fileName(),
				intake.mediaType(),
				intake.extension(),
				intake.parserRoute(),
				actualSize,
				HexFormat.of().formatHex(digest.digest()),
				createdAt,
				expiresAt,
				this.processingProperties.getPolicyVersion(),
				storagePath
			);
			this.stateStore.saveUpload(ownerId, storedUpload);
			stored = true;
			return storedUpload;
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить upload во временное хранилище.", exception);
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 должен быть доступен в стандартной JDK.", exception);
		}
		finally {
			if (!stored) {
				deletePath(temporaryPath);
				if (storagePath != null) {
					deletePath(storagePath);
				}
				}
			}
		}

	public StoredUpload getRequiredUpload(UUID uploadId) {
		return this.stateStore.findOwnedUpload(uploadId, this.ownerContext.ownerId(), Instant.now())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Файл загрузки не найден."));
	}

	public Optional<StoredUpload> findUpload(UUID uploadId) {
		return this.stateStore.findUpload(uploadId);
	}

	public StoredUpload getRequiredUploadInternal(UUID uploadId) {
		return findUpload(uploadId)
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Файл загрузки не найден."));
	}

	public StoredUpload getRequiredUploadForJob(UUID uploadId, UUID jobId) {
		return this.stateStore.findUploadOwnedByJobOwner(uploadId, jobId, Instant.now())
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Связанный upload не найден."));
	}

	public int purgeExpired(Instant cutoff, Set<UUID> retainedUploadIds) {
		var retainedIds = retainedUploadIds == null ? Set.<UUID>of() : Set.copyOf(retainedUploadIds);
		var deletedUploads = 0;

		for (var upload : this.stateStore.listExpiredUploads(cutoff)) {
			if (retainedIds.contains(upload.id()) || !upload.createdAt().isBefore(cutoff)) {
				continue;
			}
			if (!deletePath(upload.storagePath())) {
				continue;
			}
			this.stateStore.deleteUpload(upload.id());
			deletedUploads++;
		}

		try (var storedFiles = Files.list(this.processingProperties.uploadsDirectory())) {
			for (var iterator = storedFiles.iterator(); iterator.hasNext();) {
				var path = iterator.next();
				if (!Files.isRegularFile(path) || !isExpired(path, cutoff)) {
					continue;
				}

				var uploadId = extractUploadId(path);
				if (uploadId != null && (retainedIds.contains(uploadId) || this.stateStore.findUpload(uploadId).isPresent())) {
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

	private void copyBounded(InputStream input, OutputStream output, long maxBytes) throws IOException {
		var buffer = new byte[8_192];
		long total = 0;
		int read;
		while ((read = input.read(buffer)) != -1) {
			total += read;
			if (total > maxBytes) {
				throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Upload превысил допустимый byte budget.");
			}
			output.write(buffer, 0, read);
		}
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
