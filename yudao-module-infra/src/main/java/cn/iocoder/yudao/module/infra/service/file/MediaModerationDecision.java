package cn.iocoder.yudao.module.infra.service.file;

public record MediaModerationDecision(Status status, String reason) {

    public enum Status {
        SAFE,
        REVIEW,
        REJECT,
        ERROR
    }

    public static MediaModerationDecision safe() {
        return new MediaModerationDecision(Status.SAFE, null);
    }

    public static MediaModerationDecision error(String reason) {
        return new MediaModerationDecision(Status.ERROR, reason);
    }
}
