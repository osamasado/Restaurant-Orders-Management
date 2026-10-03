const MIN_DIGITS = 3

/**
 * Order numbers are shown with at least three digits (001, 002, ... 999) on
 * every screen, so a guest sees the same number on their device and on the
 * hall board. The stored number stays a plain integer; past 999 it simply
 * grows to four digits, so numbers never repeat.
 */
export function formatOrderNumber(orderNumber: number): string {
  return String(orderNumber).padStart(MIN_DIGITS, '0')
}
