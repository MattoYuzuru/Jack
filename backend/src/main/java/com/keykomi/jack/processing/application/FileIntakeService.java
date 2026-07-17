package com.keykomi.jack.processing.application;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import com.keykomi.jack.processing.domain.ProcessingException;

@Service
public class FileIntakeService {

	private static final int SNIFF_BYTES = 16_384;
	private static final Set<String> GENERIC_MEDIA_TYPES = Set.of(
		"", "application/octet-stream", "binary/octet-stream", "application/zip"
	);
	private static final Map<String, String> EXTENSION_ROUTES = Map.ofEntries(
		Map.entry("csv", "table"), Map.entry("tsv", "table"),
		Map.entry("xls", "workbook"), Map.entry("xlsx", "workbook"), Map.entry("xlsm", "workbook"), Map.entry("ods", "workbook"),
		Map.entry("sqlite", "database"), Map.entry("db", "database"),
		Map.entry("pdf", "pdf"), Map.entry("epub", "epub"), Map.entry("svg", "svg"),
		Map.entry("doc", "office"), Map.entry("docx", "office"), Map.entry("odt", "office"),
		Map.entry("pptx", "office"), Map.entry("rtf", "office"),
		Map.entry("jpg", "image"), Map.entry("jpeg", "image"), Map.entry("png", "image"),
		Map.entry("gif", "image"), Map.entry("webp", "image"), Map.entry("bmp", "image"),
		Map.entry("heic", "image"), Map.entry("heif", "image"), Map.entry("tiff", "image"),
		Map.entry("mp4", "media"), Map.entry("mov", "media"), Map.entry("webm", "media"),
		Map.entry("avi", "media"), Map.entry("mkv", "media"),
		Map.entry("mp3", "audio"), Map.entry("wav", "audio"), Map.entry("ogg", "audio"),
		Map.entry("flac", "audio"), Map.entry("m4a", "audio"), Map.entry("aac", "audio")
	);
	private static final Set<String> TEXT_EXTENSIONS = Set.of(
		"txt", "text", "log", "sql", "md", "markdown", "json", "yaml", "yml", "xml",
		"env", "html", "htm", "css", "js", "ts"
	);
	private final ProcessingResourceBudgetService budgets;

	public FileIntakeService(ProcessingResourceBudgetService budgets) {
		this.budgets = budgets;
	}

	public IntakeResult inspect(Path path, String submittedFileName, String declaredMediaType) throws IOException {
		var fileName = normalizeFileName(submittedFileName);
		var extension = extension(fileName);
		var declaredType = normalizeMediaType(declaredMediaType);
		byte[] prefix;
		try (var input = Files.newInputStream(path)) {
			prefix = input.readNBytes(SNIFF_BYTES);
		}

		var detected = detect(prefix, path, extension);
		validateMismatch(extension, declaredType, detected);
		var effectiveExtension = extension.isBlank() ? detected.primaryExtension() : extension;
		var normalizedName = extension.isBlank() && !detected.primaryExtension().isBlank()
			? fileName + "." + detected.primaryExtension()
			: fileName;
		var effectiveMediaType = "application/octet-stream".equals(detected.mediaType()) && StringUtils.hasText(declaredType)
			? declaredType
			: detected.mediaType();
		return new IntakeResult(normalizedName, effectiveExtension, effectiveMediaType, detected.parserRoute());
	}

