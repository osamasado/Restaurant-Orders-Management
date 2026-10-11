const MIN_DIGITS = 3

/**
 * Order numbers are shown with at least three digits (001, 002, ... 999) on
 * every screen, so a guest sees the same number on their device and on the
 * hall board. The shown number is a plain integer that an admin can restart
 * at 001 from Settings (for example at the start of a day), so it can repeat
 * across series, never among open orders (the restart is refused while any is
 * open). Past 999 it simply grows to four digits. The server's internal order
 * number, which never repeats, is not shown.
 */
export function formatOrderNumber(orderNumber: number): string {
  return String(orderNumber).padStart(MIN_DIGITS, '0')
}
