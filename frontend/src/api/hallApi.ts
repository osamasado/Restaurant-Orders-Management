import { apiFetch } from './http'
import type { HallBoardResponse } from './types'

/** Public: the hall screen has no login, so this needs no session. */
export function getHallBoard(): Promise<HallBoardResponse> {
  return apiFetch('/hall/orders')
}
