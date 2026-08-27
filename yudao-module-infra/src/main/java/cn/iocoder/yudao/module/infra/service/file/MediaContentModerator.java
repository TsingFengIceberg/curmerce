package cn.iocoder.yudao.module.infra.service.file;

public interface MediaContentModerator {

    MediaModerationDecision moderate(byte[] content, String mimeType, String sha256);
}
