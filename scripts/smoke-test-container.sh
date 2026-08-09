#!/usr/bin/env bash
# Builds the Debt Hunter image, runs it offline against a real fixture repo, and asserts:
#   - the image builds and runs successfully with --network none
#   - exit code is 0 or 1
#   - all 4 report files are produced (debt-hunter.json, summary.md, metrics.json, debt-hunter.sarif)
#   - the container does not run as root
#   - the container has no elevated Linux capabilities
#
# Usage: scripts/smoke-test-container.sh [image-tag]

set -euo pipefail

IMAGE_TAG="${1:-debt-hunter:smoke-test}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Colima only bind-mounts $HOME by default, so the fixture/output dirs must live under it.
WORK_DIR="$(mktemp -d "${HOME}/.debt-hunter-smoke-test.XXXXXX")"
FIXTURE_DIR="${WORK_DIR}/repo"
OUTPUT_DIR="${WORK_DIR}/output"

cleanup() {
  rm -rf "${WORK_DIR}"
}
trap cleanup EXIT

echo "==> Building image ${IMAGE_TAG}"
docker build -t "${IMAGE_TAG}" "${REPO_ROOT}"

echo "==> Creating fixture repository at ${FIXTURE_DIR}"
mkdir -p "${FIXTURE_DIR}" "${OUTPUT_DIR}"
(cd "${REPO_ROOT}" && ./mvnw -q -pl testkit \
  org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=com.debthunter.testkit.FixtureRepoCli \
  -Dexec.args="${FIXTURE_DIR}")

echo "==> Running offline scan (--network none)"
set +e
docker run --rm --network none \
  -v "${FIXTURE_DIR}:/workspace/repo:ro" \
  -v "${OUTPUT_DIR}:/output" \
  "${IMAGE_TAG}" scan --repo /workspace/repo --output-dir /output
EXIT_CODE=$?
set -e

echo "==> Exit code: ${EXIT_CODE}"
if [ "${EXIT_CODE}" -ne 0 ] && [ "${EXIT_CODE}" -ne 1 ]; then
  echo "FAIL: expected exit code 0 or 1, got ${EXIT_CODE}" >&2
  exit 1
fi

echo "==> Checking output files"
for file in debt-hunter.json summary.md metrics.json debt-hunter.sarif; do
  if [ ! -f "${OUTPUT_DIR}/${file}" ]; then
    echo "FAIL: missing output file ${file}" >&2
    exit 1
  fi
done
echo "OK: all 4 report files present"

echo "==> Checking the image runs as a non-root user"
IMAGE_USER="$(docker inspect "${IMAGE_TAG}" --format '{{.Config.User}}')"
if [ -z "${IMAGE_USER}" ] || [ "${IMAGE_USER}" = "root" ] || [ "${IMAGE_USER}" = "0" ]; then
  echo "FAIL: image User is '${IMAGE_USER}', expected a non-root user" >&2
  exit 1
fi
echo "OK: image runs as '${IMAGE_USER}'"

echo "==> Checking no elevated capabilities are requested"
CONTAINER_ID="$(docker create "${IMAGE_TAG}")"
CAP_ADD="$(docker inspect "${CONTAINER_ID}" --format '{{.HostConfig.CapAdd}}')"
PRIVILEGED="$(docker inspect "${CONTAINER_ID}" --format '{{.HostConfig.Privileged}}')"
docker rm "${CONTAINER_ID}" >/dev/null
if [ "${CAP_ADD}" != "[]" ] && [ "${CAP_ADD}" != "<no value>" ]; then
  echo "FAIL: image requests extra capabilities: ${CAP_ADD}" >&2
  exit 1
fi
if [ "${PRIVILEGED}" != "false" ]; then
  echo "FAIL: image requests privileged mode" >&2
  exit 1
fi
echo "OK: no elevated capabilities, not privileged"

echo "==> Smoke test passed"
