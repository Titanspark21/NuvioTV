#!/usr/bin/env bash
#
# Fork-only guard: has Torbox Instant survived an upstream merge?
#
# This is deliberately a shell script and not a unit test. The 6-hourly sync runs
# unattended, and a Kotlin test guard turned out to be unusable for that: running it
# compiles the WHOLE unit-test source set, so any unrelated upstream test that stops
# compiling takes the guard down with it and halts releases for a reason that has nothing
# to do with this feature. (As of 2026-08-16 three upstream test files do not compile.)
# Grepping the main source tree cannot be broken that way, and it finishes in a second
# rather than three minutes.
#
# Exit 0 = the feature is intact. Exit 1 = something is missing; do not publish.
#
# Run it yourself after a manual merge:
#   bash scripts/check-torbox-instant.sh

set -uo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
src="${repo_root}/app/src/main/java/com/nuvio/tv"
problems=()

# path-under-src<TAB>marker that must appear inside it
#
# The marker matters as much as the path: upstream could keep a file as a stub, and an
# empty DebridProvider.kt is as much a removal as a deleted one.
required="\
core/debrid/DebridProvider.kt	Instant
core/debrid/LocalDebridService.kt	checkCached
core/debrid/LocalDebridAvailabilityService.kt	LocalDebridAvailabilityService
core/debrid/DirectDebridResolver.kt	DirectDebridResolver
core/debrid/TorboxDirectDebridResolver.kt	TorboxDirectDebridResolver
core/debrid/TorboxFileSelector.kt	TorboxFileSelector
core/debrid/DirectDebridStreamPreparer.kt	DirectDebridStreamPreparer
core/debrid/DirectDebridStreamFilter.kt	filterInstant
core/cloud/TorboxCloudLibraryProviderApi.kt	TorboxCloudLibraryProviderApi
core/cloud/CloudLibraryRepository.kt	CloudLibraryRepository
data/remote/api/TorboxApi.kt	checkCached
data/remote/dto/TorboxDto.kt	Torbox
domain/model/DebridSettings.kt	torboxApiKey
data/local/DebridSettingsDataStore.kt	torbox
ui/screens/settings/DebridSettingsScreen.kt	DebridProviders
ui/screens/settings/DebridSettingsViewModel.kt	torbox"

while IFS=$'\t' read -r path marker; do
  [[ -z "${path}" ]] && continue
  file="${src}/${path}"
  if [[ ! -f "${file}" ]]; then
    problems+=("DELETED: app/src/main/java/com/nuvio/tv/${path}")
  elif ! grep -qi -- "${marker}" "${file}"; then
    problems+=("GUTTED:  app/src/main/java/com/nuvio/tv/${path} no longer mentions '${marker}'")
  fi
done <<< "${required}"

# The four capabilities that make the feature work, rather than merely exist. Losing
# LocalTorrentResolve alone turns Torbox Instant into a badge that cannot play anything.
provider_file="${src}/core/debrid/DebridProvider.kt"
if [[ -f "${provider_file}" ]]; then
  for capability in ClientResolve LocalTorrentCacheCheck LocalTorrentResolve CloudLibrary; do
    if ! grep -q -- "${capability}" "${provider_file}"; then
      problems+=("CAPABILITY: DebridProvider.kt no longer declares ${capability}")
    fi
  done
fi

# The runbook that says how to put it back.
if [[ ! -f "${repo_root}/preservation/TORBOX-INSTANT.md" ]]; then
  problems+=("DELETED: preservation/TORBOX-INSTANT.md - the restore instructions")
fi

if (( ${#problems[@]} > 0 )); then
  {
    echo "Torbox Instant is missing or incomplete in this tree."
    echo "This fork keeps the feature; upstream is removing it. Do not publish this build."
    echo
    for problem in "${problems[@]}"; do
      echo "  ${problem}"
    done
    echo
    echo "Restore with:  git checkout torbox-instant-v1 -- <the paths above>"
    echo "Full detail:   preservation/TORBOX-INSTANT.md"
  } >&2
  exit 1
fi

echo "Torbox Instant intact: $(wc -l <<< "${required}") files, 4 capabilities, runbook present."
