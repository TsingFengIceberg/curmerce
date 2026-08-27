package cn.iocoder.yudao.module.infra.service.file;

import cn.iocoder.yudao.module.infra.dal.dataobject.file.FileDO;

public record MediaAssetContent(FileDO file, byte[] content) {
}
