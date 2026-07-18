package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.config.ProcessingProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.keykomi.jack.processing.domain.ProcessingException;

@Service
public class ProcessingResourceBudgetService {

	private static final Set<String> ARCHIVE_EXTENSIONS = Set.of(
		"zip", "jar", "epub", "docx", "xlsx", "pptx", "odt", "ods"
	);
	private final ProcessingProperties properties;

	public ProcessingResourceBudgetService(ProcessingProperties properties) {
		this.properties = properties;
	}

	public void verifyDecodedPixels(long width, long height) {
		if (width <= 0 || height <= 0 || exceedsProduct(width, height, this.properties.getMaxDecodedPixels())) {
			throw tooLarge("Изображение превышает допустимый decoded-pixel budget.");
		}
	}

	public void verifyDocumentPages(int pages) {
		if (pages < 0 || pages > this.properties.getMaxDocumentPages()) {
			throw tooLarge("Документ превышает допустимое число страниц.");
		}
	}

	public void verifyTableShape(long rows, long columns) {
		if (rows < 0 || columns < 0 || rows > this.properties.getMaxTableRows()
			|| exceedsProduct(rows, columns, this.properties.getMaxTableCells())) {
			throw tooLarge("Таблица превышает допустимый row/cell budget.");
		}
	}

	public ArchiveFacts inspectArchive(Path archivePath) {
		long entries = 0;
		long expandedBytes = 0;
		try (var zipFile = new ZipFile(archivePath.toFile())) {
			var enumeration = zipFile.entries();
			while (enumeration.hasMoreElements()) {
				var entry = enumeration.nextElement();
				entries += 1;
				if (entries > this.properties.getMaxArchiveEntries()) {
					throw tooLarge("Архив содержит слишком много entries.");
				}
				validateEntryName(entry.getName());
				if (entry.isDirectory()) {
					continue;
				}
				var size = entry.getSize();
				var compressedSize = entry.getCompressedSize();
				if (size < 0 || compressedSize < 0) {
					throw corrupt("Архив содержит entry без проверяемого размера.");
				}
				expandedBytes = Math.addExact(expandedBytes, size);
				if (expandedBytes > this.properties.getMaxArchiveExpandedBytes()) {
					throw tooLarge("Архив превышает expanded-bytes budget.");
				}
				if (compressedSize == 0 ? size > 0 : size / Math.max(1, compressedSize) > this.properties.getMaxArchiveExpansionRatio()) {
					throw tooLarge("Архив превышает допустимый expansion ratio.");
				}
				if (this.properties.getMaxArchiveDepth() < 2 && isNestedArchive(entry.getName())) {
					throw tooLarge("Вложенные архивы запрещены текущим archive-depth budget.");
				}
			}
			return new ArchiveFacts(Math.toIntExact(entries), expandedBytes);
		}
		catch (ArithmeticException exception) {
			throw tooLarge("Архив превышает expanded-bytes budget.");
		}
		catch (IOException exception) {
			throw corrupt("Архив повреждён или использует неподдерживаемую структуру.");
		}
	}

	public Optional<String> readZipEntryAsText(ZipFile zipFile, String entryName, long maxBytes) throws IOException {
		validateEntryName(entryName);
		var entry = zipFile.getEntry(entryName);
		if (entry == null || entry.isDirectory()) {
			return Optional.empty();
		}
		var effectiveLimit = Math.min(maxBytes, this.properties.getMaxArchiveExpandedBytes());
		if (entry.getSize() < 0 || entry.getSize() > effectiveLimit) {
			throw tooLarge("Archive entry превышает допустимый размер.");
		}
		try (var input = zipFile.getInputStream(entry)) {
			return Optional.of(new String(readBounded(input, effectiveLimit), StandardCharsets.UTF_8));
		}
	}

	public byte[] readBounded(InputStream input, long maxBytes) throws IOException {
		var output = new ByteArrayOutputStream((int) Math.min(maxBytes, 16_384));
		var buffer = new byte[8_192];
		long total = 0;
		int read;
		while ((read = input.read(buffer)) != -1) {
			total += read;
			if (total > maxBytes) {
				throw tooLarge("Поток превысил допустимый byte budget.");
			}
			output.write(buffer, 0, read);
		}
		return output.toByteArray();
	}

	public void verifyResultSize(long sizeBytes) {
		if (sizeBytes < 0 || sizeBytes > this.properties.getMaxResultBytes()) {
			throw tooLarge("Результат превышает допустимый byte budget.");
		}
	}

	private void validateEntryName(String name) {
		if (name == null || name.isBlank() || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0
			|| name.codePoints().anyMatch(Character::isISOControl)) {
			throw corrupt("Архив содержит небезопасное имя entry.");
		}
		var comparableName = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
		var path = Path.of(comparableName);
		var normalized = path.normalize();
		if (path.isAbsolute() || normalized.startsWith("..") || !normalized.toString().replace('\\', '/').equals(comparableName)) {
			throw corrupt("Архив содержит path traversal entry.");
		}
	}

	private boolean isNestedArchive(String name) {
		var normalized = name.toLowerCase(Locale.ROOT);
		var dotIndex = normalized.lastIndexOf('.');
		return dotIndex >= 0 && ARCHIVE_EXTENSIONS.contains(normalized.substring(dotIndex + 1));
	}

	private boolean exceedsProduct(long left, long right, long limit) {
		return left != 0 && right > limit / left;
	}

	private ResponseStatusException tooLarge(String message) {
		return new ProcessingException(HttpStatus.PAYLOAD_TOO_LARGE, "RESOURCE_LIMIT_EXCEEDED", message);
	}

	private ResponseStatusException corrupt(String message) {
		return new ProcessingException(HttpStatus.UNPROCESSABLE_ENTITY, "CORRUPT_FILE", message);
	}

	public record ArchiveFacts(int entryCount, long expandedBytes) {
	}
}
