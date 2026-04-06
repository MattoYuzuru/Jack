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
			"Merge PDFs",
			"Объединяет несколько PDF в один документ с сохранением page order по очереди входов.",
			List.of("Merge", "Multi-source", "Page stack"),
			true,
			false,
			false,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"PDF merge требует доступного backend PDF_TOOLKIT job."
		),
		new PdfOperationSpec(
			"split",
			"Split PDF",
			"Разрезает PDF по заданным диапазонам страниц и отдаёт bundle как ZIP плюс preview первого part-файла.",
			List.of("Ranges", "ZIP", "Preview"),
			false,
			true,
			true,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"PDF split требует доступного backend PDF_TOOLKIT job."
		),
		new PdfOperationSpec(
			"rotate",
			"Rotate pages",
			"Поворачивает все или выбранные страницы на 90/180/270 градусов без client-side page mutation.",
			List.of("Rotate", "Pages", "Layout"),
			false,
			true,
			false,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"PDF rotate требует доступного backend PDF_TOOLKIT job."
		),
		new PdfOperationSpec(
			"reorder",
			"Extract / reorder",
			"Собирает новый PDF из выбранного page order, поэтому одним действием закрывает и extract, и reorder сценарии.",
			List.of("Reorder", "Extract", "Subset"),
			false,
			true,
			false,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"Page extract/reorder требует доступного backend PDF_TOOLKIT job."
		),
		new PdfOperationSpec(
			"ocr",
			"OCR",
			"Рендерит page images и собирает searchable PDF + TXT export поверх Tesseract runtime.",
			List.of("OCR", "Searchable PDF", "TXT"),
			false,
			false,
			false,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"OCR route требует доступного backend PDF_TOOLKIT job и OCR runtime."
		),
		new PdfOperationSpec(
			"sign",
			"E-sign / stamp",
			"Добавляет видимую signature/stamp-mark разметку как typed или image stamp и явно не притворяется certificate-based digital signature.",
			List.of("Stamp", "Signature", "Visible mark"),
			false,
			true,
			false,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"E-sign route требует доступного backend PDF_TOOLKIT job."
		),
		new PdfOperationSpec(
			"redact",
			"Redact terms",
			"Ищет чувствительные термы в text layer и собирает новый rasterized PDF с необратимой term-based redaction.",
			List.of("Redact", "Privacy", "Rasterized"),
			false,
			true,
			false,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"Redaction route требует доступного backend PDF_TOOLKIT job."
		),
		new PdfOperationSpec(
			"protect",
			"Protect PDF",
			"Навешивает user/owner password и базовые permission-флаги на итоговый PDF.",
			List.of("Protect", "Passwords", "Permissions"),
			false,
			false,
			false,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"Protect route требует доступного backend PDF_TOOLKIT job."
		),
		new PdfOperationSpec(
			"unlock",
			"Unlock PDF",
			"Снимает password protection и сохраняет чистый PDF artifact без дальнейших browser-side обходов.",
			List.of("Unlock", "Decryption", "Reuse"),
			false,
			false,
			false,
			true,
			List.of(ProcessingJobType.PDF_TOOLKIT),
			"Unlock route требует доступного backend PDF_TOOLKIT job."
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
				"PDF workspace",
				"Прямой PDF intake идёт в pdf-toolkit workspace с preview через VIEWER_RESOLVE и дальнейшими edit/protect flows через PDF_TOOLKIT.",
				List.of("PDF", "Preview", "Edit"),
				allRequiredJobsAvailable(
					List.of(ProcessingJobType.PDF_TOOLKIT, ProcessingJobType.DOCUMENT_PREVIEW, ProcessingJobType.VIEWER_RESOLVE),
					availabilityByJobType
				),
				"Прямой PDF intake требует доступных PDF_TOOLKIT, DOCUMENT_PREVIEW и VIEWER_RESOLVE capabilities.",
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
					"Этот источник не редактируется как PDF напрямую: pdf-toolkit сначала запускает existing converter path до PDF, а уже потом переводит результат в page-aware workspace.",
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
				? "OCR operation требует доступного `tesseract` runtime в backend окружении."
				: spec.unavailableDetail();

		return new CapabilityMatrixPayloads.PdfToolkitOperationCapability(
			spec.id(),
			spec.label(),
			spec.detail(),
			finalAvailable ? "PDF toolkit operation" : "Capability unavailable",
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
			return "IMAGE_CONVERT -> PDF";
		}
		if (requiredJobTypes.contains(ProcessingJobType.OFFICE_CONVERT)) {
			return "OFFICE_CONVERT -> PDF";
		}
		return "Converter -> PDF";
	}

	private List<String> buildImportAccents(List<String> sourceAccents) {
		var accents = new ArrayList<String>();
		accents.add("Import");
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
