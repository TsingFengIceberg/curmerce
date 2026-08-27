package cn.iocoder.yudao.module.infra.service.file;

public interface MediaContentScanner {

    MediaScanResult scan(byte[] content);
}
