#!/usr/bin/env bash
# Runs every test suite: backend (JUnit), frontend and extension (Vitest), plus type-checks.
# Usage: ./scripts/test-all.sh   (from anywhere)
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "==> Backend (Gradle)"
(cd "$root/backend" && ./gradlew test --rerun-tasks --console=plain)

echo "==> Frontend (type-check, Vitest)"
(cd "$root/frontend" && npm ci --silent && npx vue-tsc -b && npm test)

echo "==> Extension (type-check, Vitest)"
(cd "$root/extension" && npm ci --silent && npm run typecheck && npm test)

echo "All test suites passed."
