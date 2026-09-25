// Lets the FocusQuest web app connect the extension without the user copying anything. The web app
// (the only origin Chrome lets message us; see externally_connectable in the manifest) sends the
// extension token it got from the backend, and we store it. It is checked again here, so widening the
// manifest by mistake would not let another site sign the extension in.
//
// The web app also sends `focusquest.sync` after every session or rule change, so blocking follows
// at once instead of at the next 30-second alarm. It only triggers a check-in with the backend,
// which stays the sole authority on what to block.

import type { SyncStatus } from '../types/blocking'
import { EXTENSION_TOKEN_PREFIX } from '../utils/config'

export type ExternalRequest =
  | { type: 'focusquest.status' }
  | { type: 'focusquest.connect'; token: string }
  | { type: 'focusquest.disconnect' }
  | { type: 'focusquest.sync' }

export type ExternalResponse =
  | { ok: true; hasToken: boolean; status: SyncStatus }
  | { ok: false; error: string }

export interface ExternalMessageDependencies {
  /** The one origin allowed to send these messages. */
  allowedOrigin: string
  getToken: () => Promise<string | undefined>
  setToken: (token: string) => Promise<void>
  removeToken: () => Promise<void>
  /** Runs a sync and reports how it went, so the web app learns at once whether the token works. */
  sync: (reason: string) => Promise<{ status: SyncStatus }>
  getStatus: () => Promise<SyncStatus>
}

const REJECTED: ExternalResponse = { ok: false, error: 'Not allowed' }

function isRequest(message: unknown): message is ExternalRequest {
  if (typeof message !== 'object' || message === null) return false
  const { type } = message as { type?: unknown }
  return (
    type === 'focusquest.status' ||
    type === 'focusquest.connect' ||
    type === 'focusquest.disconnect' ||
    type === 'focusquest.sync'
  )
}

export async function handleExternalMessage(
  message: unknown,
  senderOrigin: string | undefined,
  deps: ExternalMessageDependencies,
): Promise<ExternalResponse> {
  if (senderOrigin !== deps.allowedOrigin) return REJECTED
  if (!isRequest(message)) return { ok: false, error: 'Unknown request' }

  switch (message.type) {
    case 'focusquest.status':
      return { ok: true, hasToken: (await deps.getToken()) !== undefined, status: await deps.getStatus() }

    case 'focusquest.connect': {
      const { token } = message
      if (typeof token !== 'string' || !token.startsWith(EXTENSION_TOKEN_PREFIX)) {
        return { ok: false, error: 'That is not an extension token' }
      }
      await deps.setToken(token)
      const { status } = await deps.sync('connected-by-web-app')
      return { ok: true, hasToken: true, status }
    }

    case 'focusquest.disconnect':
      await deps.removeToken()
      return { ok: true, hasToken: false, status: await deps.getStatus() }

    case 'focusquest.sync': {
      // Without a token there is nothing to check in with; don't record a failed sync for it.
      if ((await deps.getToken()) === undefined) return { ok: true, hasToken: false, status: await deps.getStatus() }
      const { status } = await deps.sync('changed-in-web-app')
      return { ok: true, hasToken: true, status }
    }
  }
}
