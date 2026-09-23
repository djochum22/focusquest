import { apiClient } from './client'
import type { CreateSessionRequest, FocusSession } from '../types/session'

const BASE = '/api/focus-sessions'

/** Creates a PLANNED session. It only begins (and blocking only starts) once it is started. */
export async function createSession(request: CreateSessionRequest): Promise<FocusSession> {
  const { data } = await apiClient.post<FocusSession>(BASE, request)
  return data
}

/** The ACTIVE or PAUSED session, or null when there is none (the backend answers 204). */
export async function fetchCurrentSession(): Promise<FocusSession | null> {
  const response = await apiClient.get<FocusSession | ''>(`${BASE}/current`)
  return response.status === 204 || !response.data ? null : response.data
}

/** Ended sessions (completed, abandoned, interrupted), most recently started first. */
export async function fetchHistory(limit?: number): Promise<FocusSession[]> {
  const { data } = await apiClient.get<FocusSession[]>(`${BASE}/history`, {
    params: limit === undefined ? undefined : { limit },
  })
  return data
}

async function transition(id: number, action: string): Promise<FocusSession> {
  const { data } = await apiClient.post<FocusSession>(`${BASE}/${id}/${action}`)
  return data
}

export const startSession = (id: number) => transition(id, 'start')
export const pauseSession = (id: number) => transition(id, 'pause')
export const resumeSession = (id: number) => transition(id, 'resume')
export const completeSession = (id: number) => transition(id, 'complete')
export const abandonSession = (id: number) => transition(id, 'abandon')

/**
 * Releases the blocking an already-abandoned session is still holding, at an XP penalty. The
 * backend refuses it for a running session; abandon first.
 */
export const overrideSession = (id: number) => transition(id, 'override')
