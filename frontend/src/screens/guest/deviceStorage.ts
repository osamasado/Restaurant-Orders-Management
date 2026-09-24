/**
 * localStorage, not sessionStorage: the pairing code belongs to the physical
 * device (set up once by staff), not to one guest's sit-down in one tab.
 */
const DEVICE_CODE_KEY = 'rom-guest-device-code'

export function readDeviceCode(): string | null {
  try {
    return window.localStorage.getItem(DEVICE_CODE_KEY)
  } catch {
    return null
  }
}

export function writeDeviceCode(code: string) {
  try {
    window.localStorage.setItem(DEVICE_CODE_KEY, code)
  } catch {
    // Storage unavailable (e.g. private browsing) - pairing still works until the tab closes.
  }
}

export function clearDeviceCode() {
  try {
    window.localStorage.removeItem(DEVICE_CODE_KEY)
  } catch {
    // Nothing stored to clear.
  }
}
