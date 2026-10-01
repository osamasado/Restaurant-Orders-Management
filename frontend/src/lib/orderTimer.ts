const WARNING_MS = 6 * 60_000
const LATE_MS = 12 * 60_000

export type TimerLevel = 'neutral' | 'warning' | 'late'

export function elapsedMs(placedAt: string, now: number): number {
  return Math.max(0, now - Date.parse(placedAt))
}

export function timerLevel(ms: number): TimerLevel {
  if (ms >= LATE_MS) return 'late'
  if (ms >= WARNING_MS) return 'warning'
  return 'neutral'
}

/** Waiting time as m:ss, e.g. 4:07 or 61:20 - minutes keep counting past 60, no hours. */
export function formatElapsed(ms: number): string {
  const totalSeconds = Math.floor(ms / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${String(seconds).padStart(2, '0')}`
}

