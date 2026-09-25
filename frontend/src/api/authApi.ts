import { apiClient } from './client'
import type { LoginRequest, LoginResponse, SetupRequest, SetupStatus, UpdateProfileRequest, User } from '../types/auth'

/** Public: tells the app whether to show first-launch setup or login. */
export async function fetchSetupStatus(): Promise<SetupStatus> {
  const { data } = await apiClient.get<SetupStatus>('/api/auth/setup-status')
  return data
}

export async function login(request: LoginRequest): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>('/api/auth/login', request)
  return data
}

/** Creates the single local account. Fails with 409 once an account exists. */
export async function setup(request: SetupRequest): Promise<LoginResponse> {
  const { data } = await apiClient.post<LoginResponse>('/api/auth/setup', request)
  return data
}

export async function fetchCurrentUser(): Promise<User> {
  const { data } = await apiClient.get<User>('/api/auth/me')
  return data
}

/** Saves the display name and time zone. A time-zone change is refused (409) while blocking is active. */
export async function updateProfile(request: UpdateProfileRequest): Promise<User> {
  const { data } = await apiClient.put<User>('/api/me/profile', request)
  return data
}
