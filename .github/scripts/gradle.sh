#!/usr/bin/env bash
# Gradle'ı çalıştırır; başarısız olursa hatanın özünü ::error:: notu olarak yazar
# (notlar GitHub API'sinden giriş yapmadan da okunabilir, kayıtlar okunamaz).
set -uo pipefail

log=$(mktemp)
chmod +x ./gradlew
./gradlew --no-daemon --stacktrace "$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}

if [ "$status" -ne 0 ]; then
  # Kotlin derleme hataları (e: ...), "What went wrong" bölümü ve ilk "Caused by" satırları.
  summary=$(
    {
      grep -E '^e: ' "$log" | head -15
      awk '/\* What went wrong:/{f=1;next} /\* Try:/{f=0} f' "$log" | grep -v '^\s*$' | head -15
      grep -E 'Caused by:' "$log" | head -5
      grep -E '^[A-Za-z0-9_.]+Test > .* FAILED' "$log" | head -15
    } | awk '!seen[$0]++' | head -40
  )
  [ -z "$summary" ] && summary=$(tail -25 "$log")
  esc=$(printf '%s' "$summary" | sed -e 's/%/%25/g' | sed -e ':a;N;$!ba;s/\n/%0A/g' | sed -e 's/\r/%0D/g')
  echo "::error title=Gradle $*::$esc"
fi
exit "$status"
