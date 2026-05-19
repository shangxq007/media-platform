package com.example.platform.render.infrastructure;

import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.util.Map;

@Service
public class JavaCVTranscodeService {

    private static final Logger log = LoggerFactory.getLogger(JavaCVTranscodeService.class);

    private final MediaProbeService mediaProbeService;

    @Value("${app.storage.local-root:/tmp/platform}")
    private String storageRoot;

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public JavaCVTranscodeService(MediaProbeService mediaProbeService) {
        this.mediaProbeService = mediaProbeService;
    }

    public MediaProbeService getMediaProbeService() {
        return mediaProbeService;
    }

    /**
     * Transcode a single video file.
     */
    public void transcode(String jobId, String inputPath, String outputPath, RenderPreset preset) throws Exception {
        log.info("JavaCVTranscodeService: transcoding job={} preset={}", jobId, preset.key());

        validateInputFile(inputPath);

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath)) {
            grabber.start();

            int width = preset.width();
            int height = preset.height();

            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, width, height)) {
                recorder.setFormat("mp4");
                recorder.setVideoCodec(mapCodecId(preset.videoCodec()));
                recorder.setFrameRate(preset.frameRate());
                recorder.setVideoBitrate(preset.videoBitrateKbps() * 1000);
                recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
                recorder.setAudioChannels(grabber.getAudioChannels() > 0 ? grabber.getAudioChannels() : 2);
                recorder.setSampleRate(grabber.getSampleRate() > 0 ? grabber.getSampleRate() : 44100);
                recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
                recorder.start();

                Frame frame;
                long frameCount = 0;
                while ((frame = grabber.grabImage()) != null) {
                    recorder.record(frame);
                    frameCount++;
                }

                recorder.stop();
                log.info("JavaCVTranscodeService: transcoded {} frames job={}", frameCount, jobId);
            }

            grabber.stop();
        }

        validateOutputFile(outputPath);
        log.info("JavaCVTranscodeService: transcode complete job={} output={}", jobId, outputPath);
    }

    /**
     * Extract a thumbnail from a video at the given timestamp.
     */
    public void extractThumbnail(String jobId, String inputPath, String outputPath,
                                  double timeOffset, int width) throws Exception {
        log.info("JavaCVTranscodeService: extracting thumbnail job={} time={}s", jobId, timeOffset);

        validateInputFile(inputPath);

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(inputPath)) {
            grabber.start();

            long targetFrame = (long) (timeOffset * grabber.getFrameRate());
            grabber.setVideoFrameNumber((int) targetFrame);

            Frame frame = grabber.grabImage();
            if (frame == null) {
                throw new PlatformException(
                        new ConfigurableErrorCode("RENDER-400-001", 500401,
                                Map.of("en", "Failed to extract frame at time offset", "zh", "无法提取指定时间偏移的帧"),
                                "render", 400),
                        "No frame at time offset: " + timeOffset,
                        Map.of("jobId", jobId, "timeOffset", String.valueOf(timeOffset)),
                        "en"
                );
            }

            // Scale the frame
            int origWidth = grabber.getImageWidth();
            int origHeight = grabber.getImageHeight();
            int height = (int) ((double) width / origWidth * origHeight);

            try (FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, width, height)) {
                recorder.setFormat("jpg");
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_MJPEG);
                recorder.setFrameRate(1);
                recorder.setVideoBitrate(5000000);
                recorder.start();
                recorder.record(frame);
                recorder.stop();
            }

            grabber.stop();
        }

        validateOutputFile(outputPath);
        log.info("JavaCVTranscodeService: thumbnail extracted job={}", jobId);
    }

    private void validateInputFile(String inputPath) {
        File inputFile = new File(inputPath);
        if (!inputFile.exists()) {
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-404-001", 500404,
                            Map.of("en", "Input file not found", "zh", "输入文件不存在"),
                            "render", 404),
                    "Input file not found: " + inputPath,
                    Map.of("inputPath", inputPath),
                    "en"
            );
        }
        if (!inputFile.canRead()) {
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-403-001", 500403,
                            Map.of("en", "Cannot read input file", "zh", "无法读取输入文件"),
                            "render", 403),
                    "Cannot read input file: " + inputPath,
                    Map.of("inputPath", inputPath),
                    "en"
            );
        }
    }

    private void validateOutputFile(String outputPath) {
        File outputFile = new File(outputPath);
        if (!outputFile.exists() || outputFile.length() == 0) {
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-002", 500502,
                            Map.of("en", "Output file generation failed", "zh", "输出文件生成失败"),
                            "render", 500),
                    "Output file is empty or missing: " + outputPath,
                    Map.of("outputPath", outputPath),
                    "en"
            );
        }
    }

    private int mapCodecId(String codec) {
        return switch (codec) {
            case "libx264" -> avcodec.AV_CODEC_ID_H264;
            case "libx265" -> avcodec.AV_CODEC_ID_HEVC;
            case "libvpx-vp9" -> avcodec.AV_CODEC_ID_VP9;
            case "h264_nvenc" -> avcodec.AV_CODEC_ID_H264;
            case "hevc_nvenc" -> avcodec.AV_CODEC_ID_HEVC;
            case "vp9_vaapi" -> avcodec.AV_CODEC_ID_VP9;
            default -> avcodec.AV_CODEC_ID_H264;
        };
    }

}
