type Translate = (key: string, options?: Record<string, unknown>) => string

/**
 * What an order's table is called on screen. A take-away order has no table: it reads "Take away" (translated)
 * wherever a table would read "Table 7", so the server's internal number for the reserved take-away table never
 * shows. `key` is the screen's own "Table {{number}}" string; `format` wraps the number for the reading direction
 * (isolate, in sentences that may run right to left).
 */
export function tableLabel(
  t: Translate,
  key: string,
  order: { tableNumber: string; takeAway: boolean },
  format: (text: string) => string = (text) => text,
): string {
  return order.takeAway ? t('common.takeAway') : t(key, { number: format(order.tableNumber) })
}
