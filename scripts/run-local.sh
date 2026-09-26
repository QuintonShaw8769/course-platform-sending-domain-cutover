#!/usr/bin/env sh
# Requires JDK 17 or newer, as documented in README.md.
set -eu

OUT="${TMPDIR:-/tmp}/course-domain-onboarding-classes"
mkdir -p "$OUT"
find src/main/java src/test/java -name '*.java' -print | xargs javac -d "$OUT"

if [ "${1:-test}" = "test" ]; then
  java -cp "$OUT" dev.learning.delivery.SendingDomainOnboardingTest
else
  java -cp "$OUT" dev.learning.delivery.CourseReleaseDomain "$@"
fi
