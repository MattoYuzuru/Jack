package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.CapabilityMatrixPayloads;
import com.keykomi.jack.processing.domain.ProcessingJobType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CapabilityCatalogService {

	private final MediaPreviewService mediaPreviewService;
	private final MediaConversionService mediaConversionService;
	private final ImageProcessingService imageProcessingService;
	private final CompressionService compressionService;
	private final PdfToolkitService pdfToolkitService;
	private final OfficeConversionService officeConversionService;
	private final DocumentPreviewService documentPreviewService;
	private final MetadataProcessingService metadataProcessingService;
	private final ViewerResolveService viewerResolveService;
	private final EditorProcessingService editorProcessingService;
	private final CapabilityMatrixService capabilityMatrixService;
	private final CompressionCapabilityMatrixService compressionCapabilityMatrixService;
	private final PdfToolkitCapabilityMatrixService pdfToolkitCapabilityMatrixService;
	private final EditorCapabilityMatrixService editorCapabilityMatrixService;

	public CapabilityCatalogService(
		MediaPreviewService mediaPreviewService,
		MediaConversionService mediaConversionService,
		ImageProcessingService imageProcessingService,
		CompressionService compressionService,
		PdfToolkitService pdfToolkitService,
		OfficeConversionService officeConversionService,
		DocumentPreviewService documentPreviewService,
		MetadataProcessingService metadataProcessingService,
		ViewerResolveService viewerResolveService,
		EditorProcessingService editorProcessingService,
		CapabilityMatrixService capabilityMatrixService,
		CompressionCapabilityMatrixService compressionCapabilityMatrixService,
		PdfToolkitCapabilityMatrixService pdfToolkitCapabilityMatrixService,
		EditorCapabilityMatrixService editorCapabilityMatrixService
	) {
		this.mediaPreviewService = mediaPreviewService;
		this.mediaConversionService = mediaConversionService;
		this.imageProcessingService = imageProcessingService;
		this.compressionService = compressionService;
		this.pdfToolkitService = pdfToolkitService;
		this.officeConversionService = officeConversionService;
		this.documentPreviewService = documentPreviewService;
		this.metadataProcessingService = metadataProcessingService;
		this.viewerResolveService = viewerResolveService;
		this.editorProcessingService = editorProcessingService;
		this.capabilityMatrixService = capabilityMatrixService;
		this.compressionCapabilityMatrixService = compressionCapabilityMatrixService;
		this.pdfToolkitCapabilityMatrixService = pdfToolkitCapabilityMatrixService;
		this.editorCapabilityMatrixService = editorCapabilityMatrixService;
	}

	public CapabilityScope viewerCapabilities() {
		var availabilityByJobType = availabilityByJobType();
		var mediaPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_PREVIEW, false);
		var imageProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false);
		var officeConversionAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.OFFICE_CONVERT, false);
		var documentPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.DOCUMENT_PREVIEW, false);
		var metadataProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.METADATA_EXPORT, false);
		var viewerResolveAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.VIEWER_RESOLVE, false);

		return new CapabilityScope(
			"viewer",
			"viewer-backend-first",
			List.of(
				new JobTypeCapability(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true, "Сервис уже умеет принять файл и подготовить его к обработке."),
				new JobTypeCapability(
					ProcessingJobType.MEDIA_PREVIEW,
					mediaPreviewAvailable,
					mediaPreviewAvailable
						? "Сервис уже умеет подготавливать совместимый предпросмотр аудио и видео."
						: "Для предпросмотра аудио и видео нужны доступные ffmpeg и ffprobe."
				),
				new JobTypeCapability(
					ProcessingJobType.IMAGE_CONVERT,
					imageProcessingAvailable,
					imageProcessingAvailable
						? "Сервис уже умеет открывать сложные графические форматы и готовить их к просмотру."
						: "Для обработки изображений нужны доступные convert, ffmpeg, potrace и raw-preview."
				),
				new JobTypeCapability(
					ProcessingJobType.OFFICE_CONVERT,
					officeConversionAvailable,
					officeConversionAvailable
						? "Сервис уже умеет готовить документы, таблицы и презентации к просмотру и конвертации."
						: "Сейчас недоступна подготовка офисных документов и PDF."
				),
				new JobTypeCapability(
					ProcessingJobType.DOCUMENT_PREVIEW,
					documentPreviewAvailable,
					documentPreviewAvailable
						? "Сервис уже умеет собирать текст, структуру и факты по документам."
						: "Сейчас недоступен расширенный просмотр документов."
				),
				new JobTypeCapability(
					ProcessingJobType.METADATA_EXPORT,
					metadataProcessingAvailable,
					metadataProcessingAvailable
						? "Сервис уже умеет читать и сохранять метаданные изображений и аудио."
						: "Сейчас недоступна работа с метаданными."
				),
				new JobTypeCapability(
					ProcessingJobType.VIEWER_RESOLVE,
					viewerResolveAvailable,
					viewerResolveAvailable
						? "Viewer уже умеет собирать единый маршрут просмотра для сложных форматов."
						: "Сейчас недоступна подготовка просмотра для части сложных форматов."
				)
			),
			List.of(
				"Viewer подготавливает сложные форматы на сервере и открывает их в едином рабочем окне.",
				"Браузер отвечает за отображение, поиск и взаимодействие, а тяжёлая подготовка остаётся на стороне сервиса."
			),
			this.capabilityMatrixService.viewerMatrix(availabilityByJobType),
			null,
			null,
			null,
			null,
			null
		);
	}

	public CapabilityScope converterCapabilities() {
		var availabilityByJobType = availabilityByJobType();
		var mediaPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_PREVIEW, false);
		var mediaConversionAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_CONVERT, false);
		var imageProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false);
		var officeConversionAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.OFFICE_CONVERT, false);
		var documentPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.DOCUMENT_PREVIEW, false);
		var metadataProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.METADATA_EXPORT, false);

		return new CapabilityScope(
			"converter",
			"converter-backend-first",
			List.of(
				new JobTypeCapability(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true, "Файл можно заранее проверить и подготовить перед конвертацией."),
				new JobTypeCapability(
					ProcessingJobType.IMAGE_CONVERT,
					imageProcessingAvailable,
					imageProcessingAvailable
						? "Конвертер уже умеет обрабатывать изображения и отдавать готовый результат с предпросмотром."
						: "Сейчас недоступна серверная обработка изображений."
				),
				new JobTypeCapability(
					ProcessingJobType.OFFICE_CONVERT,
					officeConversionAvailable,
					officeConversionAvailable
						? "Конвертер уже умеет обрабатывать офисные файлы и PDF."
						: "Сейчас недоступна конвертация офисных файлов и PDF."
				),
				new JobTypeCapability(
					ProcessingJobType.MEDIA_PREVIEW,
					mediaPreviewAvailable,
					mediaPreviewAvailable
						? "Предпросмотр медиа уже доступен для сценариев конвертации и сжатия."
						: "Для предпросмотра медиа нужны доступные ffmpeg и ffprobe."
				),
				new JobTypeCapability(
					ProcessingJobType.MEDIA_CONVERT,
					mediaConversionAvailable,
					mediaConversionAvailable
						? "Конвертер уже умеет обрабатывать видео и аудио."
						: "Сейчас недоступна конвертация видео и аудио."
				),
				new JobTypeCapability(
					ProcessingJobType.DOCUMENT_PREVIEW,
					documentPreviewAvailable,
					documentPreviewAvailable
						? "Расширенный просмотр документов уже готов для смежных сценариев."
						: "Сейчас недоступен расширенный просмотр документов."
				),
				new JobTypeCapability(
					ProcessingJobType.METADATA_EXPORT,
					metadataProcessingAvailable,
					metadataProcessingAvailable
						? "Сервис метаданных уже доступен для смежных сценариев."
						: "Сейчас недоступна работа с метаданными."
				)
			),
			List.of(
				"Converter обрабатывает изображения, документы, таблицы, презентации, видео и аудио в одном окне.",
				"Пользователь видит прогресс, предпросмотр и итоговый файл без ручной настройки внутренних маршрутов."
			),
			null,
			null,
			this.capabilityMatrixService.converterMatrix(availabilityByJobType),
			null,
			null,
			null
		);
	}

	public CapabilityScope compressionCapabilities() {
		var availabilityByJobType = availabilityByJobType();
		var compressionAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.FILE_COMPRESS, false);
		var imageProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false);
		var mediaConversionAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_CONVERT, false);
		var mediaPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_PREVIEW, false);

		return new CapabilityScope(
			"compression",
			"compression-backend-first",
			List.of(
				new JobTypeCapability(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true, "Файл можно сразу подготовить к сжатию и повторным попыткам."),
				new JobTypeCapability(
					ProcessingJobType.FILE_COMPRESS,
					compressionAvailable,
					compressionAvailable
						? "Compression уже умеет подбирать лучший вариант под лимит размера или ручные ограничения."
						: "Сейчас недоступно сжатие файлов в выбранной среде."
				),
				new JobTypeCapability(
					ProcessingJobType.IMAGE_CONVERT,
					imageProcessingAvailable,
					imageProcessingAvailable
						? "Обработка изображений уже доступна для сценариев сжатия."
						: "Сейчас недоступна обработка изображений для сжатия."
				),
				new JobTypeCapability(
					ProcessingJobType.MEDIA_CONVERT,
					mediaConversionAvailable,
					mediaConversionAvailable
						? "Обработка медиа уже доступна для сценариев сжатия."
						: "Сейчас недоступна обработка видео и аудио для сжатия."
				),
				new JobTypeCapability(
					ProcessingJobType.MEDIA_PREVIEW,
					mediaPreviewAvailable,
					mediaPreviewAvailable
						? "Предпросмотр медиа уже доступен для сценариев сжатия."
						: "Сейчас недоступен предпросмотр медиа."
				)
			),
			List.of(
				"Compression решает задачу уменьшения файла отдельно от обычной конвертации.",
				"Сервис сам перебирает варианты и возвращает один лучший результат с историей попыток."
			),
			null,
			this.compressionCapabilityMatrixService.compressionMatrix(availabilityByJobType),
			null,
			null,
			null,
			null
		);
	}

	public CapabilityScope pdfToolkitCapabilities() {
		var availabilityByJobType = availabilityByJobType();
		var pdfToolkitAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.PDF_TOOLKIT, false);
		var viewerResolveAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.VIEWER_RESOLVE, false);
		var documentPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.DOCUMENT_PREVIEW, false);
		var imageProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false);
		var officeConversionAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.OFFICE_CONVERT, false);

		return new CapabilityScope(
			"pdf-toolkit",
			"pdf-toolkit-backend-first",
			List.of(
				new JobTypeCapability(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true, "Файл можно заранее подготовить для импорта в PDF и следующих операций."),
				new JobTypeCapability(
					ProcessingJobType.PDF_TOOLKIT,
					pdfToolkitAvailable,
					pdfToolkitAvailable
						? "PDF Toolkit уже умеет объединять, разделять, поворачивать, распознавать и защищать документы."
						: "Сейчас недоступен модуль PDF Toolkit."
				),
				new JobTypeCapability(
					ProcessingJobType.VIEWER_RESOLVE,
					viewerResolveAvailable,
					viewerResolveAvailable
						? "Просмотр страниц уже доступен для работы с PDF."
						: "Сейчас недоступна подготовка просмотра PDF."
				),
				new JobTypeCapability(
					ProcessingJobType.DOCUMENT_PREVIEW,
					documentPreviewAvailable,
					documentPreviewAvailable
						? "Сводка, поиск и факты по PDF уже доступны."
						: "Сейчас недоступны поиск и сводка по PDF."
				),
				new JobTypeCapability(
					ProcessingJobType.IMAGE_CONVERT,
					imageProcessingAvailable,
					imageProcessingAvailable
						? "Импорт изображений в PDF уже доступен."
						: "Сейчас недоступен импорт изображений в PDF."
				),
				new JobTypeCapability(
					ProcessingJobType.OFFICE_CONVERT,
					officeConversionAvailable,
					officeConversionAvailable
						? "Импорт офисных файлов в PDF уже доступен."
						: "Сейчас недоступен импорт офисных файлов в PDF."
				)
			),
			List.of(
				"PDF Toolkit собирает просмотр, импорт и операции с PDF в одном рабочем окне.",
				"Здесь можно открыть документ, выполнить действие и сразу перейти к следующему шагу без ручных обходов."
			),
			null,
			null,
			null,
			this.pdfToolkitCapabilityMatrixService.pdfToolkitMatrix(availabilityByJobType, this.pdfToolkitService.isOcrAvailable()),
			null,
			null
		);
	}

	public CapabilityScope editorCapabilities() {
		var availabilityByJobType = availabilityByJobType();
		var editorProcessingAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.EDITOR_PROCESS, false);
		var documentPreviewAvailable = availabilityByJobType.getOrDefault(ProcessingJobType.DOCUMENT_PREVIEW, false);

		return new CapabilityScope(
			"editor",
			"editor-backend-first",
			List.of(
				new JobTypeCapability(
					ProcessingJobType.UPLOAD_INTAKE_ANALYSIS,
					true,
					"Файл уже можно подготовить для проверки и экспорта из редактора."
				),
				new JobTypeCapability(
					ProcessingJobType.DOCUMENT_PREVIEW,
					documentPreviewAvailable,
					documentPreviewAvailable
						? "Подготовка текста и структуры уже доступна для редактора."
						: "Сейчас ограничены подсказки и сводка по документу."
				),
				new JobTypeCapability(
					ProcessingJobType.EDITOR_PROCESS,
					editorProcessingAvailable,
					editorProcessingAvailable
						? "Редактор уже умеет собирать замечания, структуру и готовый экспорт."
						: "Сейчас недоступна серверная проверка черновика."
				)
			),
			List.of(
				"Редактор оставляет быстрые правки и превью в браузере, а проверку и экспорт доверяет сервису.",
				"Так пользователь получает знакомую рабочую зону и более надёжный результат на выходе."
			),
			null,
			null,
			null,
			null,
			null,
			this.editorCapabilityMatrixService.editorMatrix(availabilityByJobType)
		);
	}

	public CapabilityScope platformCapabilities() {
		var availabilityByJobType = availabilityByJobType();
		return new CapabilityScope(
			"platform",
			"processing-platform",
			List.of(
				new JobTypeCapability(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true, "Платформа уже умеет принимать файлы для новых модулей."),
				new JobTypeCapability(
					ProcessingJobType.FILE_COMPRESS,
					availabilityByJobType.getOrDefault(ProcessingJobType.FILE_COMPRESS, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.FILE_COMPRESS, false)
						? "Модуль Compression уже доступен как отдельный продуктовый сценарий."
						: "Модуль Compression сейчас недоступен."
				),
				new JobTypeCapability(
					ProcessingJobType.PDF_TOOLKIT,
					availabilityByJobType.getOrDefault(ProcessingJobType.PDF_TOOLKIT, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.PDF_TOOLKIT, false)
						? "Модуль PDF Toolkit уже доступен как отдельный продуктовый сценарий."
						: "Модуль PDF Toolkit сейчас недоступен."
				),
				new JobTypeCapability(
					ProcessingJobType.MEDIA_PREVIEW,
					availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_PREVIEW, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_PREVIEW, false)
						? "Предпросмотр медиа уже доступен для текущих и следующих сценариев."
						: "Для предпросмотра медиа нужны доступные ffmpeg и ffprobe."
				),
				new JobTypeCapability(
					ProcessingJobType.MEDIA_CONVERT,
					availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_CONVERT, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.MEDIA_CONVERT, false)
						? "Конвертация медиа уже доступна для текущих и следующих сценариев."
						: "Для конвертации медиа нужны доступные ffmpeg и ffprobe."
				),
				new JobTypeCapability(
					ProcessingJobType.IMAGE_CONVERT,
					availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.IMAGE_CONVERT, false)
						? "Обработка изображений уже доступна для текущих и следующих сценариев."
						: "Для обработки изображений нужны доступные convert, ffmpeg, potrace и raw-preview."
				),
				new JobTypeCapability(
					ProcessingJobType.OFFICE_CONVERT,
					availabilityByJobType.getOrDefault(ProcessingJobType.OFFICE_CONVERT, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.OFFICE_CONVERT, false)
						? "Обработка офисных файлов уже доступна для текущих и следующих сценариев."
						: "Сейчас недоступна обработка офисных файлов."
				),
				new JobTypeCapability(
					ProcessingJobType.DOCUMENT_PREVIEW,
					availabilityByJobType.getOrDefault(ProcessingJobType.DOCUMENT_PREVIEW, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.DOCUMENT_PREVIEW, false)
						? "Расширенный просмотр документов уже доступен для текущих и следующих сценариев."
						: "Сейчас недоступен расширенный просмотр документов."
				),
				new JobTypeCapability(
					ProcessingJobType.METADATA_EXPORT,
					availabilityByJobType.getOrDefault(ProcessingJobType.METADATA_EXPORT, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.METADATA_EXPORT, false)
						? "Работа с метаданными уже доступна для текущих и следующих сценариев."
						: "Сейчас недоступна работа с метаданными."
				),
				new JobTypeCapability(
					ProcessingJobType.VIEWER_RESOLVE,
					availabilityByJobType.getOrDefault(ProcessingJobType.VIEWER_RESOLVE, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.VIEWER_RESOLVE, false)
						? "Единый маршрут просмотра уже доступен для новых модулей."
						: "Сейчас недоступна единая подготовка просмотра."
				),
				new JobTypeCapability(
					ProcessingJobType.EDITOR_PROCESS,
					availabilityByJobType.getOrDefault(ProcessingJobType.EDITOR_PROCESS, false),
					availabilityByJobType.getOrDefault(ProcessingJobType.EDITOR_PROCESS, false)
						? "Сценарии проверки и экспорта из редактора уже доступны."
						: "Сейчас недоступна серверная проверка и экспорт из редактора."
				)
			),
			List.of(
				"Платформа уже объединяет общие сценарии загрузки, подготовки и выдачи результата для нескольких модулей.",
				"Новые инструменты можно строить поверх этих готовых возможностей без дублирования базовой логики."
			),
			null,
			null,
			null,
			null,
			this.capabilityMatrixService.platformMatrix(availabilityByJobType),
			null
		);
	}

	private Map<ProcessingJobType, Boolean> availabilityByJobType() {
		// Backend matrix вычисляется от живых processing services, чтобы frontend видел
		// не абстрактный roadmap, а реальную доступность конкретных format/scenario routes.
		var availabilityByJobType = new LinkedHashMap<ProcessingJobType, Boolean>();
		availabilityByJobType.put(ProcessingJobType.UPLOAD_INTAKE_ANALYSIS, true);
		availabilityByJobType.put(ProcessingJobType.FILE_COMPRESS, this.compressionService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.PDF_TOOLKIT, this.pdfToolkitService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.MEDIA_PREVIEW, this.mediaPreviewService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.MEDIA_CONVERT, this.mediaConversionService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.IMAGE_CONVERT, this.imageProcessingService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.OFFICE_CONVERT, this.officeConversionService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.DOCUMENT_PREVIEW, this.documentPreviewService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.METADATA_EXPORT, this.metadataProcessingService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.VIEWER_RESOLVE, this.viewerResolveService.isAvailable());
		availabilityByJobType.put(ProcessingJobType.EDITOR_PROCESS, this.editorProcessingService.isAvailable());
		return availabilityByJobType;
	}

	public record CapabilityScope(
		String scope,
		String phase,
		List<JobTypeCapability> jobTypes,
		List<String> notes,
		CapabilityMatrixPayloads.ViewerCapabilityMatrix viewerMatrix,
		CapabilityMatrixPayloads.CompressionCapabilityMatrix compressionMatrix,
		CapabilityMatrixPayloads.ConverterCapabilityMatrix converterMatrix,
		CapabilityMatrixPayloads.PdfToolkitCapabilityMatrix pdfToolkitMatrix,
		CapabilityMatrixPayloads.PlatformCapabilityMatrix platformMatrix,
		CapabilityMatrixPayloads.EditorCapabilityMatrix editorMatrix
	) {
	}

	public record JobTypeCapability(
		ProcessingJobType jobType,
		boolean implemented,
		String detail
	) {
	}

}
