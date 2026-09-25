interface ImportMetaEnv {
  /** Base URL of the Spring Boot backend, without a trailing slash. */
  readonly VITE_API_BASE_URL?: string
  /** Id of the FocusQuest Chrome extension. Defaults to the id its manifest key pins. */
  readonly VITE_EXTENSION_ID?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
