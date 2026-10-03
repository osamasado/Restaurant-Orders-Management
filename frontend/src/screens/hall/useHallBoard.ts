import { useEffect, useState } from 'react'
import { getHallBoard } from '../../api/hallApi'
import type { HallBoardResponse } from '../../api/types'

const POLL_MS = 5000

type HallBoardState = {
  /** Last successful response - kept while a later poll fails, so numbers don't vanish from the wall. */
  board: HallBoardResponse | null
  /** True while polls are failing (network down, server restarting). */
  connectionLost: boolean
}

/**
 * Polls the hall board every 5 s and never stops - it is a wall screen with
 * no login, so there is no session to expire. The next poll is scheduled only
 * after the previous one settles, so requests never overlap.
 */
export function useHallBoard(): HallBoardState {
  const [state, setState] = useState<HallBoardState>({ board: null, connectionLost: false })

  useEffect(() => {
    let cancelled = false
    let timer: number | undefined

    const poll = () => {
      getHallBoard()
        .then((board) => {
          if (cancelled) return
          setState({ board, connectionLost: false })
          timer = window.setTimeout(poll, POLL_MS)
        })
        .catch(() => {
          if (cancelled) return
          setState((previous) => ({ ...previous, connectionLost: true }))
          timer = window.setTimeout(poll, POLL_MS)
        })
    }

    poll()

    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [])

  return state
}
