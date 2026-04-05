package com.keykomi.jack.processing.web;

import com.keykomi.jack.processing.application.ProcessingJobService;
import com.keykomi.jack.processing.domain.ProcessingJobType;
import com.keykomi.jack.processing.domain.StoredArtifact;
import com.keykomi.jack.processing.domain.StoredProcessingJob;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
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
	public ResponseEntity<ByteArrayResource> downloadArtifact(@PathVariable UUID jobId, @PathVariable UUID artifactId) {
		var artifact = this.processingJobService.getRequiredArtifact(jobId, artifactId);

		try {
			var body = new ByteArrayResource(Files.readAllBytes(artifact.storagePath()));
			var headers = new HttpHeaders();
			headers.setContentType(MediaType.parseMediaType(artifact.mediaType()));
			headers.setContentDisposition(
				ContentDisposition.attachment()
					.filename(artifact.fileName())
					.build()
			);

			return new ResponseEntity<>(body, headers, HttpStatus.OK);
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось прочитать artifact с диска.", exception);
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
			job.errorMessage(),
			job.createdAt(),
			job.startedAt(),
			job.completedAt(),
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
			artifact.createdAt(),
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
		String errorMessage,
		Instant createdAt,
		Instant startedAt,
		Instant completedAt,
		List<ArtifactResponse> artifacts
	) {
	}

	public record ArtifactResponse(
		UUID id,
		String kind,
		String fileName,
		String mediaType,
		long sizeBytes,
		Instant createdAt,
		String downloadPath
	) {
	}

}
