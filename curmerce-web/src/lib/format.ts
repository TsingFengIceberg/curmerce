export function formatMoney(fen?: number | null) {
  return `¥${((fen ?? 0) / 100).toFixed(2)}`;
}

export function formatStock(stock?: number | null) {
  return stock && stock > 0 ? `库存 ${stock}` : "暂时缺货";
}
