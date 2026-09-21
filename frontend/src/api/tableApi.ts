import { apiFetch } from './http'
import type { TableRequest, TableResponse } from './types'

export function listTables(): Promise<TableResponse[]> {
  return apiFetch('/tables')
}

export function createTable(request: TableRequest): Promise<TableResponse> {
  return apiFetch('/tables', { method: 'POST', body: request })
}

export function updateTable(id: number, request: TableRequest): Promise<TableResponse> {
  return apiFetch(`/tables/${id}`, { method: 'PUT', body: request })
}

export function deleteTable(id: number): Promise<void> {
  return apiFetch(`/tables/${id}`, { method: 'DELETE' })
}

export function pairTable(id: number): Promise<TableResponse> {
  return apiFetch(`/tables/${id}/pair`, { method: 'POST' })
}

export function unpairTable(id: number): Promise<TableResponse> {
  return apiFetch(`/tables/${id}/pair`, { method: 'DELETE' })
}
