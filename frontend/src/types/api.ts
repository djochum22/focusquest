/** Body of every backend error response (`ErrorResponse` on the server). */
export interface ApiErrorBody {
  code: string
  message: string
}

/** A failed API call, normalised so views never have to inspect Axios errors. */
export interface ApiError {
  /** HTTP status, or null when no response arrived (network failure, backend down). */
  status: number | null
  code: string
  message: string
}
