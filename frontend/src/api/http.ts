const API_BASE = '/api'

export class ApiError extends Error {
  status: number
  /** How long the server asked the client to wait (the Retry-After header), e.g. on a 429. */
  retryAfterSeconds?: number

  constructor(status: number, message: string, retryAfterSeconds?: number) {
    super(message)
    this.status = status
    this.retryAfterSeconds = retryAfterSeconds
  }
}

type SessionProblemListener = (status: 401 | 403) => void

let sessionProblemListener: SessionProblemListener | null = null

/**
 * Lets the staff session (AuthProvider) hear about a 401 or 403 on any staff
 * request: the account was deleted or its PIN reset (401), or its role changed
 * (403). Screens that have no staff session, like the guest screen, register
 * nothing and are unaffected. Returns a function that removes the listener.
 */
export function onSessionProblem(listener: SessionProblemListener): () => void {
  sessionProblemListener = listener
  return () => {
    if (sessionProblemListener === listener) sessionProblemListener = null
  }
}

/** These answer 401 as a normal part of their job (wrong PIN, not signed in yet), so they are not a session problem. */
const SESSION_CALLS = ['/staff/login', '/staff/me', '/staff/logout']

type ApiFetchOptions = Omit<RequestInit, 'body'> & { body?: unknown }

/**
 * credentials: 'include' carries the session cookie set by /api/staff/login
 * (see AuthProvider). FormData bodies (image uploads) skip the JSON
 * Content-Type header so the browser can set its own multipart boundary.
 */
export async function apiFetch<T>(path: string, options: ApiFetchOptions = {}): Promise<T> {
  const { body, headers, ...rest } = options
  const isFormData = body instanceof FormData

  const response = await fetch(`${API_BASE}${path}`, {
    ...rest,
    credentials: 'include',
    headers: isFormData ? headers : { 'Content-Type': 'application/json', ...headers },
    body: isFormData ? body : body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (!response.ok) {
    const message = await response.text().catch(() => '')
    if ((response.status === 401 || response.status === 403) && !SESSION_CALLS.some((call) => path.startsWith(call))) {
      sessionProblemListener?.(response.status)
    }
    const retryAfter = Number(response.headers.get('Retry-After'))
    throw new ApiError(response.status, message || response.statusText, retryAfter > 0 ? retryAfter : undefined)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}
