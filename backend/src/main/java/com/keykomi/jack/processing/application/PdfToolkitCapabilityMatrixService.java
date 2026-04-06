package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.CapabilityMatrixPayloads;
import com.keykomi.jack.processing.domain.ProcessingJobType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PdfToolkitCapabilityMatrixService {

	private static final List<PdfOperationSpec> OPERATION_SPECS = List.of(
		new PdfOperationSpec(
			"merge",
			"Объединить PDF",
			"Собирает несколько PDF в один документ и сохраняет порядок страниц по очереди добавления.",
			List.of("Объединение", "Несколько файлов", "Страницы"),
			true,
			false,
			false,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"Операция объединения сейчас недоступна."
		),
		new PdfOperationSpec(
			"split",
			"Разделить PDF",
			"Разбивает документ по указанным диапазонам страниц и сохраняет результат в архив.",
			List.of("Диапазоны", "Архив", "Фрагменты"),
			false,
			true,
			true,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"Операция разделения сейчас недоступна."
		),
		new PdfOperationSpec(
			"rotate",
			"Повернуть страницы",
			"Поворачивает все или выбранные страницы на 90, 180 или 270 градусов.",
			List.of("Поворот", "Страницы", "Ориентация"),
			false,
			true,
			false,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"Операция поворота сейчас недоступна."
		),
		new PdfOperationSpec(
			"reorder",
			"Выбрать и переставить",
			"Собирает новый PDF из выбранных страниц в нужном порядке, поэтому одним действием закрывает и выборку, и перестановку.",
			List.of("Порядок", "Выборка", "Подмножество"),
			false,
			true,
			false,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"Операция выбора и перестановки сейчас недоступна."
		),
		new PdfOperationSpec(
			"ocr",
			"OCR",
			"Распознаёт текст на страницах и добавляет в документ поисковый слой вместе с текстовой выгрузкой.",
			List.of("OCR", "Поиск по тексту", "TXT"),
			false,
			false,
			false,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"OCR сейчас недоступен."
		),
		new PdfOperationSpec(
			"sign",
			"Подпись и штамп",
			"Добавляет видимую подпись или штамп в документ как текстовую или графическую отметку.",
			List.of("Подпись", "Штамп", "Отметка"),
			false,
			true,
			false,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"Операция подписи сейчас недоступна."
		),
		new PdfOperationSpec(
			"redact",
			"Скрыть фрагменты",
			"Ищет чувствительные слова в текстовом слое и собирает новый PDF с необратимым скрытием найденных фрагментов.",
			List.of("Скрытие", "Конфиденциальность", "Защита"),
			false,
			true,
			false,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"Операция скрытия сейчас недоступна."
		),
		new PdfOperationSpec(
			"protect",
			"Защитить PDF",
			"Добавляет пароль на открытие и базовые ограничения на действия с документом.",
			List.of("Пароль", "Доступ", "Ограничения"),
			false,
			false,
			false,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"Операция защиты сейчас недоступна."
		),
		new PdfOperationSpec(
			"unlock",
			"Снять защиту",
			"Снимает парольную защиту и сохраняет чистую копию PDF для дальнейшей работы.",
			List.of("Пароль", "Разблокировка", "Продолжить работу"),
			false,
			false,
			false,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"Операция снятия защиты сейчас недоступна."
		)
	);

	private final CapabilityMatrixService capabilityMatrixService;

	public PdfToolkitCapabilityMatrixService(CapabilityMatrixService capabilityMatrixService) {
		this.capabilityMatrixService = capabilityMatrixService;
	}

	public CapabilityMatrixPayloads.PdfToolkitCapabilityMatrix pdfToolkitMatrix(
		Map<ProcessingJobType, Boolean> availabilityByJobType,
		boolean ocrRuntimeAvailable
	) {
		var directFormats = List.of(
			new CapabilityMatrixPayloads.PdfToolkitSourceCapability(
				"pdf",
				List.of(),
				"PDF",
				"document",
				"direct-pdf",
				"Прямое открытие PDF",
				"Документ можно сразу открыть в рабочем окне, просмотреть страницы и перейти к операциям без дополнительной подготовки.",
				List.of("PDF", "Просмотр", "Операции"),
				allRequiredJobsAvailable(
					List.of(ProcessingJobType.PDF_TOOLKIT, ProcessingJobType.DOCUMENT_PREVIEW, ProcessingJobType.VIEWER_RESOLVE),
					availabilityByJobType
				),
				"Прямое открытие PDF сейчас недоступно.",
				List.of(ProcessingJobType.PDF_TOOLKIT, ProcessingJobType.DOCUMENT_PREVIEW, ProcessingJobType.VIEWER_RESOLVE)
			)
		);

		var converterMatrix = this.capabilityMatrixService.converterMatrix(availabilityByJobType);
		var sourceByExtension = new LinkedHashMap<String, CapabilityMatrixPayloads.ConverterSourceCapability>();
		for (var source : converterMatrix.sourceFormats()) {
			sourceByExtension.putIfAbsent(source.extension(), source);
		}

		var importFormats = new ArrayList<CapabilityMatrixPayloads.PdfToolkitSourceCapability>();
		var seenImportExtensions = new LinkedHashSet<String>();
		for (var scenario : converterMatrix.scenarios()) {
			if (!"pdf".equals(scenario.targetExtension()) || !scenario.available()) {
				continue;
			}

			var source = sourceByExtension.get(scenario.sourceExtension());
			if (source == null || !source.available() || !seenImportExtensions.add(source.extension())) {
				continue;
			}

			importFormats.add(
				new CapabilityMatrixPayloads.PdfToolkitSourceCapability(
					source.extension(),
					source.aliases(),
					source.label(),
					source.family(),
					"convert-to-pdf",
					resolveImportRouteLabel(source.requiredJobTypes()),
					"Этот формат сначала будет аккуратно приведён к PDF, после чего с документом можно продолжить работу в том же окне.",
					buildImportAccents(source.accents()),
					true,
					null,
					source.requiredJobTypes()
				)
			);
		}

		var operations = OPERATION_SPECS.stream()
			.map(spec -> toOperation(spec, availabilityByJobType, ocrRuntimeAvailable))
			.toList();

		return new CapabilityMatrixPayloads.PdfToolkitCapabilityMatrix(
			buildAcceptAttribute(directFormats),
			buildAcceptAttribute(importFormats),
			directFormats,
			importFormats,
			operations
		);
	}

	private CapabilityMatrixPayloads.PdfToolkitOperationCapability toOperation(
		PdfOperationSpec spec,
		Map<ProcessingJobType, Boolean> availabilityByJobType,
		boolean ocrRuntimeAvailable
	) {
		var available = allRequiredJobsAvailable(spec.requiredJobTypes(), availabilityByJobType);
		var ocrBlocked = "ocr".equals(spec.id()) && !ocrRuntimeAvailable;
		var finalAvailable = available && !ocrBlocked;
		var availabilityDetail = finalAvailable
			? null
			: ocrBlocked
			? "OCR недоступен, потому что в текущем окружении не найден движок распознавания текста."
				: spec.unavailableDetail();

		return new CapabilityMatrixPayloads.PdfToolkitOperationCapability(
			spec.id(),
			spec.label(),
			spec.detail(),
			finalAvailable ? "Готово к запуску" : "Недоступно",
			spec.accents(),
			finalAvailable,
			availabilityDetail,
			spec.supportsMultiSource(),
			spec.requiresPageSelection(),
			spec.producesArchive(),
			spec.producesPreviewPdf(),
			spec.requiredJobTypes()
		);
	}

	private String resolveImportRouteLabel(List<ProcessingJobType> requiredJobTypes) {
		if (requiredJobTypes.contains(ProcessingJobType.IMAGE_CONVERT)) {
			return "Подготовка изображения в PDF";
		}
		if (requiredJobTypes.contains(ProcessingJobType.OFFICE_CONVERT)) {
			return "Подготовка документа в PDF";
		}
		return "Подготовка в PDF";
	}

	private List<String> buildImportAccents(List<String> sourceAccents) {
		var accents = new ArrayList<String>();
		accents.add("Импорт");
		accents.add("PDF");
		for (String accent : sourceAccents) {
			if (accents.size() >= 4) {
				break;
			}
			if (!accents.contains(accent)) {
				accents.add(accent);
			}
		}
		return List.copyOf(accents);
	}

	private String buildAcceptAttribute(List<CapabilityMatrixPayloads.PdfToolkitSourceCapability> formats) {
		var extensions = new LinkedHashSet<String>();
		for (var format : formats) {
			if (!format.available()) {
				continue;
			}
			extensions.add("." + format.extension());
			for (String alias : format.aliases()) {
				extensions.add("." + alias);
			}
		}
		return String.join(",", extensions);
	}

	private boolean allRequiredJobsAvailable(
		List<ProcessingJobType> requiredJobTypes,
		Map<ProcessingJobType, Boolean> availabilityByJobType
	) {
		if (requiredJobTypes.isEmpty()) {
			return true;
		}
		for (var requiredJobType : requiredJobTypes) {
			if (!availabilityByJobType.getOrDefault(requiredJobType, false)) {
				return false;
			}
		}
		return true;
	}

	private record PdfOperationSpec(
		String id,
		String label,
		String detail,
		List<String> accents,
		boolean supportsMultiSource,
		boolean requiresPageSelection,
		boolean producesArchive,
		boolean producesPreviewPdf,
		List<ProcessingJobType> requiredJobTypes,
		String unavailableDetail
	) {
	}

}
