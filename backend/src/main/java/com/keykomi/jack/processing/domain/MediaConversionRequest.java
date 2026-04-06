package com.keykomi.jack.processing.domain;

public record MediaConversionRequest(
	String targetExtension,
	String videoCodec,
	String audioCodec,
	Integer maxWidth,
	Integer maxHeight,
	Integer targetFps,
	Integer videoBitrateKbps,
	Integer audioBitrateKbps,
	String presetLabel
) {
}
