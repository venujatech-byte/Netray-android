#!/usr/bin/env bash
set -euo pipefail

pr_number=""
if [[ "${1:-}" == "--pr" ]]; then
  pr_number="${2:?PR number required}"
fi

mapfile -t tags < <(git tag --list 'android-v[0-9]*' --sort=-version:refname)
latest_tag="${tags[0]:-}"
if [[ -z "$latest_tag" ]]; then
  major=1; minor=0; patch=0
  bump="initial"
  if [[ -n "$pr_number" ]]; then
    patch=1
    bump="pr-patch"
  fi
else
  base="${latest_tag#android-v}"
  IFS=. read -r major minor patch <<< "$base"
  major="${major:-0}"; minor="${minor:-0}"; patch="${patch:-0}"
  if [[ -n "$pr_number" ]]; then
    ((patch+=1)); bump="pr-patch"
  else
    messages="$(git log "$latest_tag..HEAD" --format=%B)"
    if [[ "$messages" == *"#major"* ]]; then
      ((major+=1)); minor=0; patch=0; bump="major"
    elif [[ "$messages" == *"#minor"* ]]; then
      ((minor+=1)); patch=0; bump="minor"
    else
      ((patch+=1)); bump="patch"
    fi
  fi
fi

version="${major}.${minor}.${patch}"
if [[ -n "$pr_number" ]]; then version+="-pr.${pr_number}"; fi
version_code=$(((major * 1000000 + minor * 1000 + patch) * 10))

printf 'version=%s\n' "$version"
printf 'version_code=%s\n' "$version_code"
printf 'tag=android-v%s\n' "${major}.${minor}.${patch}"
printf 'bump=%s\n' "$bump"
