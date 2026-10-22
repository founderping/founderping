#!/usr/bin/env bash
# Builds the library and publishes it to Maven Central as app.founderping:widget,
# at the version set in founderping/build.gradle.kts:
#
#   ./publish.sh
#
# Reads two gitignored files next to it, both explained in publish.env.example:
# publish.env (Central Portal token, signing passphrase) and signing-key.asc.
# The Portal validates the upload first, and the release then waits for a yes
# here: a version on Maven Central can never be changed or taken down.
set -euo pipefail
cd "$(dirname "$0")"

CENTRAL_API=https://central.sonatype.com/api/v1/publisher
BUNDLE=founderping/build/central/bundle.zip
POLL_SECONDS=5

fail() { echo "$*" >&2; exit 1; }

command -v gpg >/dev/null || fail "gpg not found: install GnuPG, which signs the release"
[[ -f publish.env ]] || fail "publish.env not found: copy publish.env.example and fill it in"
[[ -f signing-key.asc ]] || fail "signing-key.asc not found: publish.env.example says how to export it"
. ./publish.env
for name in MAVEN_CENTRAL_USERNAME MAVEN_CENTRAL_PASSWORD SIGNING_KEY_PASSWORD; do
  [[ -n ${!name:-} ]] || fail "$name is empty in publish.env"
done

# gpg signs from a keyring that exists for this build only; the short path keeps
# gpg-agent's socket under the length limit on macOS.
gnupg_home=$(mktemp -d /tmp/founderping-gnupg.XXXXXX)
drop_keyring() {
  gpgconf --homedir "$gnupg_home" --kill gpg-agent 2>/dev/null || true
  rm -rf "$gnupg_home"
}
trap drop_keyring EXIT
gpg --homedir "$gnupg_home" --batch --quiet --pinentry-mode loopback --passphrase-fd 0 \
  --import signing-key.asc <<<"$SIGNING_KEY_PASSWORD"

# No daemon, so the passphrase doesn't stay behind in a background JVM; no
# stdin, so Gradle can't swallow the answer to the prompt below.
SIGNING_GNUPG_HOME=$gnupg_home SIGNING_KEY_PASSWORD=$SIGNING_KEY_PASSWORD \
  ./gradlew --no-daemon \
  :founderping:clean :founderping:test :founderping:lintRelease :founderping:centralBundle </dev/null
drop_keyring

auth=$(printf '%s:%s' "$MAVEN_CENTRAL_USERNAME" "$MAVEN_CENTRAL_PASSWORD" | base64 | tr -d '\n')

# The token reaches curl on stdin rather than argv, where `ps` would show it.
central() {
  printf 'header = "Authorization: Bearer %s"\n' "$auth" |
    curl --config - --silent --show-error --fail-with-body "$@"
}

# Polls until the deployment reaches state $1, leaving the last response in $status.
wait_for() {
  local state shown=
  while :; do
    status=$(central -X POST "$CENTRAL_API/status?id=$deployment")
    state=$(sed -n 's/.*"deploymentState" *: *"\([A-Z_]*\)".*/\1/p' <<<"$status")
    [[ $state == "$shown" ]] || { echo "  $state"; shown=$state; }
    [[ $state == FAILED ]] && fail "Deployment failed: $status"
    [[ $state == "$1" ]] && return
    sleep "$POLL_SECONDS"
  done
}

deployment=$(central -X POST -F "bundle=@$BUNDLE" "$CENTRAL_API/upload?publishingType=USER_MANAGED") ||
  fail "Upload failed: $deployment"
echo "Uploaded as deployment $deployment"
wait_for VALIDATED

purls=$(grep -o 'pkg:maven/[^"]*' <<<"$status" | paste -sd ' ' -)
answer=
read -r -p "Publish $purls to Maven Central? This can never be undone. [y/N] " answer || true
[[ $answer == [yY] ]] || {
  echo "Not published. It waits at https://central.sonatype.com/publishing/deployments to be published or dropped."
  exit 0
}

central -X POST "$CENTRAL_API/deployment/$deployment"
echo "Publishing, which can take half an hour. Ctrl-C is safe: it carries on at the Portal."
wait_for PUBLISHED
echo "Published: $purls"
