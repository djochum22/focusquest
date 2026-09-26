import { apiClient } from './client'
import type { OffTaskStatus } from '../types/offTask'

/** A camera-verified session's off-task status and episodes. */
export async function fetchOffTaskStatus(sessionId: number): Promise<OffTaskStatus> {
  const { data } = await apiClient.get<OffTaskStatus>(`/api/focus-sessions/${sessionId}/off-task`)
  return data
}

/** Marks an off-task episode as inaccurate: it is never subtracted, and time already taken is given back. */
export async function disputeOffTask(sessionId: number, episodeStartedAt: string): Promise<OffTaskStatus> {
  const { data } = await apiClient.post<OffTaskStatus>(`/api/focus-sessions/${sessionId}/off-task/disputes`, {
    episodeStartedAt,
  })
  return data
}
