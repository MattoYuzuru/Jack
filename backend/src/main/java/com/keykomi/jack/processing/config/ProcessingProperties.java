package com.keykomi.jack.processing.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jack.processing")
public class ProcessingProperties {

	private Path storageRoot = Path.of(System.getProperty("java.io.tmpdir"), "jack-processing");
	private long maxUploadSizeBytes = 67_108_864L;
	private int maxConcurrentJobs = 4;
	private int jobQueueCapacity = 16;
	private int publicRequestsPerMinute = 30;
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
	private long cleanupIntervalMillis = 1_800_000L;
	private long uploadRetentionHours = 24L;
	private long artifactRetentionHours = 24L;
	private long jobRetentionHours = 24L;
	private int maxConcurrentJobsPerSession = 2;
	private long maxStorageBytesPerSession = 268_435_456L;
	private String sessionCookieName = "JACK_SESSION";
	private String sessionSecret = "jack-local-development-secret-change-in-production";
	private boolean sessionCookieSecure;
	private String policyVersion = "jack-processing-2";
	private String fixedSessionOwner = "";
	private long maxDecodedPixels = 40_000_000L;
	private int maxDocumentPages = 500;
	private int maxTableRows = 20_000;
	private long maxTableCells = 400_000L;
	private int maxArchiveEntries = 2_048;
	private long maxArchiveExpandedBytes = 134_217_728L;
	private int maxArchiveExpansionRatio = 100;
	private int maxArchiveDepth = 1;
	private long maxProcessOutputBytes = 1_048_576L;
	private long maxResultBytes = 134_217_728L;

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

	public int getMaxConcurrentJobs() {
		return this.maxConcurrentJobs;
	}

	public void setMaxConcurrentJobs(int maxConcurrentJobs) {
		this.maxConcurrentJobs = maxConcurrentJobs;
	}

	public int getJobQueueCapacity() {
		return this.jobQueueCapacity;
	}

	public void setJobQueueCapacity(int jobQueueCapacity) {
		this.jobQueueCapacity = jobQueueCapacity;
	}

	public int getPublicRequestsPerMinute() {
		return this.publicRequestsPerMinute;
	}

	public void setPublicRequestsPerMinute(int publicRequestsPerMinute) {
		this.publicRequestsPerMinute = publicRequestsPerMinute;
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

	public long getCleanupIntervalMillis() {
		return this.cleanupIntervalMillis;
	}

	public void setCleanupIntervalMillis(long cleanupIntervalMillis) {
		this.cleanupIntervalMillis = cleanupIntervalMillis;
	}

	public long getUploadRetentionHours() {
		return this.uploadRetentionHours;
	}

	public void setUploadRetentionHours(long uploadRetentionHours) {
		this.uploadRetentionHours = uploadRetentionHours;
	}

	public long getArtifactRetentionHours() {
		return this.artifactRetentionHours;
	}

	public void setArtifactRetentionHours(long artifactRetentionHours) {
		this.artifactRetentionHours = artifactRetentionHours;
	}

	public long getJobRetentionHours() {
		return this.jobRetentionHours;
	}

	public void setJobRetentionHours(long jobRetentionHours) {
		this.jobRetentionHours = jobRetentionHours;
	}

	public int getMaxConcurrentJobsPerSession() {
		return this.maxConcurrentJobsPerSession;
	}

	public void setMaxConcurrentJobsPerSession(int maxConcurrentJobsPerSession) {
		this.maxConcurrentJobsPerSession = maxConcurrentJobsPerSession;
	}

	public long getMaxStorageBytesPerSession() {
		return this.maxStorageBytesPerSession;
	}

	public void setMaxStorageBytesPerSession(long maxStorageBytesPerSession) {
		this.maxStorageBytesPerSession = maxStorageBytesPerSession;
	}

	public String getSessionCookieName() {
		return this.sessionCookieName;
	}

	public void setSessionCookieName(String sessionCookieName) {
		this.sessionCookieName = sessionCookieName;
	}

	public String getSessionSecret() {
		return this.sessionSecret;
	}

	public void setSessionSecret(String sessionSecret) {
		this.sessionSecret = sessionSecret;
	}

	public boolean isSessionCookieSecure() {
		return this.sessionCookieSecure;
	}

	public void setSessionCookieSecure(boolean sessionCookieSecure) {
		this.sessionCookieSecure = sessionCookieSecure;
	}

	public String getPolicyVersion() {
		return this.policyVersion;
	}

	public void setPolicyVersion(String policyVersion) {
		this.policyVersion = policyVersion;
	}

	public String getFixedSessionOwner() {
		return this.fixedSessionOwner;
	}

	public void setFixedSessionOwner(String fixedSessionOwner) {
		this.fixedSessionOwner = fixedSessionOwner;
	}

	public long getMaxDecodedPixels() {
		return this.maxDecodedPixels;
	}

	public void setMaxDecodedPixels(long maxDecodedPixels) {
		this.maxDecodedPixels = maxDecodedPixels;
	}

	public int getMaxDocumentPages() {
		return this.maxDocumentPages;
	}

	public void setMaxDocumentPages(int maxDocumentPages) {
		this.maxDocumentPages = maxDocumentPages;
	}

	public int getMaxTableRows() {
		return this.maxTableRows;
	}

	public void setMaxTableRows(int maxTableRows) {
		this.maxTableRows = maxTableRows;
	}

	public long getMaxTableCells() {
		return this.maxTableCells;
	}

	public void setMaxTableCells(long maxTableCells) {
		this.maxTableCells = maxTableCells;
	}

	public int getMaxArchiveEntries() {
		return this.maxArchiveEntries;
	}

	public void setMaxArchiveEntries(int maxArchiveEntries) {
		this.maxArchiveEntries = maxArchiveEntries;
	}

	public long getMaxArchiveExpandedBytes() {
		return this.maxArchiveExpandedBytes;
	}

	public void setMaxArchiveExpandedBytes(long maxArchiveExpandedBytes) {
		this.maxArchiveExpandedBytes = maxArchiveExpandedBytes;
	}

	public int getMaxArchiveExpansionRatio() {
		return this.maxArchiveExpansionRatio;
	}

	public void setMaxArchiveExpansionRatio(int maxArchiveExpansionRatio) {
		this.maxArchiveExpansionRatio = maxArchiveExpansionRatio;
	}

	public int getMaxArchiveDepth() {
		return this.maxArchiveDepth;
	}

	public void setMaxArchiveDepth(int maxArchiveDepth) {
		this.maxArchiveDepth = maxArchiveDepth;
	}

	public long getMaxProcessOutputBytes() {
		return this.maxProcessOutputBytes;
	}

	public void setMaxProcessOutputBytes(long maxProcessOutputBytes) {
		this.maxProcessOutputBytes = maxProcessOutputBytes;
	}

	public long getMaxResultBytes() {
		return this.maxResultBytes;
	}

	public void setMaxResultBytes(long maxResultBytes) {
		this.maxResultBytes = maxResultBytes;
	}

}
