package cn.iocoder.yudao.module.commerce.service.personal;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.commerce.controller.app.personal.vo.*;

public interface PersonalListingService {
    Long create(Long userId, PersonalListingCreateReqVO reqVO);
    void update(Long userId, PersonalListingUpdateReqVO reqVO);
    PersonalListingRespVO get(Long userId, Long id);
    PageResult<PersonalListingRespVO> page(Long userId, PersonalListingPageReqVO reqVO);
    void submit(Long userId, Long id);
    void list(Long userId, Long id);
    void delist(Long userId, Long id);
}
