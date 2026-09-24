/**
 * Mirrors the backend `ProgressionResponse`. `levelStartXp` and `nextLevelXp` are the total XP at
 * which the current level began and the next one begins.
 */
export interface Progression {
  totalXp: number
  level: number
  levelStartXp: number
  nextLevelXp: number
  gems: number
}
