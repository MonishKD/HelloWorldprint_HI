# apiwatch: every API call to the backend, live, with its JSON (passwords hidden). Ctrl+C to stop.
# Runs inside docker so it works the same on Windows, Mac and Linux. From the root of the repo, with the app up:
#   docker compose run --rm apiwatch
tshark -l -i eth0 -Y http -T fields -E separator='|' \
  -e http.request.method -e http.request.uri -e http.response.code -e http.time -e http.file_data 2>/dev/null |
while IFS='|' read -r method uri code secs body; do
  if [[ -n $method ]]; then
    printf '\n\033[1;36m%s %s\033[0m  \033[2m%s\033[0m\n' "$method" "$uri" "$(date +%T)"
  else
    case $code in 2*) c=32 ;; 4*) c=33 ;; *) c=31 ;; esac
    printf '\033[1;%sm%s\033[0m %s  \033[2m%.0fms\033[0m\n' "$c" "$code" "$uri" "$(( ${secs:-0} * 1000 ))"
  fi
  [[ -z $body ]] && continue
  json=$(printf '%s' "$body" | xxd -r -p)
  printf '%s' "$json" | jq -C 'walk(if type == "object" and has("password") then .password = "***" else . end)' 2>/dev/null ||
    printf '%s\n' "$json"
done
