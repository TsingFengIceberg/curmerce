package cn.iocoder.yudao.module.commerce.controller.app.cart.vo;
import lombok.Data;
import java.util.List;
@Data public class CartListRespVO { private List<CartItemRespVO> validList; private List<CartItemRespVO> invalidList; }
