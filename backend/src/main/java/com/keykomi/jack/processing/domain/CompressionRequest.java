package com.keykomi.jack.processing.domain;

public record CompressionRequest(
	Mode mode,
	Long targetSizeBytes,
	String targetExtension,
	Integer maxWidth,
	Integer maxHeight,
	Double quality,
	String backgroundColor,
	Integer targetFps,
	Integer videoBitrateKbps,
	Integer audioBitrateKbps,
	String presetLabel
) {

	public enum Mode {
		MAX_REDUCTION,
		TARGET_SIZE,
		CUSTOM
	}

}
