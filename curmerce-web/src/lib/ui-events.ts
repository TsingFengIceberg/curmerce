export const CART_CHANGED_EVENT = "curmerce:cart-changed";
export const FAVORITES_CHANGED_EVENT = "curmerce:favorites-changed";

export function notifyCartChanged() {
  if (typeof window !== "undefined") window.dispatchEvent(new Event(CART_CHANGED_EVENT));
}

export function notifyFavoritesChanged() {
  if (typeof window !== "undefined") window.dispatchEvent(new Event(FAVORITES_CHANGED_EVENT));
}
