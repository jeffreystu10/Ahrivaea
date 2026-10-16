#!/bin/bash
# Starts a dedicated server with the mod twice (to test saving and loading), then reports what happened.
set -u
mkdir -p run/world/datapacks
echo "eula=true" > run/eula.txt
printf 'online-mode=false\nlevel-seed=-447167\nspawn-protection=0\n' > run/server.properties
cp -r .github/test-pack run/world/datapacks/ahv_test
cp -r datapacks/ahrivaea_gateway run/world/datapacks/ahrivaea_gateway
: > server-all.log
run_once () {
  rm -f in.fifo; mkfifo in.fifo
  ./gradlew runServer --no-daemon < in.fifo > server.log 2>&1 &
  local pid=$!
  exec 3> in.fifo
  local ok=0
  for i in $(seq 1 120); do
    sleep 5
    if grep -q "AHRIVAEA_TEST check done" server.log; then ok=1; break; fi
    if grep -qE "Crash report|Exception in server tick loop|Failed to start the minecraft server|BUILD FAILED" server.log; then break; fi
    if ! kill -0 $pid 2>/dev/null; then break; fi
  done
  echo "stop" >&3
  for i in $(seq 1 24); do kill -0 $pid 2>/dev/null || break; sleep 5; done
  kill $pid 2>/dev/null; exec 3>&-
  cat server.log >> server-all.log
  return $((1-ok))
}
run_once; first=$?
run_once; second=$?
report=$(grep -E "AHRIVAEA_TEST|Challenge|Ahrivaea|ahrivaea|gateway|function|ERROR|Exception|Caused by|Crash|at dev\.ahrivaea" server-all.log | grep -v "^\s*$" | head -80 | sed 's/%/%25/g' | sed ':a;N;$!ba;s/\n/%0A/g')
if [ $first -eq 0 ] && [ $second -eq 0 ] && grep -q "AHRIVAEA_TEST creature present" server-all.log && [ "$(grep -c "AHRIVAEA_TEST selftest passed" server-all.log)" -ge 2 ] && ! grep -q "Could not load" server-all.log && [ "$(grep -c "leaderboard: 1000/1000 stage 4; Alice 600, Bob 450" server-all.log)" -ge 2 ] && grep -q "AHRIVAEA_TEST tower complete" server-all.log && grep -q "Tower tower_2 (logs) placed" server-all.log && ! grep -q "unknown block" server-all.log && ! grep -q "unknown item\|unknown reward" server-all.log && grep -q "Loaded 128 challenges" server-all.log && grep -q "Battle arena dimension ready" server-all.log && grep -qE "Loaded 2[0-9][0-9] creature species" server-all.log \
    && [ "$(grep -c "AHRIVAEA_TEST gateway portal ok" server-all.log)" -ge 2 ] && grep -q "AHRIVAEA_TEST gateway config ok" server-all.log \
    && ! grep -qiE "Failed to (load|parse) function|gateway:.*(error|Unknown)" server-all.log; then
  echo "::notice title=Server test::${report}"
else
  echo "::error title=Server test::${report}"
  exit 1
fi
