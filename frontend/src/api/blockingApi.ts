import { apiClient } from './client'
import type { RuleTarget, RuleTargetRequest } from '../types/blocking'

const BLOCKED = '/api/blocked-targets'
const ALLOWLIST = '/api/allowlist-targets'

/*
 * Block and allowlist rules share one request and response shape. While website blocking is being
 * enforced the backend only lets the configuration get stricter and answers 409 otherwise: that
 * means editing or deleting a block rule, or adding or editing an allowlist rule.
 */

export async function getBlockedTargets(): Promise<RuleTarget[]> {
  const { data } = await apiClient.get<RuleTarget[]>(BLOCKED)
  return data
}

/** Fails with 400 for a malformed rule and 409 for one that already exists. */
export async function addBlockedTarget(request: RuleTargetRequest): Promise<RuleTarget> {
  const { data } = await apiClient.post<RuleTarget>(BLOCKED, request)
  return data
}

/** Replaces the rule; 409 while blocking is enforced. */
export async function updateBlockedTarget(id: number, request: RuleTargetRequest): Promise<RuleTarget> {
  const { data } = await apiClient.put<RuleTarget>(`${BLOCKED}/${id}`, request)
  return data
}

/** 409 while blocking is enforced. */
export async function deleteBlockedTarget(id: number): Promise<void> {
  await apiClient.delete(`${BLOCKED}/${id}`)
}

export async function getAllowlistTargets(): Promise<RuleTarget[]> {
  const { data } = await apiClient.get<RuleTarget[]>(ALLOWLIST)
  return data
}

/** Fails with 400 for a malformed rule, 409 for one that already exists or while blocking is enforced. */
export async function addAllowlistTarget(request: RuleTargetRequest): Promise<RuleTarget> {
  const { data } = await apiClient.post<RuleTarget>(ALLOWLIST, request)
  return data
}

/** Replaces the rule; 409 while blocking is enforced. */
export async function updateAllowlistTarget(id: number, request: RuleTargetRequest): Promise<RuleTarget> {
  const { data } = await apiClient.put<RuleTarget>(`${ALLOWLIST}/${id}`, request)
  return data
}

export async function deleteAllowlistTarget(id: number): Promise<void> {
  await apiClient.delete(`${ALLOWLIST}/${id}`)
}
