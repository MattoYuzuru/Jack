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
	private String imageConvertExecutable = "convert";
	private String potraceExecutable = "potrace";
	private String rawPreviewExecutable = "dcraw_emu";
	private long imageProcessingTimeoutSeconds = 240L;
	private String tesseractExecutable = "tesseract";
	private long pdfToolkitTimeoutSeconds = 300L;
	private String pdfToolkitDefaultOcrLanguage = "eng";

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

	public String getImageConvertExecutable() {
		return this.imageConvertExecutable;
	}

	public void setImageConvertExecutable(String imageConvertExecutable) {
		this.imageConvertExecutable = imageConvertExecutable;
	}

	public String getPotraceExecutable() {
		return this.potraceExecutable;
	}

	public void setPotraceExecutable(String potraceExecutable) {
		this.potraceExecutable = potraceExecutable;
	}

	public String getRawPreviewExecutable() {
		return this.rawPreviewExecutable;
	}

	public void setRawPreviewExecutable(String rawPreviewExecutable) {
		this.rawPreviewExecutable = rawPreviewExecutable;
	}

	public long getImageProcessingTimeoutSeconds() {
		return this.imageProcessingTimeoutSeconds;
	}

	public void setImageProcessingTimeoutSeconds(long imageProcessingTimeoutSeconds) {
		this.imageProcessingTimeoutSeconds = imageProcessingTimeoutSeconds;
	}

	public String getTesseractExecutable() {
		return this.tesseractExecutable;
	}

	public void setTesseractExecutable(String tesseractExecutable) {
		this.tesseractExecutable = tesseractExecutable;
	}

	public long getPdfToolkitTimeoutSeconds() {
		return this.pdfToolkitTimeoutSeconds;
	}

	public void setPdfToolkitTimeoutSeconds(long pdfToolkitTimeoutSeconds) {
		this.pdfToolkitTimeoutSeconds = pdfToolkitTimeoutSeconds;
	}

	public String getPdfToolkitDefaultOcrLanguage() {
		return this.pdfToolkitDefaultOcrLanguage;
	}

	public void setPdfToolkitDefaultOcrLanguage(String pdfToolkitDefaultOcrLanguage) {
		this.pdfToolkitDefaultOcrLanguage = pdfToolkitDefaultOcrLanguage;
	}

}
