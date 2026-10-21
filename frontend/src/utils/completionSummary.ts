/**
 * What the completion screen showed, kept in this browser so a reload brings the screen back until
 * the student chooses "Plan another". The session itself comes from the server; this only holds
 * what was worked out when it ended (the XP it added, whether it reached today's target), and only
 * for the day it was saved. A convenience: without storage, a reload just shows the start page.
 */
const KEY = 'focusquest.completionSummary'

export interface CompletionSummary {
  sessionId: number
  xpGained: number | null
  reachedTarget: boolean
}

const today = () => new Date().toDateString()

export function loadCompletionSummary(): CompletionSummary | null {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) ?? 'null') as (CompletionSummary & { savedOn?: string }) | null
    if (!saved || saved.savedOn !== today() || !Number.isInteger(saved.sessionId)) return null
    return {
      sessionId: saved.sessionId,
      xpGained: Number.isInteger(saved.xpGained) ? saved.xpGained : null,
      reachedTarget: saved.reachedTarget === true,
    }
  } catch {
    return null
  }
}

export function saveCompletionSummary(summary: CompletionSummary): void {
  try {
    localStorage.setItem(KEY, JSON.stringify({ ...summary, savedOn: today() }))
  } catch {
    // Storage blocked: a reload shows the start page instead.
  }
}

export function clearCompletionSummary(): void {
  try {
    localStorage.removeItem(KEY)
  } catch {
    // ignore
  }
}
