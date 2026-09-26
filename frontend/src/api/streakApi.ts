import { apiClient } from './client'
import type {
  CreateStreakConfigurationRequest,
  CurrentStreaks,
  FreezeInventory,
  StreakConfiguration,
  UpdateStreakConfigurationRequest,
} from '../types/streak'

/** The current daily and weekly period. `weekly` is null while no weekly streak is configured. */
export async function fetchCurrentStreaks(): Promise<CurrentStreaks> {
  const { data } = await apiClient.get<CurrentStreaks>('/api/streaks/current')
  return data
}

/** The configuration in force per period type: always daily, weekly only once configured. */
export async function fetchStreakConfigurations(): Promise<StreakConfiguration[]> {
  const { data } = await apiClient.get<StreakConfiguration[]>('/api/streak-configurations')
  return data
}

/** Configures a period type that has none yet (in practice, weekly). Fails with 409 if one exists. */
export async function createStreakConfiguration(
  request: CreateStreakConfigurationRequest,
): Promise<StreakConfiguration> {
  const { data } = await apiClient.post<StreakConfiguration>('/api/streak-configurations', request)
  return data
}

/** Changes a configuration. A period already in progress keeps the settings it started with. */
export async function updateStreakConfiguration(
  id: number,
  request: UpdateStreakConfigurationRequest,
): Promise<StreakConfiguration> {
  const { data } = await apiClient.put<StreakConfiguration>(`/api/streak-configurations/${id}`, request)
  return data
}

/** The user's streak freezes, their price and the gem balance. */
export async function fetchFreezes(): Promise<FreezeInventory> {
  const { data } = await apiClient.get<FreezeInventory>('/api/me/streak-freezes')
  return data
}

/** Buys one freeze. Fails with 409 when at the limit or short of gems. */
export async function purchaseFreeze(): Promise<FreezeInventory> {
  const { data } = await apiClient.post<FreezeInventory>('/api/me/streak-freezes/purchase')
  return data
}
