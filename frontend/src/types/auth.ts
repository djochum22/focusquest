/** Mirrors the backend `UserDto`. */
export interface User {
  id: number
  username: string
  displayName: string
  timezone: string
  createdAt: string
}

/** Mirrors the backend `LoginRequest`. */
export interface LoginRequest {
  username: string
  password: string
}

/** Mirrors the backend `SetupRequest`. */
export interface SetupRequest {
  username: string
  password: string
  displayName: string
  timezone: string
}

/** Mirrors the backend `UpdateProfileRequest`. The username cannot be changed. */
export interface UpdateProfileRequest {
  displayName: string
  timezone: string
}

/** Mirrors the backend `SetupStatusResponse`. */
export interface SetupStatus {
  /** True until the first-launch account has been created. */
  setupRequired: boolean
}

/** Mirrors the backend `LoginResponse`; returned by both login and setup. */
export interface LoginResponse {
  token: string
  tokenType: string
  expiresInSeconds: number
  user: User
}
