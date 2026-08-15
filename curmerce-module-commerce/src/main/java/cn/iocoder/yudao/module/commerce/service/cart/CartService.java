package cn.iocoder.yudao.module.commerce.service.cart;

import cn.iocoder.yudao.module.commerce.controller.app.cart.vo.*;

public interface CartService {
    Long add(Long userId, CartAddReqVO reqVO);
    void updateQuantity(Long userId, CartQuantityUpdateReqVO reqVO);
    void updateSelected(Long userId, CartSelectionUpdateReqVO reqVO);
    void delete(Long userId, CartBatchReqVO reqVO);
    CartListRespVO list(Long userId);
    Long count(Long userId);
}
