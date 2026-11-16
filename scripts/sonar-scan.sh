#!/usr/bin/env bash
# SonarQube scan of one git ref, exported to JSON (Phase IV 5.2).
# From the root of the repo, with Docker running, Java 25 and jq installed:
#   scripts/sonar-scan.sh tags/phase3 docs/metrics/phase3
# Starts SonarQube Community Build on http://localhost:9000 if it isn't up yet and leaves it running,
# so you can browse the results. Stop it with: docker stop sonarqube-local
set -euo pipefail

SONARQUBE_IMAGE=sonarqube:26.11.0.134175-community
# SonarScanner CLI 8.4.0.16527. Pinned by digest: Docker Hub has no tag for this exact version.
SCANNER_IMAGE=sonarsource/sonar-scanner-cli@sha256:6e6eca4d552695a98fb7dab84f0c1bd0885ebbfe272654d1552e24bd7f452d51
CONTAINER=sonarqube-local
HOST=http://localhost:9000
# Only protects a SonarQube that runs on your own computer. Replaces the default admin/admin on first start.
ADMIN_PASSWORD=${SONAR_ADMIN_PASSWORD:-LocalScan_2026x}
METRICS=ncloc,complexity,cognitive_complexity,code_smells
PAGE_SIZE=500

if [[ $# -ne 2 ]]; then
  echo "usage: $0 <git-ref> <output-folder>   (example: $0 tags/phase3 docs/metrics/phase3)" >&2
  exit 1
fi
ref=$1
repo=$(git rev-parse --show-toplevel)
commit=$(git -C "$repo" rev-parse --verify --quiet "$ref^{commit}") || { echo "unknown git ref: $ref" >&2; exit 1; }
project=fortunategoods-${ref##*/}
mkdir -p "$2"
out=$(cd "$2" && pwd)

api() { curl -fsS -u "admin:$ADMIN_PASSWORD" "${@:2}" "$HOST/api/$1"; }

echo "==> SonarQube ($SONARQUBE_IMAGE)"
if docker container inspect "$CONTAINER" >/dev/null 2>&1; then
  running_image=$(docker container inspect -f '{{.Config.Image}}' "$CONTAINER")
  if [[ $running_image != "$SONARQUBE_IMAGE" ]]; then
    echo "container $CONTAINER runs $running_image, not $SONARQUBE_IMAGE. Remove it first: docker rm -f $CONTAINER" >&2
    exit 1
  fi
  docker start "$CONTAINER" >/dev/null
else
  docker run -d --name "$CONTAINER" -p 9000:9000 -e SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true "$SONARQUBE_IMAGE" >/dev/null
fi
until curl -fsS "$HOST/api/system/status" 2>/dev/null | grep -q '"status":"UP"'; do sleep 3; done

if ! api authentication/validate | grep -q '"valid":true'; then
  curl -fsS -u admin:admin -X POST \
    "$HOST/api/users/change_password?login=admin&previousPassword=admin&password=$ADMIN_PASSWORD"
fi

token_name=$project-$(date +%s)
token=$(api "user_tokens/generate?name=$token_name" -X POST | jq -r .token)
work=$(mktemp -d)
cleanup() {
  git -C "$repo" worktree remove --force "$work/src" 2>/dev/null || true
  rm -rf "$work"
  api "user_tokens/revoke?name=$token_name" -X POST >/dev/null 2>&1 || true
}
trap cleanup EXIT

echo "==> Checking out $ref ($commit)"
git -C "$repo" worktree add --detach --quiet "$work/src" "$commit"

echo "==> Building the backend (the Java analysis reads the compiled classes)"
(cd "$work/src/app/backend" &&
  ./mvnw -q -DskipTests compile dependency:copy-dependencies -DoutputDirectory=target/deps)

echo "==> Scanning into project $project"
docker run --rm --add-host=host.docker.internal:host-gateway \
  -e SONAR_HOST_URL=http://host.docker.internal:9000 -e SONAR_TOKEN="$token" \
  -v "$work/src:/usr/src" "$SCANNER_IMAGE" \
  -Dsonar.projectKey="$project" -Dsonar.projectName="$project" -Dsonar.projectVersion="${commit:0:7}" \
  -Dsonar.sources=app/backend/src/main/java,app/frontend/src \
  -Dsonar.exclusions='**/node_modules/**' \
  -Dsonar.java.binaries=app/backend/target/classes \
  -Dsonar.java.libraries='app/backend/target/deps/*.jar' \
  -Dsonar.working.directory=.scannerwork \
  -Dsonar.scm.disabled=true

# The scanner only uploads the report; the server computes the measures afterwards.
task=$(sed -n 's/^ceTaskId=//p' "$work/src/.scannerwork/report-task.txt")
while :; do
  status=$(api "ce/task?id=$task" | jq -r .task.status)
  case $status in
    SUCCESS) break ;;
    FAILED | CANCELED) echo "SonarQube failed to process the report ($status)" >&2; exit 1 ;;
    *) sleep 2 ;;
  esac
done

# Fetches every page of a list endpoint and writes one JSON with all the items under $2.
fetch_all() {
  local query=$1 key=$2 file=$3 page=1 pages
  pages=$(mktemp)
  while :; do
    api "$query&ps=$PAGE_SIZE&p=$page" >>"$pages"
    (( page * PAGE_SIZE < $(tail -n 1 "$pages" | jq .paging.total) )) || break
    page=$((page + 1))
  done
  jq -s --arg k "$key" \
    '.[0] + {($k): (map(.[$k]) | add)} | .paging = {pageIndex: 1, pageSize: (.[$k] | length), total: .paging.total}' \
    "$pages" >"$file"
  rm -f "$pages"
}

echo "==> Exporting to $out"
fetch_all "measures/component_tree?component=$project&qualifiers=FIL&metricKeys=$METRICS" components "$out/measures.json"
fetch_all "issues/search?componentKeys=$project&types=CODE_SMELL&resolved=false" issues "$out/code-smells.json"

jq -n \
  --arg ref "$ref" --arg commit "$commit" --arg project "$project" \
  --arg sonarqube "$(api server/version)" --arg sonarqubeImage "$SONARQUBE_IMAGE" \
  --arg scanner "$(docker run --rm "$SCANNER_IMAGE" --version 2>/dev/null | sed -n 's/.*SonarScanner CLI //p' | head -n 1)" \
  --arg scannerImage "$SCANNER_IMAGE" \
  --argjson profiles "$(api "qualityprofiles/search?project=$project" |
    jq --argjson langs "$(jq '[.components[].language] | unique' "$out/measures.json")" \
      '[.profiles[] | select(.language as $l | $langs | index($l)) | {language: .languageName, name, isBuiltIn}]')" \
  --arg command "scripts/sonar-scan.sh $*" --arg scannedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  '{ref: $ref, commit: $commit, project: $project, sonarqube: $sonarqube, sonarqubeImage: $sonarqubeImage,
    scanner: $scanner, scannerImage: $scannerImage, qualityProfiles: $profiles, command: $command, scannedAt: $scannedAt}' \
  >"$out/scan-info.json"

echo "==> Done: $out (browse $HOST/dashboard?id=$project)"
