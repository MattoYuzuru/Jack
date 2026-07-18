package com.keykomi.jack.processing.web;

import com.keykomi.jack.processing.application.ProcessingJobService;
import com.keykomi.jack.processing.domain.ProcessingJobType;
import com.keykomi.jack.processing.domain.StoredArtifact;
import com.keykomi.jack.processing.domain.StoredProcessingJob;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/jobs")
public class ProcessingJobController {

	private final ProcessingJobService processingJobService;

	public ProcessingJobController(ProcessingJobService processingJobService) {
		this.processingJobService = processingJobService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public ProcessingJobResponse createJob(@RequestBody CreateJobRequest request) {
		if (request.uploadId() == null || request.jobType() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для создания job нужны uploadId и jobType.");
		}

		return toResponse(this.processingJobService.enqueue(request.uploadId(), request.jobType(), request.parameters()));
	}

	@GetMapping("/{jobId}")
	public ProcessingJobResponse getJob(@PathVariable UUID jobId) {
		return toResponse(this.processingJobService.getRequiredJob(jobId));
	}

	@DeleteMapping("/{jobId}")
	public ProcessingJobResponse cancelJob(@PathVariable UUID jobId) {
		return toResponse(this.processingJobService.cancel(jobId));
	}

	@GetMapping("/{jobId}/artifacts/{artifactId}")
	public ResponseEntity<?> downloadArtifact(
		@PathVariable UUID jobId,
		@PathVariable UUID artifactId,
		@org.springframework.web.bind.annotation.RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
	) {
		var artifact = this.processingJobService.getRequiredArtifact(jobId, artifactId);
		var resource = new FileSystemResource(artifact.storagePath());
		var headers = downloadHeaders(artifact);

		if (rangeHeader == null || rangeHeader.isBlank()) {
			headers.setContentLength(artifact.sizeBytes());
			return new ResponseEntity<Resource>(resource, headers, HttpStatus.OK);
		}

		var range = parseRange(rangeHeader, artifact.sizeBytes());
		headers.set(HttpHeaders.CONTENT_RANGE, "bytes %d-%d/%d".formatted(range.start(), range.end(), artifact.sizeBytes()));
		headers.setContentLength(range.length());
		return new ResponseEntity<>(new ResourceRegion(resource, range.start(), range.length()), headers, HttpStatus.PARTIAL_CONTENT);
	}

	private HttpHeaders downloadHeaders(StoredArtifact artifact) {
		var headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType(artifact.mediaType()));
		headers.setContentDisposition(ContentDisposition.attachment().filename(artifact.fileName()).build());
		headers.setCacheControl("no-store");
		headers.set("X-Content-Type-Options", "nosniff");
		headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
		return headers;
	}

	private DownloadRange parseRange(String header, long sizeBytes) {
		if (!header.startsWith("bytes=") || header.contains(",") || sizeBytes <= 0L) {
			throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Range не поддерживается.");
		}
		var parts = header.substring("bytes=".length()).split("-", -1);
		try {
			var start = parts[0].isBlank() ? Math.max(0L, sizeBytes - Long.parseLong(parts[1])) : Long.parseLong(parts[0]);
			var requestedEnd = parts.length < 2 || parts[1].isBlank() ? sizeBytes - 1L : Long.parseLong(parts[1]);
			var end = Math.min(requestedEnd, Math.min(sizeBytes - 1L, start + 8_388_607L));
			if (start < 0L || start >= sizeBytes || end < start) {
				throw new NumberFormatException("invalid bounds");
			}
			return new DownloadRange(start, end);
		}
		catch (NumberFormatException exception) {
			throw new ResponseStatusException(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "Range не поддерживается.");
		}
	}

	private ProcessingJobResponse toResponse(StoredProcessingJob job) {
		return new ProcessingJobResponse(
			job.id(),
			job.uploadId(),
			job.type(),
			job.status(),
			job.progressPercent(),
			job.message(),
			job.errorCode(),
			job.errorMessage(),
			job.correlationId(),
			job.createdAt(),
			job.startedAt(),
			job.completedAt(),
			job.expiresAt(),
			job.policyVersion(),
			job.artifacts()
				.stream()
				.map(this::toArtifactResponse)
				.toList()
		);
	}

	private ArtifactResponse toArtifactResponse(StoredArtifact artifact) {
		return new ArtifactResponse(
			artifact.id(),
			artifact.kind(),
			artifact.fileName(),
			artifact.mediaType(),
			artifact.sizeBytes(),
			artifact.sha256(),
			artifact.createdAt(),
			artifact.expiresAt(),
			"/api/jobs/%s/artifacts/%s".formatted(artifact.jobId(), artifact.id())
		);
	}

	public record CreateJobRequest(
		UUID uploadId,
		ProcessingJobType jobType,
		Map<String, Object> parameters
	) {
	}

	public record ProcessingJobResponse(
		UUID id,
		UUID uploadId,
		ProcessingJobType jobType,
		com.keykomi.jack.processing.domain.ProcessingJobStatus status,
		int progressPercent,
		String message,
		String errorCode,
		String errorMessage,
		UUID correlationId,
		Instant createdAt,
		Instant startedAt,
		Instant completedAt,
		Instant expiresAt,
		String policyVersion,
		List<ArtifactResponse> artifacts
	) {
	}

	public record ArtifactResponse(
		UUID id,
		String kind,
		String fileName,
		String mediaType,
		long sizeBytes,
		String sha256,
		Instant createdAt,
		Instant expiresAt,
		String downloadPath
	) {
	}

	private record DownloadRange(long start, long end) {
		long length() {
			return this.end - this.start + 1L;
		}
	}

}