	private DetectedType detect(byte[] bytes, Path path, String extension) {
		if (startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII))) {
			return type("application/pdf", "pdf", "pdf");
		}
		if (startsWith(bytes, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a})) {
			return type("image/png", "png", "image");
		}
		if (startsWith(bytes, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
			return type("image/jpeg", "jpg", "image");
		}
		if (startsWith(bytes, "GIF8".getBytes(StandardCharsets.US_ASCII))) {
			return type("image/gif", "gif", "image");
		}
		if (startsWith(bytes, "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII))) {
			return type("application/vnd.sqlite3", "sqlite", "database");
		}
		if (isZip(bytes)) {
			this.budgets.inspectArchive(path);
			return detectZipPackage(path, extension);
		}
		if (isRiff(bytes, "WEBP")) {
			return type("image/webp", "webp", "image");
		}
		if (isRiff(bytes, "WAVE")) {
			return type("audio/wav", "wav", "audio");
		}
		if (isRiff(bytes, "AVI ")) {
			return type("video/x-msvideo", "avi", "media");
		}
		if (bytes.length >= 12 && ascii(bytes, 4, 4).equals("ftyp")) {
			return type(extension.equals("m4a") ? "audio/mp4" : "video/mp4", extension.equals("m4a") ? "m4a" : "mp4", extension.equals("m4a") ? "audio" : "media");
		}
		if (startsWith(bytes, "OggS".getBytes(StandardCharsets.US_ASCII))) {
			return type(extension.equals("ogg") ? "audio/ogg" : "application/ogg", "ogg", "audio");
		}
		if (looksLikeSvg(bytes)) {
			return type("image/svg+xml", "svg", "svg");
		}
		if (looksLikeUtf8Text(bytes)) {
			var route = TEXT_EXTENSIONS.contains(extension) ? "text" : EXTENSION_ROUTES.getOrDefault(extension, "binary");
			return type(mediaTypeForTextExtension(extension), extension, route);
		}
		return type("application/octet-stream", extension, EXTENSION_ROUTES.getOrDefault(extension, "binary"));
	}

	private DetectedType detectZipPackage(Path path, String extension) {
		try (var zip = new ZipFile(path.toFile())) {
			if (zip.getEntry("word/document.xml") != null) {
				return type("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx", "office");
			}
			if (zip.getEntry("xl/workbook.xml") != null) {
				return type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx", "workbook");
			}
			if (zip.getEntry("ppt/presentation.xml") != null) {
				return type("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx", "office");
			}
			if (zip.getEntry("META-INF/container.xml") != null) {
				return type("application/epub+zip", "epub", "epub");
			}
			if (zip.getEntry("content.xml") != null) {
				return type(extension.equals("ods") ? "application/vnd.oasis.opendocument.spreadsheet" : "application/vnd.oasis.opendocument.text", extension.equals("ods") ? "ods" : "odt", extension.equals("ods") ? "workbook" : "office");
			}
			return type("application/zip", "zip", "archive");
		}
		catch (IOException exception) {
			throw new ProcessingException(HttpStatus.UNPROCESSABLE_ENTITY, "CORRUPT_FILE", "Архив повреждён.", exception);
		}
	}

	private void validateMismatch(String extension, String declaredMediaType, DetectedType detected) {
		if (!extension.isBlank() && !detected.primaryExtension().isBlank()
			&& !compatibleExtensions(extension, detected.primaryExtension(), detected.parserRoute())) {
			throw new ProcessingException(HttpStatus.BAD_REQUEST, "FILE_TYPE_MISMATCH", "Содержимое файла не соответствует его extension.");
		}
		if (!GENERIC_MEDIA_TYPES.contains(declaredMediaType) && isStrongMediaType(detected.mediaType())
			&& !sameMediaFamily(declaredMediaType, detected.mediaType())) {
			throw new ProcessingException(HttpStatus.BAD_REQUEST, "FILE_TYPE_MISMATCH", "Содержимое файла не соответствует заявленному MIME type.");
		}
	}

	private String normalizeFileName(String input) {
		var candidate = StringUtils.hasText(input) ? Normalizer.normalize(input.strip(), Normalizer.Form.NFKC) : "upload.bin";
		if (candidate.length() > 180 || candidate.equals(".") || candidate.equals("..") || candidate.contains("/")
			|| candidate.contains("\\") || candidate.codePoints().anyMatch(Character::isISOControl)) {
			throw new ProcessingException(HttpStatus.BAD_REQUEST, "UNSAFE_FILE_NAME", "Имя файла содержит небезопасные символы или path traversal.");
		}
		return candidate;
	}

	private String extension(String fileName) {
		var dot = fileName.lastIndexOf('.');
		return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
	}

	private String normalizeMediaType(String mediaType) {
		if (!StringUtils.hasText(mediaType)) {
			return "";
		}
		var normalized = mediaType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
		return normalized.indexOf('/') > 0 ? normalized : "application/octet-stream";
	}

	private boolean compatibleExtensions(String declared, String detected, String route) {
		if (declared.equals(detected)) {
			return true;
		}
		return (Set.of("jpg", "jpeg").contains(declared) && Set.of("jpg", "jpeg").contains(detected))
			|| (Set.of("xlsx", "xlsm").contains(declared) && "xlsx".equals(detected))
			|| (route.equals("text") && TEXT_EXTENSIONS.contains(declared));
	}

	private boolean sameMediaFamily(String left, String right) {
		return left.substring(0, left.indexOf('/') + 1).equals(right.substring(0, right.indexOf('/') + 1))
			|| (left.contains("zip") && right.contains("officedocument"))
			|| (left.contains("officedocument") && right.contains("zip"));
	}

	private boolean isStrongMediaType(String mediaType) {
		return !Set.of("application/octet-stream", "text/plain").contains(mediaType);
	}

	private boolean looksLikeUtf8Text(byte[] bytes) {
		if (bytes.length == 0) {
			return false;
		}
		for (byte value : bytes) {
			if (value == 0) {
				return false;
			}
		}
		try {
			StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes));
			return true;
		}
		catch (CharacterCodingException exception) {
			return false;
		}
	}

	private boolean looksLikeSvg(byte[] bytes) {
		if (!looksLikeUtf8Text(bytes)) {
			return false;
		}
		var text = new String(bytes, StandardCharsets.UTF_8).stripLeading().toLowerCase(Locale.ROOT);
		return text.startsWith("<svg") || (text.startsWith("<?xml") && text.contains("<svg"));
	}

	private String mediaTypeForTextExtension(String extension) {
		return switch (extension) {
			case "html", "htm" -> "text/html";
			case "csv" -> "text/csv";
			case "tsv" -> "text/tab-separated-values";
			case "json" -> "application/json";
			case "xml" -> "application/xml";
			default -> "text/plain";
		};
	}

	private boolean isZip(byte[] bytes) {
		return startsWith(bytes, new byte[] {'P', 'K', 3, 4}) || startsWith(bytes, new byte[] {'P', 'K', 5, 6});
	}

	private boolean isRiff(byte[] bytes, String kind) {
		return startsWith(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII)) && bytes.length >= 12 && ascii(bytes, 8, 4).equals(kind);
	}

	private boolean startsWith(byte[] value, byte[] prefix) {
		if (value.length < prefix.length) {
			return false;
		}
		for (int index = 0; index < prefix.length; index += 1) {
			if (value[index] != prefix[index]) {
				return false;
			}
		}
		return true;
	}

	private String ascii(byte[] bytes, int offset, int length) {
		return new String(bytes, offset, length, StandardCharsets.US_ASCII);
	}

	private DetectedType type(String mediaType, String extension, String parserRoute) {
		return new DetectedType(mediaType, extension, parserRoute);
	}

	public record IntakeResult(String fileName, String extension, String mediaType, String parserRoute) {
	}

	private record DetectedType(String mediaType, String primaryExtension, String parserRoute) {
	}
}
