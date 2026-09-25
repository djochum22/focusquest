#!/usr/bin/env bash
# Runs every test suite: backend (JUnit), frontend and extension (Vitest), plus type-checks.
# Usage: ./scripts/test-all.sh   (from anywhere)
#        FOCUSQUEST_E2E=1 ./scripts/test-all.sh   also runs the Playwright end-to-end suite
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Backend (Gradle)"
(cd "$root/backend" && ./gradlew test --rerun-tasks --console=plain)

echo "==> Frontend (type-check, Vitest)"
(cd "$root/frontend" && npm ci --silent && npx vue-tsc -b && npm test)

echo "==> Extension (type-check, Vitest)"
(cd "$root/extension" && npm ci --silent && npm run typecheck && npm test)

if [[ "${FOCUSQUEST_E2E:-}" == "1" ]]; then
  echo "==> End-to-end (Playwright)"
  (cd "$root/e2e" && npm ci --silent && npx playwright install chromium && npm test)
fi

echo "All test suites passed."
