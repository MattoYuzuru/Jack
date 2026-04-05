package com.keykomi.jack.processing.domain;

public record MetadataProcessingRequest(
	Operation operation,
	MetadataPayloads.EditableMetadata editableMetadata
) {

	public enum Operation {
		INSPECT_IMAGE,
		INSPECT_AUDIO,
		EXPORT_IMAGE
	}

}
