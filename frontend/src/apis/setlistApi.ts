import http from './http'
import type { Setlist, SetlistCreateRequest, SharedSetlist } from '../types/setlist'

export const getSetlists = async () => {
  const { data } = await http.get<Setlist[]>('/api/setlists')
  return data
}

export const getSetlist = async (setlistId: number) => {
  const { data } = await http.get<Setlist>(`/api/setlists/${setlistId}`)
  return data
}

export const createSetlist = async (request: SetlistCreateRequest) => {
  const { data } = await http.post<Setlist>('/api/setlists', request)
  return data
}

export const updateSetlist = async (setlistId: number, request: SetlistCreateRequest) => {
  const { data } = await http.put<Setlist>(`/api/setlists/${setlistId}`, request)
  return data
}

export const deleteSetlist = async (setlistId: number) => {
  await http.delete(`/api/setlists/${setlistId}`)
}

export const generateShareToken = async (setlistId: number): Promise<string> => {
  const { data } = await http.post<{ shareToken: string }>(`/api/setlists/${setlistId}/share`)
  return data.shareToken
}

export const revokeShareToken = async (setlistId: number): Promise<void> => {
  await http.delete(`/api/setlists/${setlistId}/share`)
}

export const getSharedSetlist = async (token: string): Promise<SharedSetlist> => {
  const { data } = await http.get<SharedSetlist>(`/api/setlists/share/${token}`)
  return data
}

export const reorderSetlistItems = async (setlistId: number, itemIds: number[]): Promise<void> => {
  await http.patch(`/api/setlists/${setlistId}/items/reorder`, { itemIds })
}

export const duplicateSetlist = async (setlistId: number, serviceDate: string): Promise<Setlist> => {
  const { data } = await http.post<Setlist>(`/api/setlists/${setlistId}/duplicate`, { serviceDate })
  return data
}

export const createSetlistFromText = async (text: string): Promise<Setlist> => {
  const { data } = await http.post<Setlist>('/api/setlists/from-text', { text })
  return data
}
