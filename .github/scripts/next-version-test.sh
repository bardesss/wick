#!/usr/bin/env bash
# Checks next-version.sh against the release rules.
set -u
cd "$(dirname "$0")"

fail=0
assert_eq() {
  if [ "$1" != "$2" ]; then
    echo "FAIL: expected '$1', got '$2' (case: $3)"
    fail=1
  else
    echo "ok: $3"
  fi
}

run() { printf '%b' "$2" | ./next-version.sh "$1"; }

assert_eq "v0.3.1" "$(run v0.3.0 'Fix a')" "patch by default"
assert_eq "v0.3.1" "$(run v0.3.0 'Fix a\nBump x [skip release]')" "skipped commit does not hide a shipped one"
assert_eq "" "$(run v0.3.0 'Bump x [skip release]')" "all skipped ships nothing"
assert_eq "v0.4.0" "$(run v0.3.0 'Fix a\nAdd b [minor]')" "minor beats patch"
assert_eq "v1.0.0" "$(run v0.3.0 'Add b [minor]\nRework [major]')" "major beats minor"
assert_eq "v0.4.0" "$(run v0.3.99 'Fix a')" "patch clamps at 99"
assert_eq "v0.1.0" "$(run '' 'First')" "no tag starts at 0.1.0"
assert_eq "v0.3.1" "$(run v0.3.0 'Fix a\nAdd b [minor] [skip release]')" "skipped minor does not bump"
assert_eq "v1.0.0" "$(run v0.99.0 'Add b [minor]')" "minor clamps at 99"

assert_eq "v1.0.0" "$(run v0.3.0 'Rework [major]
Add b [minor]')" "major wins when it comes first"
assert_eq "" "$(printf '' | ./next-version.sh v0.3.0)" "empty stdin ships nothing"
assert_eq "" "$(run v0.3.0 '
')" "one blank line ships nothing"
assert_eq "" "$(run '' '
')" "blank line with no tag ships nothing"

exit "$fail"
