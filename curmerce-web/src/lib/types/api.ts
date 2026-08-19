export interface CommonResult<T> {
  code: number;
  msg?: string;
  data: T;
}

export interface ApiPage<T> {
  list: T[];
  total: number;
}

export interface PublicCategoryNode {
  id: number;
  parentId: number | null;
  name: string;
  imageUrl?: string | null;
  children: PublicCategoryNode[];
}

export interface ProductSpecificationValue {
  name: string;
  value: string;
}

export interface PublicProductSummary {
  id: number;
  categoryId: number;
  storeId: number;
  storeName: string;
  name: string;
  subtitle?: string | null;
  mainImageUrl?: string | null;
  minPrice: number;
  minMarketPrice?: number | null;
  totalStock: number;
  available: boolean;
}

export interface PublicProductSku {
  id: number;
  specificationValues: ProductSpecificationValue[];
  imageUrl?: string | null;
  price: number;
  marketPrice?: number | null;
  stock: number;
  available: boolean;
}

export interface PublicProductDetail extends PublicProductSummary {
  imageUrls: string[];
  description?: string | null;
  skus: PublicProductSku[];
}

export interface CartItem {
  id: number;
  quantity: number;
  selected: boolean;
  product?: PublicProductSummary | null;
  sku?: PublicProductSku | null;
  invalidReason?: string | null;
}

export interface CartList {
  validList: CartItem[];
  invalidList: CartItem[];
}

export interface OrderCreateResult {
  orderId: number;
  orderNo: string;
  status: number;
  payableAmount: number;
}

export type ApiDateValue = string | number | null;

export interface OrderSummary {
  id: number;
  orderNo: string;
  merchantId?: number | null;
  storeId?: number | null;
  status: number;
  refundStatus?: number | null;
  itemCount: number;
  totalAmount: number;
  payableAmount: number;
  createTime?: ApiDateValue;
  completionTime?: ApiDateValue;
}

export interface OrderItem {
  id: number;
  productId: number;
  skuId: number;
  productName: string;
  productImageUrl?: string | null;
  skuCode?: string | null;
  specificationValues?: ProductSpecificationValue[] | null;
  skuImageUrl?: string | null;
  price: number;
  quantity: number;
  totalAmount: number;
}

export interface RefundSummary {
  id: number;
  refundNo: string;
  amount: number;
  status: number;
  reason?: string | null;
  requestedTime?: ApiDateValue;
  processedTime?: ApiDateValue;
}

export interface OrderDetail extends OrderSummary {
  paymentNo?: string | null;
  paymentStatus?: number | null;
  paymentAmount?: number | null;
  paidTime?: ApiDateValue;
  receiverName?: string | null;
  receiverMobile?: string | null;
  receiverAreaId?: number | null;
  receiverAreaName?: string | null;
  receiverDetailAddress?: string | null;
  shippingTime?: ApiDateValue;
  logisticsCompany?: string | null;
  trackingNo?: string | null;
  items: OrderItem[];
  refund?: RefundSummary | null;
}

export interface PaymentCreateResult {
  paymentId: number;
  paymentNo: string;
  orderId: number;
  orderNo: string;
  amount: number;
  status: number;
}

export interface PaymentCallbackResult {
  paymentId: number;
  paymentNo: string;
  orderId: number;
  paymentStatus: number;
  orderStatus: number;
  paidAmount: number;
  callbackId: string;
}

export interface ApiErrorShape {
  code?: number;
  msg?: string;
}

export interface MemberToken {
  userId: number;
  accessToken: string;
  refreshToken: string;
  expiresTime: string;
}

export interface MemberProfile {
  id: number;
  mobile: string;
  nickname: string;
  avatar?: string | null;
  email?: string | null;
  sex?: number | null;
}

export interface MemberAddress {
  id: number;
  name: string;
  mobile: string;
  areaId: number;
  areaName?: string | null;
  detailAddress: string;
  defaultStatus: boolean;
}

export interface MemberAddressInput {
  name: string;
  mobile: string;
  areaId: number;
  detailAddress: string;
  defaultStatus: boolean;
}

export interface MemberAddressUpdateInput extends MemberAddressInput {
  id: number;
}
