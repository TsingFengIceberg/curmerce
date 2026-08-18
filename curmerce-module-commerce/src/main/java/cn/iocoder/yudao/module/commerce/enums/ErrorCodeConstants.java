package cn.iocoder.yudao.module.commerce.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

public interface ErrorCodeConstants {

    ErrorCode MERCHANT_NOT_EXISTS = new ErrorCode(1_023_001_000, "商家不存在");
    ErrorCode MERCHANT_CODE_DUPLICATE = new ErrorCode(1_023_001_001, "商家编码已存在");
    ErrorCode MERCHANT_DEFAULT_STORE_CODE_DUPLICATE = new ErrorCode(1_023_001_002, "默认店铺编码已存在");
    ErrorCode MERCHANT_NOT_PENDING = new ErrorCode(1_023_001_003, "商家不处于待审核状态");
    ErrorCode MERCHANT_REVIEW_CONFLICT = new ErrorCode(1_023_001_004, "商家审核状态已被其他请求改变");
    ErrorCode MERCHANT_OWNER_PASSWORD_INVALID = new ErrorCode(1_023_001_005, "商家主账号密码长度必须为 8-64 位");
    ErrorCode STORE_CODE_DUPLICATE = new ErrorCode(1_023_001_006, "店铺编码已存在");
    ErrorCode STORE_NOT_EXISTS = new ErrorCode(1_023_001_007, "店铺不存在");
    ErrorCode MERCHANT_OPERATOR_NOT_EXISTS = new ErrorCode(1_023_001_008, "商家操作员关系不存在");
    ErrorCode MERCHANT_OPERATOR_NOT_ACTIVE = new ErrorCode(1_023_001_009, "商家操作员关系已停用");
    ErrorCode MERCHANT_OPERATOR_AMBIGUOUS = new ErrorCode(1_023_001_010, "当前账号关联多个商家，暂不支持自助操作");
    ErrorCode STORE_ACCESS_DENIED = new ErrorCode(1_023_001_011, "无权访问该店铺");

    ErrorCode PRODUCT_CATEGORY_NOT_EXISTS = new ErrorCode(1_023_002_000, "商品分类不存在");
    ErrorCode PRODUCT_CATEGORY_CODE_DUPLICATE = new ErrorCode(1_023_002_001, "商品分类编码已存在或已被占用");
    ErrorCode PRODUCT_CATEGORY_PARENT_NOT_EXISTS = new ErrorCode(1_023_002_002, "商品分类父级不存在");
    ErrorCode PRODUCT_CATEGORY_PARENT_SELF = new ErrorCode(1_023_002_003, "商品分类不能以自身为父级");
    ErrorCode PRODUCT_CATEGORY_PARENT_CYCLE = new ErrorCode(1_023_002_004, "商品分类父子关系存在循环");
    ErrorCode PRODUCT_CATEGORY_ANCESTOR_DISABLED = new ErrorCode(1_023_002_005, "商品分类的父级分类未启用");
    ErrorCode PRODUCT_CATEGORY_ENABLED_DESCENDANT = new ErrorCode(1_023_002_006, "商品分类存在启用中的子分类");
    ErrorCode PRODUCT_CATEGORY_SUBTREE_PRODUCT_ON_SALE = new ErrorCode(1_023_002_007, "商品分类或子分类存在上架商品");
    ErrorCode PRODUCT_CATEGORY_TREE_INVALID = new ErrorCode(1_023_002_008, "商品分类树数据无效");
    ErrorCode PRODUCT_CATEGORY_STATE_CONFLICT = new ErrorCode(1_023_002_009, "商品分类状态已被其他请求改变");

    ErrorCode PRODUCT_NOT_EXISTS_OR_ACCESS_DENIED = new ErrorCode(1_023_003_000, "商品不存在或无权访问");
    ErrorCode PRODUCT_STORE_DISABLED = new ErrorCode(1_023_003_001, "商品所属店铺未启用");
    ErrorCode PRODUCT_CATEGORY_DISABLED = new ErrorCode(1_023_003_002, "商品分类未启用");
    ErrorCode PRODUCT_CODE_DUPLICATE = new ErrorCode(1_023_003_003, "商品编码已存在或已被占用");
    ErrorCode PRODUCT_SKU_CODE_DUPLICATE = new ErrorCode(1_023_003_004, "商品 SKU 编码已存在或已被占用");
    ErrorCode PRODUCT_EDIT_STATE_INVALID = new ErrorCode(1_023_003_005, "当前商品状态不允许编辑");
    ErrorCode PRODUCT_SKU_COUNT_INVALID = new ErrorCode(1_023_003_006, "商品 SKU 数量必须在 1 到 100 之间");
    ErrorCode PRODUCT_SKU_ID_DUPLICATE = new ErrorCode(1_023_003_007, "商品 SKU ID 重复");
    ErrorCode PRODUCT_SKU_ID_INVALID = new ErrorCode(1_023_003_008, "商品 SKU 不属于当前商品");
    ErrorCode PRODUCT_SKU_CODE_REQUEST_DUPLICATE = new ErrorCode(1_023_003_009, "请求中的商品 SKU 编码重复");
    ErrorCode PRODUCT_SKU_CODE_IMMUTABLE = new ErrorCode(1_023_003_010, "已有商品 SKU 编码不可修改");
    ErrorCode PRODUCT_SKU_SPECIFICATION_INVALID = new ErrorCode(1_023_003_011, "商品 SKU 规格值无效");
    ErrorCode PRODUCT_SKU_PRICE_INVALID = new ErrorCode(1_023_003_012, "商品 SKU 划线价不能低于售价");
    ErrorCode PRODUCT_SKU_STATUS_INVALID = new ErrorCode(1_023_003_017, "商品 SKU 状态无效");
    ErrorCode PRODUCT_AUDIT_STATE_INVALID = new ErrorCode(1_023_003_013, "当前商品审核状态不允许该操作");
    ErrorCode PRODUCT_SALE_STATE_INVALID = new ErrorCode(1_023_003_014, "当前商品销售状态不允许该操作");
    ErrorCode PRODUCT_NO_SELLABLE_SKU = new ErrorCode(1_023_003_015, "商品没有可售 SKU 或库存");
    ErrorCode PRODUCT_STATE_CONFLICT = new ErrorCode(1_023_003_016, "商品状态已被其他请求改变");
    ErrorCode CART_SKU_NOT_AVAILABLE = new ErrorCode(1_023_004_100, "商品规格当前不可购买");
    ErrorCode CART_QUANTITY_INVALID = new ErrorCode(1_023_004_101, "购物车数量超出当前库存或上限");
    ErrorCode CART_ITEM_NOT_EXISTS = new ErrorCode(1_023_004_102, "购物车项不存在");

