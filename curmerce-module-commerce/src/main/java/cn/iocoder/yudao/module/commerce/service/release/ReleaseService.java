package cn.iocoder.yudao.module.commerce.service.release;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.admin.release.vo.ReleaseCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.release.vo.ReleasePageReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseReqVO;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePurchaseRespVO;
import cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleaseRespVO;
import java.time.LocalDateTime;

public interface ReleaseService {
    Long create(ReleaseCreateReqVO reqVO);
    PageResult<ReleaseRespVO> getOwnPage(ReleasePageReqVO reqVO);
    PageResult<ReleaseRespVO> getPublicPage(cn.iocoder.yudao.module.commerce.controller.app.release.vo.ReleasePageReqVO reqVO);
    ReleaseRespVO get(Long id, boolean publicOnly);
    void publish(Long id);
    void cancel(Long id);
    void finish(Long id);
    ReleasePurchaseRespVO purchase(Long userId, ReleasePurchaseReqVO reqVO);
    int advanceStatuses(LocalDateTime now);
}
