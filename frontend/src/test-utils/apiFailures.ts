import { AxiosError, type InternalAxiosRequestConfig } from 'axios'

/** An error like the one Axios throws when the backend answers with a failure status. */
export function apiFailure(status: number, body: unknown): AxiosError {
  const config = { headers: {} } as InternalAxiosRequestConfig
  return new AxiosError(`Request failed with status code ${status}`, 'ERR_BAD_REQUEST', config, null, {
    status,
    statusText: '',
    headers: {},
    config,
    data: body,
  })
}

/** An error like the one Axios throws when no response arrives at all (backend down, offline). */
export function networkFailure(): AxiosError {
  return new AxiosError('Network Error', 'ERR_NETWORK', { headers: {} } as InternalAxiosRequestConfig)
}
