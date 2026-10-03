/**
 * Data text (sizes like "300 g", prices, device codes) inside an Arabic
 * sentence is reordered by the bidi algorithm: "300 g" can render as "g 300".
 * Wrapping a value in a first-strong isolate (U+2068 ... U+2069) keeps it
 * intact and lets it pick its own direction. Use this for values interpolated
 * into translated strings; in JSX use <bdi> for the same effect.
 */
export function isolate(text: string): string {
  return `⁨${text}⁩`
}
