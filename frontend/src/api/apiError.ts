import { isAxiosError } from 'axios'
import type { ApiError, ApiErrorBody } from '../types/api'

function isErrorBody(data: unknown): data is ApiErrorBody {
  return (
    typeof data === 'object' &&
    data !== null &&
    typeof (data as ApiErrorBody).code === 'string' &&
    typeof (data as ApiErrorBody).message === 'string'
  )
}

/** Converts anything thrown by an API call into an {@link ApiError}. */
export function toApiError(error: unknown): ApiError {
  if (isAxiosError(error)) {
    if (!error.response) {
      return {
        status: null,
        code: 'NETWORK_ERROR',
        message: 'Cannot reach the FocusQuest server. Make sure the backend is running.',
      }
    }
    const { status, data } = error.response
    if (isErrorBody(data)) {
      return { status, code: data.code, message: data.message }
    }
    return { status, code: 'ERROR', message: `Request failed (HTTP ${status}).` }
  }
  return {
    status: null,
    code: 'UNKNOWN',
    message: error instanceof Error ? error.message : 'Something went wrong.',
  }
}

export function getErrorMessage(error: unknown): string {
  return toApiError(error).message
}
