package com.keykomi.jack.processing.application;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.ExifThumbnailDirectory;
import com.drew.metadata.file.FileTypeDirectory;
import com.drew.metadata.icc.IccDirectory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keykomi.jack.processing.domain.MetadataPayloads;
import com.keykomi.jack.processing.domain.MetadataProcessingRequest;
import com.keykomi.jack.processing.domain.StoredArtifact;
import com.keykomi.jack.processing.domain.StoredUpload;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingException;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfoAscii;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.datatype.Artwork;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MetadataProcessingService {

	private static final int MAX_METADATA_FIELD_LENGTH = 512;
	private static final DateTimeFormatter INPUT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
	private static final DateTimeFormatter EXIF_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
	private static final Set<String> EMBEDDED_JPEG_EXTENSIONS = Set.of("jpg", "jpeg");

	private final ArtifactStorageService artifactStorageService;
	private final ObjectMapper objectMapper;

	public MetadataProcessingService(
		ArtifactStorageService artifactStorageService,
		ObjectMapper objectMapper
	) {
		this.artifactStorageService = artifactStorageService;
		this.objectMapper = objectMapper;
	}

	public boolean isAvailable() {
		return true;
	}

	public MetadataProcessingResult process(UUID jobId, StoredUpload upload, MetadataProcessingRequest request) {
		return switch (request.operation()) {
			case INSPECT_IMAGE -> inspectImage(jobId, upload);
			case INSPECT_AUDIO -> inspectAudio(jobId, upload);
			case EXPORT_IMAGE -> exportImage(jobId, upload, request.editableMetadata());
		};
	}

	private MetadataProcessingResult inspectImage(UUID jobId, StoredUpload upload) {
		ensureFamily(upload, "image", "INSPECT_IMAGE job принимает только image uploads.");
		var result = readImageMetadata(upload);
		var manifest = new MetadataPayloads.MetadataInspectManifest(
			"inspect-image",
			"image",
			result.payload(),
			null,
			result.warnings()
		);
		var artifact = this.artifactStorageService.storeJsonArtifact(
			jobId,
			"metadata-inspect-manifest",
			"metadata-inspect-manifest.json",
			manifest
		);
		return new MetadataProcessingResult(List.of(artifact), "Проверка метаданных изображения");
	}

	private MetadataProcessingResult inspectAudio(UUID jobId, StoredUpload upload) {
		ensureFamily(upload, "audio", "INSPECT_AUDIO job принимает только audio uploads.");
		var result = readAudioMetadata(upload);
		var manifest = new MetadataPayloads.MetadataInspectManifest(
			"inspect-audio",
			"audio",
			null,
			result.payload(),
			result.warnings()
		);
		var artifact = this.artifactStorageService.storeJsonArtifact(
			jobId,
			"metadata-inspect-manifest",
			"metadata-inspect-manifest.json",
			manifest
		);
		return new MetadataProcessingResult(List.of(artifact), "Проверка метаданных аудио");
	}

	private MetadataProcessingResult exportImage(
		UUID jobId,
		StoredUpload upload,
		MetadataPayloads.EditableMetadata editableMetadata
	) {
		ensureFamily(upload, "image", "EXPORT_IMAGE job принимает только image uploads.");
		var normalizedMetadata = validateEditableMetadata(editableMetadata);

		// Встраиваем metadata только в JPEG, где backend умеет контролируемо обновить EXIF.
		// Для остальных форматов сознательно отдаём sidecar, чтобы не портить контейнер
		// и не обещать безопасную запись туда, где у нас пока нет faithful writer path.
		if (EMBEDDED_JPEG_EXTENSIONS.contains(normalizeExtension(upload.extension()))) {
			var exportBytes = writeEmbeddedJpegMetadata(upload, normalizedMetadata);
			var exportArtifact = this.artifactStorageService.storeBytesArtifact(
				jobId,
				"metadata-export-binary",
				withMetadataSuffix(upload.originalFileName()),
				upload.mediaType(),
				exportBytes
			);
			var manifestArtifact = this.artifactStorageService.storeJsonArtifact(
				jobId,
				"metadata-export-manifest",
				"metadata-export-manifest.json",
				new MetadataPayloads.MetadataExportManifest(
					"embedded-jpeg",
					withMetadataSuffix(upload.originalFileName()),
					List.of("Изменения проверены и встроены прямо в JPEG-файл.")
				)
			);
			return new MetadataProcessingResult(List.of(manifestArtifact, exportArtifact), "Экспорт метаданных");
		}

		var sidecarBytes = buildMetadataSidecar(upload, normalizedMetadata);
		var exportArtifact = this.artifactStorageService.storeBytesArtifact(
			jobId,
			"metadata-export-binary",
			withJsonSuffix(upload.originalFileName()),
			"application/json",
			sidecarBytes
		);
		var manifestArtifact = this.artifactStorageService.storeJsonArtifact(
			jobId,
			"metadata-export-manifest",
			"metadata-export-manifest.json",
			new MetadataPayloads.MetadataExportManifest(
				"json-sidecar",
				withJsonSuffix(upload.originalFileName()),
				List.of("Изменения сохранены в отдельный JSON-файл, чтобы не менять оригинальный контейнер.")
			)
		);
		return new MetadataProcessingResult(List.of(manifestArtifact, exportArtifact), "Экспорт метаданных");
	}

	private ImageInspectResult readImageMetadata(StoredUpload upload) {
		try {
			var metadata = ImageMetadataReader.readMetadata(upload.storagePath().toFile());
			var warnings = collectImageWarnings(metadata);
			var summary = buildImageSummary(upload, metadata);
			var groups = buildImageGroups(upload, metadata);
			var editable = buildEditableMetadata(metadata);
			var thumbnailDataUrl = extractImageThumbnail(upload);

			return new ImageInspectResult(
				new MetadataPayloads.ImageMetadataPayload(summary, groups, editable, thumbnailDataUrl),
				warnings
			);
		}
		catch (ImageProcessingException | IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось прочитать метаданные изображения.", exception);
		}
	}

	private AudioInspectResult readAudioMetadata(StoredUpload upload) {
		try {
			AudioFile audioFile = AudioFileIO.read(upload.storagePath().toFile());
			var header = audioFile.getAudioHeader();
			var tag = audioFile.getTag();
			var summary = buildAudioSummary(header, tag);
			var groups = buildAudioGroups(header, tag);
			var artworkDataUrl = extractAudioArtwork(tag);
			var searchableText = buildAudioSearchableText(header, tag);
			var technical = buildAudioTechnicalMetadata(header);

			return new AudioInspectResult(
				new MetadataPayloads.AudioMetadataPayload(summary, groups, artworkDataUrl, searchableText, technical),
				List.of()
			);
		}
		catch (Exception exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось прочитать аудиотеги файла.", exception);
		}
	}

	private List<MetadataPayloads.MetadataItem> buildImageSummary(StoredUpload upload, Metadata metadata) throws IOException {
		var summary = new ArrayList<MetadataPayloads.MetadataItem>();
		var detectedType = Optional.ofNullable(metadata.getFirstDirectoryOfType(FileTypeDirectory.class))
			.map(directory -> directory.getString(FileTypeDirectory.TAG_DETECTED_FILE_TYPE_NAME))
			.filter(value -> value != null && !value.isBlank())
			.orElse(normalizeExtension(upload.extension()).toUpperCase(Locale.ROOT));
		var dimensions = resolveImageDimensions(upload, metadata);

		pushSummaryItem(summary, "Тип файла", detectedType);
		pushSummaryItem(summary, "Ширина", dimensions.width() == null ? null : String.valueOf(dimensions.width()));
		pushSummaryItem(summary, "Высота", dimensions.height() == null ? null : String.valueOf(dimensions.height()));
		pushSummaryItem(summary, "Камера", findImageTag(metadata, "Exif IFD0", "Make"));
		pushSummaryItem(summary, "Модель", findImageTag(metadata, "Exif IFD0", "Model"));
		pushSummaryItem(summary, "Объектив", findImageTag(metadata, "Exif SubIFD", "Lens Model"));
		pushSummaryItem(summary, "Снято", findImageTag(metadata, "Exif SubIFD", "Date/Time Original"));
		pushSummaryItem(summary, "Ориентация", findImageTag(metadata, "Exif IFD0", "Orientation"));
		pushSummaryItem(summary, "ISO", findImageTag(metadata, "Exif SubIFD", "ISO Speed Ratings"));
		pushSummaryItem(summary, "Выдержка", findImageTag(metadata, "Exif SubIFD", "Exposure Time"));
		pushSummaryItem(summary, "Диафрагма", findImageTag(metadata, "Exif SubIFD", "F-Number"));
		pushSummaryItem(summary, "Фокусное расстояние", findImageTag(metadata, "Exif SubIFD", "Focal Length"));
		pushSummaryItem(summary, "Цветовое пространство", findImageTag(metadata, "Exif SubIFD", "Color Space"));
		pushSummaryItem(summary, "ICC profile", resolveIccProfileDescription(metadata));

		return summary;
	}

	private List<MetadataPayloads.MetadataGroup> buildImageGroups(StoredUpload upload, Metadata metadata) throws IOException {
		var groups = new ArrayList<MetadataPayloads.MetadataGroup>();
		var dimensions = resolveImageDimensions(upload, metadata);
		var fileEntries = new ArrayList<MetadataPayloads.MetadataItem>();
		fileEntries.add(new MetadataPayloads.MetadataItem("File Name", upload.originalFileName()));
		fileEntries.add(new MetadataPayloads.MetadataItem("Media Type", upload.mediaType()));
		fileEntries.add(new MetadataPayloads.MetadataItem("Extension", normalizeExtension(upload.extension())));
		fileEntries.add(new MetadataPayloads.MetadataItem("Size", String.valueOf(upload.sizeBytes())));
		if (dimensions.width() != null) {
			fileEntries.add(new MetadataPayloads.MetadataItem("Image Width", String.valueOf(dimensions.width())));
		}
		if (dimensions.height() != null) {
			fileEntries.add(new MetadataPayloads.MetadataItem("Image Height", String.valueOf(dimensions.height())));
		}
		groups.add(new MetadataPayloads.MetadataGroup("file", "File", fileEntries));

		for (Directory directory : metadata.getDirectories()) {
			var entries = directory.getTags().stream()
				.map(this::toImageMetadataItem)
				.filter(item -> item != null)
				.toList();
			if (entries.isEmpty()) {
				continue;
			}
			groups.add(new MetadataPayloads.MetadataGroup(
				toMetadataGroupId(directory.getName()),
				mapImageDirectoryLabel(directory.getName()),
				entries
			));
		}

		return groups;
	}

	private MetadataPayloads.EditableMetadata buildEditableMetadata(Metadata metadata) {
		var ifd0Directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
		var exifSubIfdDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);

		return new MetadataPayloads.EditableMetadata(
			readDirectoryValue(ifd0Directory, ExifIFD0Directory.TAG_IMAGE_DESCRIPTION).orElse(""),
			readDirectoryValue(ifd0Directory, ExifIFD0Directory.TAG_ARTIST).orElse(""),
			readDirectoryValue(ifd0Directory, ExifIFD0Directory.TAG_COPYRIGHT).orElse(""),
			formatExifDateForInput(
				readDirectoryValue(exifSubIfdDirectory, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)
					.orElseGet(() -> readDirectoryValue(exifSubIfdDirectory, ExifSubIFDDirectory.TAG_DATETIME_DIGITIZED).orElse(""))
			)
		);
	}

	private String extractImageThumbnail(StoredUpload upload) {
		try {
			var imageMetadata = Imaging.getMetadata(upload.storagePath().toFile());
			if (!(imageMetadata instanceof JpegImageMetadata jpegImageMetadata)) {
				return null;
			}
			var thumbnailBytes = jpegImageMetadata.getExifThumbnailData();
			if (thumbnailBytes == null || thumbnailBytes.length == 0) {
				return null;
			}
			return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(thumbnailBytes);
		}
		catch (IOException exception) {
			return null;
		}
	}

	private List<String> collectImageWarnings(Metadata metadata) {
		var warnings = new ArrayList<String>();
		for (Directory directory : metadata.getDirectories()) {
			for (String error : directory.getErrors()) {
				warnings.add("%s: %s".formatted(directory.getName(), error));
			}
		}
		return warnings;
	}

	private MetadataPayloads.MetadataItem toImageMetadataItem(Tag tag) {
		var value = Optional.ofNullable(tag.getDescription()).map(String::trim).orElse("");
		if (value.isBlank()) {
			return null;
		}
		return new MetadataPayloads.MetadataItem(tag.getTagName(), value);
	}

	private ImageDimensions resolveImageDimensions(StoredUpload upload, Metadata metadata) throws IOException {
		try (var inputStream = Files.newInputStream(upload.storagePath())) {
			BufferedImage image = ImageIO.read(inputStream);
			if (image != null) {
				return new ImageDimensions(image.getWidth(), image.getHeight());
			}
		}

		var exifSubIfdDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
		Integer width = exifSubIfdDirectory == null ? null : exifSubIfdDirectory.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_WIDTH);
		Integer height = exifSubIfdDirectory == null ? null : exifSubIfdDirectory.getInteger(ExifSubIFDDirectory.TAG_EXIF_IMAGE_HEIGHT);
		return new ImageDimensions(width, height);
	}

	private String findImageTag(Metadata metadata, String directoryName, String tagName) {
		for (Directory directory : metadata.getDirectories()) {
			if (!directoryName.equals(directory.getName())) {
				continue;
			}
			for (Tag tag : directory.getTags()) {
				if (tagName.equals(tag.getTagName())) {
					return Optional.ofNullable(tag.getDescription()).map(String::trim).orElse(null);
				}
			}
		}
		return null;
	}

	private String resolveIccProfileDescription(Metadata metadata) {
		var iccDirectory = metadata.getFirstDirectoryOfType(IccDirectory.class);
		if (iccDirectory == null) {
			return null;
		}
		return Optional.ofNullable(iccDirectory.getDescription(IccDirectory.TAG_TAG_desc))
			.orElseGet(() -> Optional.ofNullable(iccDirectory.getDescription(IccDirectory.TAG_PROFILE_CLASS)).orElse(null));
	}

	private byte[] writeEmbeddedJpegMetadata(
		StoredUpload upload,
		MetadataPayloads.EditableMetadata editableMetadata
	) {
		try {
			var sourceBytes = Files.readAllBytes(upload.storagePath());
			var imageMetadata = Imaging.getMetadata(sourceBytes);
			TiffOutputSet outputSet = null;
			boolean hasExistingExif = false;

			if (imageMetadata instanceof JpegImageMetadata jpegImageMetadata && jpegImageMetadata.getExif() != null) {
				outputSet = jpegImageMetadata.getExif().getOutputSet();
				hasExistingExif = true;
			}

			if (outputSet == null) {
				outputSet = new TiffOutputSet();
			}

			var rootDirectory = outputSet.getOrCreateRootDirectory();
			var exifDirectory = outputSet.getOrCreateExifDirectory();

			mutateAsciiField(rootDirectory, TiffTagConstants.TIFF_TAG_IMAGE_DESCRIPTION, editableMetadata.description());
			mutateAsciiField(rootDirectory, TiffTagConstants.TIFF_TAG_ARTIST, editableMetadata.artist());
			mutateAsciiField(rootDirectory, TiffTagConstants.TIFF_TAG_COPYRIGHT, editableMetadata.copyright());
			mutateAsciiField(rootDirectory, TiffTagConstants.TIFF_TAG_SOFTWARE, "Jack Backend");
			mutateAsciiField(exifDirectory, ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, formatInputDateForExif(editableMetadata.capturedAt()));

			var output = new ByteArrayOutputStream();
			var exifRewriter = new ExifRewriter();
			// У JPEG без исходного EXIF нельзя требовать lossless path:
			// сначала создаём контейнер lossy-веткой, иначе часть файлов ломается на записи.
			if (hasExistingExif) {
				exifRewriter.updateExifMetadataLossless(sourceBytes, output, outputSet);
			} else {
				exifRewriter.updateExifMetadataLossy(sourceBytes, output, outputSet);
			}
			return output.toByteArray();
		}
		catch (IOException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось собрать JPEG с обновлёнными EXIF-полями на backend.", exception);
		}
	}

	private void mutateAsciiField(TiffOutputDirectory directory, TagInfoAscii tagInfo, String value) throws ImagingException {
		directory.removeField(tagInfo);
		if (value == null || value.isBlank()) {
			return;
		}
		directory.add(tagInfo, value.trim());
	}

	private byte[] buildMetadataSidecar(
		StoredUpload upload,
		MetadataPayloads.EditableMetadata editableMetadata
	) {
		var payload = new LinkedHashMap<String, Object>();
		payload.put("fileName", upload.originalFileName());
		payload.put("exportedAt", java.time.Instant.now().toString());
		payload.put("metadata", editableMetadata);
		payload.put("mode", "json-sidecar");
		payload.put("source", "jack-backend-metadata-service");

		try {
			return this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload);
		}
		catch (JsonProcessingException exception) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сериализовать metadata sidecar.", exception);
		}
	}

	private MetadataPayloads.EditableMetadata validateEditableMetadata(MetadataPayloads.EditableMetadata editableMetadata) {
		if (editableMetadata == null) {
			return new MetadataPayloads.EditableMetadata("", "", "", "");
		}

		return new MetadataPayloads.EditableMetadata(
			validateMetadataField("description", editableMetadata.description()),
			validateMetadataField("artist", editableMetadata.artist()),
			validateMetadataField("copyright", editableMetadata.copyright()),
			validateCapturedAt(editableMetadata.capturedAt())
		);
	}

	private String validateMetadataField(String fieldName, String value) {
		var normalizedValue = value == null ? "" : value.trim();
		if (normalizedValue.length() > MAX_METADATA_FIELD_LENGTH) {
			throw new ResponseStatusException(
				HttpStatus.BAD_REQUEST,
				"Поле %s превышает лимит %s символов.".formatted(fieldName, MAX_METADATA_FIELD_LENGTH)
			);
		}
		return normalizedValue;
	}

	private String validateCapturedAt(String value) {
		var normalizedValue = value == null ? "" : value.trim();
		if (normalizedValue.isBlank()) {
			return "";
		}

		try {
			LocalDateTime.parse(normalizedValue, INPUT_DATE_FORMATTER);
			return normalizedValue;
		}
		catch (DateTimeParseException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Поле capturedAt должно быть в формате datetime-local.", exception);
		}
	}

	private String formatInputDateForExif(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}

		try {
			return LocalDateTime.parse(value.trim(), INPUT_DATE_FORMATTER).format(EXIF_DATE_FORMATTER);
		}
		catch (DateTimeParseException exception) {
			return "";
		}
	}

	private String formatExifDateForInput(String value) {
		if (value == null || value.isBlank()) {
			return "";
		}

		try {
			return LocalDateTime.parse(value.trim(), EXIF_DATE_FORMATTER).format(INPUT_DATE_FORMATTER);
		}
		catch (DateTimeParseException exception) {
			return "";
		}
	}

	private List<MetadataPayloads.MetadataItem> buildAudioSummary(AudioHeader header, org.jaudiotagger.tag.Tag tag) {
		var summary = new ArrayList<MetadataPayloads.MetadataItem>();
		pushSummaryItem(summary, "Title", readAudioField(tag, FieldKey.TITLE));
		pushSummaryItem(summary, "Artist", readAudioField(tag, FieldKey.ARTIST));
		pushSummaryItem(summary, "Album", readAudioField(tag, FieldKey.ALBUM));
		pushSummaryItem(summary, "Bitrate", header == null ? null : header.getBitRate());
		pushSummaryItem(summary, "Sample Rate", header == null ? null : header.getSampleRate());
		pushSummaryItem(summary, "Channels", header == null ? null : header.getChannels());
		pushSummaryItem(summary, "Codec", header == null ? null : header.getEncodingType());
		pushSummaryItem(summary, "Container", header == null ? null : header.getFormat());
		return summary;
	}

	private List<MetadataPayloads.MetadataGroup> buildAudioGroups(AudioHeader header, org.jaudiotagger.tag.Tag tag) {
		var groups = new ArrayList<MetadataPayloads.MetadataGroup>();
		var commonEntries = new ArrayList<MetadataPayloads.MetadataItem>();
		pushSummaryItem(commonEntries, "Title", readAudioField(tag, FieldKey.TITLE));
		pushSummaryItem(commonEntries, "Artist", readAudioField(tag, FieldKey.ARTIST));
		pushSummaryItem(commonEntries, "Album Artist", readAudioField(tag, FieldKey.ALBUM_ARTIST));
		pushSummaryItem(commonEntries, "Album", readAudioField(tag, FieldKey.ALBUM));
		pushSummaryItem(commonEntries, "Genre", readAudioField(tag, FieldKey.GENRE));
		pushSummaryItem(commonEntries, "Year", readAudioField(tag, FieldKey.YEAR));
		pushSummaryItem(commonEntries, "Track", readAudioField(tag, FieldKey.TRACK));
		pushSummaryItem(commonEntries, "Disc", readAudioField(tag, FieldKey.DISC_NO));
		pushSummaryItem(commonEntries, "Composer", readAudioField(tag, FieldKey.COMPOSER));
		pushSummaryItem(commonEntries, "Comment", readAudioField(tag, FieldKey.COMMENT));
		if (!commonEntries.isEmpty()) {
			groups.add(new MetadataPayloads.MetadataGroup("common", "Common", commonEntries));
		}

		var formatEntries = new ArrayList<MetadataPayloads.MetadataItem>();
		pushSummaryItem(formatEntries, "Bitrate", header == null ? null : header.getBitRate());
		pushSummaryItem(formatEntries, "Sample Rate", header == null ? null : header.getSampleRate());
		pushSummaryItem(formatEntries, "Channels", header == null ? null : header.getChannels());
		pushSummaryItem(formatEntries, "Codec", header == null ? null : header.getEncodingType());
		pushSummaryItem(formatEntries, "Container", header == null ? null : header.getFormat());
		pushSummaryItem(formatEntries, "Track Length", header == null ? null : String.valueOf(header.getTrackLength()));
		if (!formatEntries.isEmpty()) {
			groups.add(new MetadataPayloads.MetadataGroup("format", "Format", formatEntries));
		}

		if (tag != null) {
			var nativeEntries = new LinkedHashMap<String, String>();
			for (var iterator = tag.getFields(); iterator.hasNext();) {
				TagField field = iterator.next();
				var value = String.valueOf(field);
				if (value == null || value.isBlank() || value.startsWith("Artwork")) {
					continue;
				}
				nativeEntries.putIfAbsent(field.getId(), value.trim());
			}
			if (!nativeEntries.isEmpty()) {
				groups.add(new MetadataPayloads.MetadataGroup(
					"native",
					"Native Tags",
					nativeEntries.entrySet().stream()
						.map(entry -> new MetadataPayloads.MetadataItem(entry.getKey(), entry.getValue()))
						.toList()
				));
			}
		}

		return groups;
	}

	private MetadataPayloads.AudioTechnicalMetadata buildAudioTechnicalMetadata(AudioHeader header) {
		if (header == null) {
			return new MetadataPayloads.AudioTechnicalMetadata(null, null, null, null);
		}

		return new MetadataPayloads.AudioTechnicalMetadata(
			parseIntegerValue(header.getSampleRate()),
			resolveChannelCount(header.getChannels()),
			normalizeValue(header.getEncodingType()),
			normalizeValue(header.getFormat())
		);
	}

	private String extractAudioArtwork(org.jaudiotagger.tag.Tag tag) {
		if (tag == null) {
			return null;
		}

		try {
			Artwork artwork = tag.getFirstArtwork();
			if (artwork == null || artwork.getBinaryData() == null || artwork.getBinaryData().length == 0) {
				return null;
			}
			var mimeType = Optional.ofNullable(artwork.getMimeType()).filter(value -> !value.isBlank()).orElse("image/jpeg");
			return "data:%s;base64,%s".formatted(mimeType, Base64.getEncoder().encodeToString(artwork.getBinaryData()));
		}
		catch (Exception ignored) {
			return null;
		}
	}

	private String buildAudioSearchableText(AudioHeader header, org.jaudiotagger.tag.Tag tag) {
		var searchableParts = new LinkedHashSet<String>();
		addIfPresent(searchableParts, readAudioField(tag, FieldKey.TITLE));
		addIfPresent(searchableParts, readAudioField(tag, FieldKey.ARTIST));
		addIfPresent(searchableParts, readAudioField(tag, FieldKey.ALBUM_ARTIST));
		addIfPresent(searchableParts, readAudioField(tag, FieldKey.ALBUM));
		addIfPresent(searchableParts, readAudioField(tag, FieldKey.GENRE));
		addIfPresent(searchableParts, readAudioField(tag, FieldKey.COMMENT));
		addIfPresent(searchableParts, readAudioField(tag, FieldKey.COMPOSER));
		addIfPresent(searchableParts, header == null ? null : header.getEncodingType());
		addIfPresent(searchableParts, header == null ? null : header.getFormat());
		return String.join(" ", searchableParts);
	}

	private Optional<String> readDirectoryValue(Directory directory, int tagType) {
		if (directory == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(directory.getDescription(tagType)).map(String::trim).filter(value -> !value.isBlank());
	}

	private String readAudioField(org.jaudiotagger.tag.Tag tag, FieldKey fieldKey) {
		if (tag == null) {
			return null;
		}
		try {
			return normalizeValue(tag.getFirst(fieldKey));
		}
		catch (Exception ignored) {
			return null;
		}
	}

	private Integer resolveChannelCount(String channelDescription) {
		var normalized = normalizeValue(channelDescription);
		if (normalized == null) {
			return null;
		}
		var lowerCased = normalized.toLowerCase(Locale.ROOT);
		if (lowerCased.contains("mono")) {
			return 1;
		}
		if (lowerCased.contains("stereo")) {
			return 2;
		}
		return parseIntegerValue(normalized);
	}

	private Integer parseIntegerValue(String value) {
		var normalized = normalizeValue(value);
		if (normalized == null) {
			return null;
		}
		var digits = normalized.replaceAll("[^0-9]", "");
		if (digits.isBlank()) {
			return null;
		}
		try {
			return Integer.parseInt(digits);
		}
		catch (NumberFormatException exception) {
			return null;
		}
	}

	private String normalizeValue(String value) {
		if (value == null) {
			return null;
		}
		var normalized = value.trim();
		return normalized.isBlank() ? null : normalized;
	}

	private void pushSummaryItem(List<MetadataPayloads.MetadataItem> items, String label, String value) {
		var normalizedValue = normalizeValue(value);
		if (normalizedValue == null) {
			return;
		}
		items.add(new MetadataPayloads.MetadataItem(label, normalizedValue));
	}

	private void addIfPresent(Set<String> target, String value) {
		var normalizedValue = normalizeValue(value);
		if (normalizedValue != null) {
			target.add(normalizedValue);
		}
	}

	private String toMetadataGroupId(String directoryName) {
		var normalized = directoryName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
		return normalized.isBlank() ? "metadata-group" : normalized;
	}

	private String mapImageDirectoryLabel(String directoryName) {
		return switch (directoryName) {
			case "Exif IFD0", "Exif SubIFD" -> "EXIF";
			case "GPS" -> "GPS";
			case "ICC Profile" -> "ICC Profile";
			case "Makernote", "Olympus Makernote", "Canon Makernote", "Nikon Makernote", "Sony Makernote", "Panasonic Makernote", "Fujifilm Makernote" -> "Maker Notes";
			case "Photoshop" -> "Photoshop";
			case "Exif Thumbnail" -> "Миниатюра";
			default -> directoryName;
		};
	}

	private void ensureFamily(StoredUpload upload, String expectedFamily, String message) {
		if (!expectedFamily.equals(ProcessingFileFamilyResolver.detectFamily(upload))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
		}
	}

	private String normalizeExtension(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

	private String withMetadataSuffix(String fileName) {
		var dotIndex = fileName.lastIndexOf('.');
		if (dotIndex < 0) {
			return fileName + "-metadata";
		}
		return fileName.substring(0, dotIndex) + "-metadata" + fileName.substring(dotIndex);
	}

	private String withJsonSuffix(String fileName) {
		return fileName + ".jack-metadata.json";
	}

	private record ImageDimensions(
		Integer width,
		Integer height
	) {
	}

	private record ImageInspectResult(
		MetadataPayloads.ImageMetadataPayload payload,
		List<String> warnings
	) {
	}

	private record AudioInspectResult(
		MetadataPayloads.AudioMetadataPayload payload,
		List<String> warnings
	) {
	}

	public record MetadataProcessingResult(
		List<StoredArtifact> artifacts,
		String runtimeLabel
	) {
	}

}
