package com.keykomi.jack.processing.web;

import com.keykomi.jack.processing.application.UploadStorageService;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

	private final UploadStorageService uploadStorageService;

	public UploadController(UploadStorageService uploadStorageService) {
		this.uploadStorageService = uploadStorageService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UploadResponse upload(@RequestPart("file") MultipartFile file) {
		return toResponse(this.uploadStorageService.store(file));
	}

	@GetMapping("/{uploadId}")
	public UploadResponse getUpload(@PathVariable UUID uploadId) {
		return toResponse(this.uploadStorageService.getRequiredUpload(uploadId));
	}

	private UploadResponse toResponse(StoredUpload upload) {
		return new UploadResponse(
			upload.id(),
			upload.originalFileName(),
			upload.mediaType(),
			upload.extension(),
			upload.sizeBytes(),
			upload.sha256(),
			upload.createdAt()
		);
	}

	public record UploadResponse(
		UUID id,
		String originalFileName,
		String mediaType,
		String extension,
		long sizeBytes,
		String sha256,
		Instant createdAt
	) {
	}

}
