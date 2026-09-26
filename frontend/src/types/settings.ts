/**
 * The JSON document returned by `GET /api/export` (the backend `LocalDataExportDto`). The UI only
 * saves it to a file and sends it back to restore it, so just the metadata it reads is typed.
 */
export interface LocalDataExport {
  exportedAt: string
  schemaVersion: string
  [section: string]: unknown
}
