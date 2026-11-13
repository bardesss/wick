#!/usr/bin/env bash
# Prints the next release tag, or nothing when there is nothing to ship.
# Usage: next-version.sh <latest-tag-or-empty>, commit titles one per line on stdin.
# [major] beats [minor] beats a patch bump. Titles with [skip release] are ignored.
set -eu

latest="${1:-}"
bump=none

while IFS= read -r title || [ -n "$title" ]; do
  case "$title" in
    "") continue ;;
    *"[skip release]"*) continue ;;
    *"[major]"*) bump=major ;;
    *"[minor]"*) [ "$bump" = major ] || bump=minor ;;
    *) [ "$bump" != none ] || bump=patch ;;
  esac
done

[ "$bump" != none ] || exit 0

if [ -z "$latest" ]; then
  echo "v0.1.0"
  exit 0
fi

IFS=. read -r major minor patch <<< "${latest#v}"
case "$bump" in
  major) major=$((major + 1)); minor=0; patch=0 ;;
  minor) minor=$((minor + 1)); patch=0 ;;
  *) patch=$((patch + 1)) ;;
esac
# versionCode allows at most 99 for minor and patch
if [ "$patch" -gt 99 ]; then minor=$((minor + 1)); patch=0; fi
if [ "$minor" -gt 99 ]; then major=$((major + 1)); minor=0; fi
echo "v$major.$minor.$patch"
