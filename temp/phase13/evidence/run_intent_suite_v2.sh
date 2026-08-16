#!/usr/bin/env bash
# Phase 13 §8/§9 — intent quality + latency, Device A (S20 FE), frozen RC2.
# v2: foreground-guarded. Aborts rather than tapping blind if Thraksha is not on top.
# Never presses START; no routine is executed by this suite.
set -u
export MSYS_NO_PATHCONV=1
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
D="-s RZCW40LVBJD"
OUT="temp/phase13/evidence/intent_results.tsv"
UI=/sdcard/ui13.xml
PKG=com.thraksha.guardian

INPUT="540 786"; SEND="902 784"; CLOSE="996 185"; MAYBE="540 1916"; TAB_AUTO="540 2198"

sh_() { $ADB $D shell "$@" </dev/null 2>/dev/null; }
dump() { sh_ uiautomator dump $UI >/dev/null; sh_ cat $UI | tr -d '\r'; }
fg_ok() { sh_ dumpsys activity activities | tr -d '\r' | grep -m1 topResumedActivity | grep -q "$PKG/.MainActivity"; }
tap() { sh_ input tap $1 $2 >/dev/null; }

relaunch() {
  sh_ am force-stop $PKG >/dev/null; sleep 3
  sh_ monkey -p $PKG -c android.intent.category.LAUNCHER 1 >/dev/null; sleep 10
  local x; x=$(dump)
  echo "$x" | grep -q "Samsung Optimization" && { tap $MAYBE; sleep 2; }
  tap $TAB_AUTO; sleep 3
}

# wait until no inference is in flight
wait_idle() { for i in $(seq 1 60); do dump | grep -q "WORKING" || return 0; sleep 3; done; return 1; }

ready() {                       # returns 0 only when safe to type a new prompt
  fg_ok || { relaunch; fg_ok || return 1; }
  local x; x=$(dump)
  echo "$x" | grep -q "Samsung Optimization" && { tap $MAYBE; sleep 2; x=$(dump); }
  echo "$x" | grep -qE "Plan preview" && { tap $CLOSE; sleep 2; x=$(dump); }
  echo "$x" | grep -q "Ask Thraksha" || { tap $TAB_AUTO; sleep 3; x=$(dump); }
  echo "$x" | grep -q "Ask Thraksha" || return 1
  wait_idle || return 1
  return 0
}

PROMPT_FILE=temp/phase13/evidence/prompts_v2.tsv
echo -e "idx\tcategory\tprompt\texpected\telapsed_s\toutcome\tresult_text" > "$OUT"

i=0
while IFS=$'\t' read -r prompt expected category <&3; do
  [ -z "${prompt:-}" ] && continue
  i=$((i+1))

  if ! ready; then
    printf '%s\t%s\t%s\t%s\t\tHARNESS_ABORT\t(Thraksha not in foreground / stuck)\n' \
      "$i" "$category" "$prompt" "$expected" >> "$OUT"
    echo "[$i] ABORT - could not reach a ready state"; continue
  fi

  tap $INPUT; sleep 1
  sh_ input text "$(printf '%s' "$prompt" | sed 's/ /%s/g')" >/dev/null; sleep 1

  start=$(date +%s)
  tap $SEND

  # 1) wait for WORKING to appear (inference accepted), 2) wait for it to clear
  seen=0
  for t in $(seq 1 10); do sleep 1; dump | grep -q "WORKING" && { seen=1; break; }; done
  outcome=""; elapsed=""; result=""
  for t in $(seq 1 60); do
    sleep 3
    x=$(dump)
    echo "$x" | grep -q "WORKING" && continue
    elapsed=$(( $(date +%s) - start ))
    if echo "$x" | grep -q "THRAKSHA UNDERSTOOD"; then
      outcome="PLAN"
      result=$(echo "$x" | grep -oE 'text="[^"]*"' | sed 's/text="//;s/"$//' \
               | grep -v '^$' | grep -vE '^(Automate|Ask Thraksha|AVAILABLE|Plan preview|THRAKSHA UNDERSTOOD)$' \
               | head -2 | tr '\n' '|')
    else
      outcome="NO_PLAN"
      result=$(echo "$x" | grep -oE 'text="[^"]*"' | sed 's/text="//;s/"$//' \
               | grep -v '^$' | grep -vE '^(Automate|Protect|Audit|Ask Thraksha|AVAILABLE|ROUTINES|Meeting|Focus|Driving|PREVIEW)$' \
               | head -3 | tr '\n' '|')
    fi
    break
  done
  [ -z "$elapsed" ] && { elapsed=$(( $(date +%s) - start )); outcome="TIMEOUT"; result="(still WORKING after 180s)"; }
  [ "$seen" = "0" ] && [ "$outcome" = "NO_PLAN" ] && outcome="NO_INFERENCE"

  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$i" "$category" "$prompt" "$expected" "$elapsed" "$outcome" "$result" >> "$OUT"
  echo "[$i] ${elapsed}s $outcome :: $prompt :: $result"

  x=$(dump)
  echo "$x" | grep -q "Plan preview" && { tap $CLOSE; sleep 2; }
done 3< "$PROMPT_FILE"

echo "DONE - $i prompts"
