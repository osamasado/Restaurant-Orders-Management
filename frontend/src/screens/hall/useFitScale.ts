import { useLayoutEffect, useRef } from 'react'

/**
 * The size steps a panel tries, largest first: 1 is the designed size (74 px in preparation, 86 px ready),
 * the smaller ones are what a busy evening drops to so every number still fits on the screen.
 */
const SCALES = [1, 0.85, 0.72, 0.62, 0.53, 0.45, 0.38, 0.32]

/**
 * A wall display cannot be scrolled, so a list that does not fit is a list that is cut off. The list flows in
 * columns (top to bottom, then the next column); when the numbers do not fit the panel, this picks the largest
 * size step at which they do and sets it as --hall-scale on the list. It re-fits when the number of entries
 * changes, when the panel is resized (a window, a rotated screen) and when the language changes.
 *
 * Quiet boards keep the designed size. At the smallest step the list can still scroll, so nothing is lost
 * even with far more orders than a kitchen can have open.
 */
export function useFitScale(entryCount: number, language: string) {
  const listRef = useRef<HTMLUListElement>(null)

  useLayoutEffect(() => {
    const list = listRef.current
    if (!list) return

    const fit = () => {
      for (const scale of SCALES) {
        list.style.setProperty('--hall-scale', String(scale))
        // Columns that do not fit run off the side (the end side, in right-to-left too), a list that is too
        // tall for its panel runs off the bottom: either way the content is wider or taller than the box.
        if (list.scrollWidth <= list.clientWidth + 1 && list.scrollHeight <= list.clientHeight + 1) return
      }
    }
    fit()

    const observer = new ResizeObserver(fit)
    observer.observe(list)
    return () => observer.disconnect()
  }, [entryCount, language])

  return listRef
}