    ErrorCode ORDER_CHECKOUT_EMPTY = new ErrorCode(1_023_005_000, "没有选中的购物车商品");
    ErrorCode ORDER_CHECKOUT_MULTI_STORE = new ErrorCode(1_023_005_001, "一次下单只能包含同一店铺的商品");
    ErrorCode ORDER_ADDRESS_NOT_AVAILABLE = new ErrorCode(1_023_005_002, "收货地址不存在或无权使用");
    ErrorCode ORDER_ITEM_NOT_AVAILABLE = new ErrorCode(1_023_005_003, "订单商品当前不可购买");
    ErrorCode ORDER_STOCK_INSUFFICIENT = new ErrorCode(1_023_005_004, "商品库存不足");
    ErrorCode ORDER_NOT_FOUND = new ErrorCode(1_023_005_005, "订单不存在");
    ErrorCode ORDER_IDEMPOTENCY_KEY_INVALID = new ErrorCode(1_023_005_006, "幂等键格式无效");
    ErrorCode ORDER_AMOUNT_OVERFLOW = new ErrorCode(1_023_005_007, "订单金额超出允许范围");
    ErrorCode ORDER_SHIP_STATE_INVALID = new ErrorCode(1_023_005_008, "当前订单状态不可发货");
    ErrorCode ORDER_SHIPPING_INFO_INVALID = new ErrorCode(1_023_005_009, "物流公司和物流单号不能为空");
    ErrorCode ORDER_RECEIPT_STATE_INVALID = new ErrorCode(1_023_005_010, "当前订单状态不可确认收货");
    ErrorCode ORDER_CANCEL_STATE_INVALID = new ErrorCode(1_023_005_011, "当前订单状态不可取消");
    ErrorCode ORDER_STOCK_RESTORE_FAILED = new ErrorCode(1_023_005_012, "订单库存恢复失败");

    ErrorCode REFUND_ORDER_NOT_REFUNDABLE = new ErrorCode(1_023_007_000, "当前订单不可申请退款");
    ErrorCode REFUND_ALREADY_EXISTS = new ErrorCode(1_023_007_001, "该订单已存在退款记录");
    ErrorCode REFUND_NOT_FOUND = new ErrorCode(1_023_007_002, "退款记录不存在");
    ErrorCode REFUND_STATE_INVALID = new ErrorCode(1_023_007_003, "当前退款状态不允许该操作");
    ErrorCode REFUND_AMOUNT_INVALID = new ErrorCode(1_023_007_004, "退款金额无效");
    ErrorCode REFUND_CALLBACK_ID_INVALID = new ErrorCode(1_023_007_005, "退款回调标识格式无效");
    ErrorCode REFUND_CALLBACK_CONFLICT = new ErrorCode(1_023_007_006, "退款回调重复或内容冲突");
    ErrorCode REFUND_REVIEW_REMARK_INVALID = new ErrorCode(1_023_007_007, "退款审核备注不能为空");

    ErrorCode PAYMENT_METHOD_INVALID = new ErrorCode(1_023_006_000, "当前仅支持模拟支付方式");
    ErrorCode PAYMENT_ORDER_NOT_PAYABLE = new ErrorCode(1_023_006_001, "当前订单状态不可支付");
    ErrorCode PAYMENT_ALREADY_EXISTS = new ErrorCode(1_023_006_002, "订单支付单状态异常");
    ErrorCode PAYMENT_NOT_FOUND = new ErrorCode(1_023_006_003, "支付单不存在");
    ErrorCode PAYMENT_NO_INVALID = new ErrorCode(1_023_006_004, "支付单号格式无效");
    ErrorCode PAYMENT_CALLBACK_ID_INVALID = new ErrorCode(1_023_006_005, "支付回调标识格式无效");
    ErrorCode PAYMENT_AMOUNT_INVALID = new ErrorCode(1_023_006_006, "支付金额无效");
    ErrorCode PAYMENT_AMOUNT_MISMATCH = new ErrorCode(1_023_006_007, "支付金额与订单应付金额不一致");
    ErrorCode PAYMENT_CALLBACK_CONFLICT = new ErrorCode(1_023_006_008, "支付回调重复或内容冲突");
    ErrorCode PAYMENT_ORDER_STATE_INVALID = new ErrorCode(1_023_006_009, "支付单与订单状态不一致");
}
