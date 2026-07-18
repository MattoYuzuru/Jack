package com.keykomi.jack.processing.web;

import com.keykomi.jack.processing.application.UploadStorageService;
import com.keykomi.jack.processing.application.DelimitedTablePreviewService;
import com.keykomi.jack.processing.application.WorkbookRangeService;
import com.keykomi.jack.processing.application.SqliteRangeService;
import com.keykomi.jack.processing.domain.ProcessingException;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

	private final UploadStorageService uploadStorageService;
	private final DelimitedTablePreviewService delimitedTablePreviewService;
	private final WorkbookRangeService workbookRangeService;
	private final SqliteRangeService sqliteRangeService;

	public UploadController(
		UploadStorageService uploadStorageService,
		DelimitedTablePreviewService delimitedTablePreviewService,
		WorkbookRangeService workbookRangeService,
		SqliteRangeService sqliteRangeService
	) {
		this.uploadStorageService = uploadStorageService;
		this.delimitedTablePreviewService = delimitedTablePreviewService;
		this.workbookRangeService = workbookRangeService;
		this.sqliteRangeService = sqliteRangeService;
	}

	@GetMapping("/{uploadId}/database-range")
	public SqliteRangeService.DatabaseRange getDatabaseRange(
		@PathVariable UUID uploadId,
		@RequestParam String table,
		@RequestParam(required = false) String cursor,
		@RequestParam(required = false) Integer offset,
		@RequestParam(defaultValue = "50") int limit
	) {
		return this.sqliteRangeService.readRange(
			this.uploadStorageService.getRequiredUpload(uploadId),
			table,
			cursor,
			offset,
			limit
		);
	}

	@GetMapping("/{uploadId}/workbook-range")
	public WorkbookRangeService.WorkbookRange getWorkbookRange(
		@PathVariable UUID uploadId,
		@RequestParam(defaultValue = "0") int sheetIndex,
		@RequestParam(defaultValue = "0") int startRow,
		@RequestParam(defaultValue = "0") int startColumn,
		@RequestParam(defaultValue = "50") int rows,
		@RequestParam(defaultValue = "20") int columns
	) {
		return this.workbookRangeService.readRange(
			this.uploadStorageService.getRequiredUpload(uploadId),
			sheetIndex,
			startRow,
			startColumn,
			rows,
			columns
		);
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

	@GetMapping("/{uploadId}/table-range")
	public DelimitedTablePreviewService.TableWindow getTableRange(
		@PathVariable UUID uploadId,
		@RequestParam(required = false) String cursor,
		@RequestParam(required = false) Integer offset,
		@RequestParam(defaultValue = "50") int limit,
		@RequestParam(required = false) String delimiter,
		@RequestParam(defaultValue = "AUTO") String headerMode
	) {
		var upload = this.uploadStorageService.getRequiredUpload(uploadId);
		if (!"table".equals(upload.parserRoute())) {
			throw new ProcessingException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FORMAT", "Range API доступен только для CSV/TSV uploads.");
		}
		return this.delimitedTablePreviewService.page(
			upload,
			parseDelimiter(delimiter),
			parseHeaderMode(headerMode),
			cursor,
			offset,
			limit
		);
	}

	private Character parseDelimiter(String value) {
		if (value == null || value.isBlank() || "auto".equalsIgnoreCase(value)) {
			return null;
		}
		if ("tab".equalsIgnoreCase(value) || "\\t".equals(value)) {
			return '\t';
		}
		if (value.length() != 1) {
			throw new ProcessingException(HttpStatus.BAD_REQUEST, "INVALID_TABLE_OPTIONS", "Delimiter должен быть одним символом.");
		}
		return value.charAt(0);
	}

	private DelimitedTablePreviewService.HeaderMode parseHeaderMode(String value) {
		try {
			return DelimitedTablePreviewService.HeaderMode.valueOf(value.toUpperCase(java.util.Locale.ROOT));
		}
		catch (IllegalArgumentException exception) {
			throw new ProcessingException(HttpStatus.BAD_REQUEST, "INVALID_TABLE_OPTIONS", "Header mode не поддерживается.");
		}
	}

	private UploadResponse toResponse(StoredUpload upload) {
		return new UploadResponse(
			upload.id(),
			upload.originalFileName(),
			upload.mediaType(),
			upload.extension(),
			upload.parserRoute(),
			upload.sizeBytes(),
				upload.sha256(),
				upload.createdAt(),
				upload.expiresAt(),
				upload.policyVersion()
		);
	}

	public record UploadResponse(
		UUID id,
		String originalFileName,
		String mediaType,
		String extension,
		String parserRoute,
		long sizeBytes,
		String sha256,
		Instant createdAt,
		Instant expiresAt,
		String policyVersion
	) {
	}

}
