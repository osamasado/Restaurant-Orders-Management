import { apiFetch } from './http'
import type { RawMaterialRequest, RawMaterialResponse } from './types'

export function listRawMaterials(): Promise<RawMaterialResponse[]> {
  return apiFetch('/raw-materials')
}

export function createRawMaterial(request: RawMaterialRequest): Promise<RawMaterialResponse> {
  return apiFetch('/raw-materials', { method: 'POST', body: request })
}

export function updateRawMaterial(id: number, request: RawMaterialRequest): Promise<RawMaterialResponse> {
  return apiFetch(`/raw-materials/${id}`, { method: 'PUT', body: request })
}

export function deleteRawMaterial(id: number): Promise<void> {
  return apiFetch(`/raw-materials/${id}`, { method: 'DELETE' })
}

export function uploadRawMaterialImage(id: number, file: File): Promise<RawMaterialResponse> {
  const formData = new FormData()
  formData.append('file', file)
  return apiFetch(`/raw-materials/${id}/image`, { method: 'POST', body: formData })
}

export function deleteRawMaterialImage(id: number): Promise<RawMaterialResponse> {
  return apiFetch(`/raw-materials/${id}/image`, { method: 'DELETE' })
}
