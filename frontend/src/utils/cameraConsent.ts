/**
 * The camera consent text the user accepts to turn camera verification on. The version must match
 * the backend's `CameraSettingsService.CONSENT_VERSION`: when these points change in substance, bump
 * both, and everyone is asked again. See requirements specification, sections 19 and 21.
 */
export const CAMERA_CONSENT_VERSION = 1

export const CAMERA_CONSENT_POINTS: readonly string[] = [
  'During a session that uses it, a companion program on this computer watches the camera to check that you are on task, for example facing the screen rather than a phone.',
  'Camera images are analysed in memory on this computer only. They are never saved, uploaded, or sent to FocusQuest.',
  'There is no facial recognition. Only what was observed is kept: the kind of signal (such as away, or a phone in hand), how confident the program was, and when.',
  'When you seem off task you are warned first. If it goes on past a short grace period, that time does not count toward the session or your streaks. You can dispute an interval you think is wrong.',
  'The camera is on only while a session that uses it is running. You can turn camera verification off here at any time.',
]
