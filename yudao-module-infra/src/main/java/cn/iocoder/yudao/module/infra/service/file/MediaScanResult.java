package cn.iocoder.yudao.module.infra.service.file;

public record MediaScanResult(Status status, String detail) {

    public enum Status { CLEAN, REJECTED, SKIPPED, ERROR }

    public static MediaScanResult clean() {
        return new MediaScanResult(Status.CLEAN, null);
    }

    public static MediaScanResult rejected(String detail) {
        return new MediaScanResult(Status.REJECTED, detail);
    }

    public static MediaScanResult skipped(String detail) {
        return new MediaScanResult(Status.SKIPPED, detail);
    }

    public static MediaScanResult error(String detail) {
        return new MediaScanResult(Status.ERROR, detail);
    }
}
