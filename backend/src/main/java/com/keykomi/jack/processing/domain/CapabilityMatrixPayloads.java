package com.keykomi.jack.processing.domain;

import java.util.List;

public final class CapabilityMatrixPayloads {

	private CapabilityMatrixPayloads() {
	}

	public record ViewerCapabilityMatrix(
		String acceptAttribute,
		List<ViewerFormatCapability> formats
	) {
	}

	public record ViewerFormatCapability(
		String extension,
		List<String> aliases,
		String label,
		String family,
		List<String> mimeTypes,
		String previewPipeline,
		String previewStrategyId,
		String statusLabel,
		String notes,
		List<String> accents,
		boolean available,
		String availabilityDetail,
		List<ProcessingJobType> requiredJobTypes
	) {
	}

	public record ConverterCapabilityMatrix(
		String acceptAttribute,
		List<ConverterSourceCapability> sourceFormats,
		List<ConverterTargetCapability> targetFormats,
		List<ConverterScenarioCapability> scenarios,
		List<ConverterPresetCapability> presets
	) {
	}

	public record ConverterSourceCapability(
		String extension,
		List<String> aliases,
		String label,
		String family,
		List<String> mimeTypes,
		String sourceStrategyId,
		String statusLabel,
		String notes,
		List<String> accents,
		boolean available,
		String availabilityDetail,
		List<ProcessingJobType> requiredJobTypes
	) {
	}

	public record ConverterTargetCapability(
		String extension,
		String label,
		String family,
		String mimeType,
		String targetStrategyId,
		boolean supportsQuality,
		boolean supportsTransparency,
		Double defaultQuality,
		String statusLabel,
		String notes,
		List<String> accents,
		boolean available,
		String availabilityDetail,
		List<ProcessingJobType> requiredJobTypes
	) {
	}

	public record ConverterScenarioCapability(
		String id,
		String family,
		String label,
		String sourceExtension,
		String targetExtension,
		String statusLabel,
		String notes,
		List<String> accents,
		String executionMode,
		boolean available,
		String availabilityDetail,
		List<ProcessingJobType> requiredJobTypes
	) {
	}

	public record ConverterPresetCapability(
		String id,
		String label,
		String detail,
		String statusLabel,
		List<String> accents,
		Integer maxWidth,
		Integer maxHeight,
		Double preferredQuality,
		String defaultBackgroundColor,
		boolean available,
		String availabilityDetail
	) {
	}

	public record PlatformCapabilityMatrix(
		List<PlatformModuleCapability> modules
	) {
	}

	public record PlatformModuleCapability(
		String id,
		String label,
		String summary,
		String detail,
		String statusLabel,
		List<String> accents,
		List<String> reusedDomains,
		List<ProcessingJobType> reusedJobTypes,
		List<String> nextSlices,
		boolean foundationReady,
		String availabilityDetail
	) {
	}

}
