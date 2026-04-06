package com.keykomi.jack.processing.application;

import com.keykomi.jack.processing.domain.CapabilityMatrixPayloads;
import com.keykomi.jack.processing.domain.ProcessingJobType;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EditorCapabilityMatrixService {

	public CapabilityMatrixPayloads.EditorCapabilityMatrix editorMatrix(
		Map<ProcessingJobType, Boolean> availabilityByJobType
	) {
		var formats = EditorFormatCatalog.FORMAT_SPECS.stream()
			.map(spec -> new CapabilityMatrixPayloads.EditorFormatCapability(
				spec.id(),
				spec.label(),
				spec.extensions(),
				spec.mimeTypes(),
				spec.syntaxMode(),
				spec.previewMode(),
				spec.supportsFormatting(),
				spec.supportsPlainTextExport(),
				"Editor format",
				spec.notes(),
				spec.accents(),
				isAvailable(spec.requiredJobTypes(), availabilityByJobType),
				resolveAvailabilityDetail(spec.requiredJobTypes(), availabilityByJobType),
				spec.requiredJobTypes()
			))
			.toList();

		return new CapabilityMatrixPayloads.EditorCapabilityMatrix(
			EditorFormatCatalog.buildAcceptAttribute(),
			formats
		);
	}

	private boolean isAvailable(
		List<ProcessingJobType> requiredJobTypes,
		Map<ProcessingJobType, Boolean> availabilityByJobType
	) {
		return requiredJobTypes.stream().allMatch(jobType -> availabilityByJobType.getOrDefault(jobType, false));
	}

	private String resolveAvailabilityDetail(
		List<ProcessingJobType> requiredJobTypes,
		Map<ProcessingJobType, Boolean> availabilityByJobType
	) {
		if (isAvailable(requiredJobTypes, availabilityByJobType)) {
			return null;
		}

		return "Формат требует доступных backend capabilities: %s."
			.formatted(requiredJobTypes.stream().map(Enum::name).toList());
	}

}
