package com.keykomi.jack.processing.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jack.processing")
public class ProcessingProperties {

	private Path storageRoot = Path.of(System.getProperty("java.io.tmpdir"), "jack-processing");
	private long maxUploadSizeBytes = 268_435_456L;
	private String ffmpegExecutable = "ffmpeg";
	private String ffprobeExecutable = "ffprobe";
	private long mediaPreviewTimeoutSeconds = 240L;

	public Path getStorageRoot() {
		return this.storageRoot;
	}

	public void setStorageRoot(Path storageRoot) {
		this.storageRoot = storageRoot;
	}

	public long getMaxUploadSizeBytes() {
		return this.maxUploadSizeBytes;
	}

	public void setMaxUploadSizeBytes(long maxUploadSizeBytes) {
		this.maxUploadSizeBytes = maxUploadSizeBytes;
	}

	public Path uploadsDirectory() {
		return this.storageRoot.resolve("uploads");
	}

	public Path artifactsDirectory() {
		return this.storageRoot.resolve("artifacts");
	}

	public String getFfmpegExecutable() {
		return this.ffmpegExecutable;
	}

	public void setFfmpegExecutable(String ffmpegExecutable) {
		this.ffmpegExecutable = ffmpegExecutable;
	}

	public String getFfprobeExecutable() {
		return this.ffprobeExecutable;
	}

	public void setFfprobeExecutable(String ffprobeExecutable) {
		this.ffprobeExecutable = ffprobeExecutable;
	}

	public long getMediaPreviewTimeoutSeconds() {
		return this.mediaPreviewTimeoutSeconds;
	}

	public void setMediaPreviewTimeoutSeconds(long mediaPreviewTimeoutSeconds) {
		this.mediaPreviewTimeoutSeconds = mediaPreviewTimeoutSeconds;
	}

}
