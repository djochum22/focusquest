import { apiClient } from './client'
import type { Progression } from '../types/progression'

export async function fetchProgression(): Promise<Progression> {
  const { data } = await apiClient.get<Progression>('/api/me/progression')
  return data
}
