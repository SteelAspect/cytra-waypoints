#!/usr/bin/env bash
# 1.20.4 smoke test. Two dedicated 1.20.4 Fabric servers start: one with the built jar (server/build/libs) and Fabric
# API, one with only Fabric API. Then the dev client (server/src/smoketest) joins the first, checks the menu sync,
# WaypointsApi and every screen, and joins the second to check the client stays quiet on a server without the mod.
# Run it through ./headless.sh so no window shows. WITH_XAERO=1 adds Xaero's Minimap to the client and checks the sync.
# Logs: build/smokeserver*/console.log and server/build/run/smoke/logs/latest.log.
set -uo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$HOME/.local/share/PrismLauncher/java/java-runtime-delta}"
MC=1.20.4 LOADER=0.19.5 INSTALLER=1.1.0
FABRIC_API=$(find ~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.97.3+1.20.4 -name 'fabric-api-0.97.3+1.20.4.jar' | head -1)

./gradlew :server:remapJar --no-daemon -q || exit 1
JAR=$(ls server/build/libs/alloy-waypoints-*+1.20.4*.jar | grep -v sources | head -1)
echo "jar: $JAR"

PIDS=()
start_server() { # dir port jars...
    local S=$1 PORT=$2; shift 2
    rm -rf "$S/mods" "$S/world" "$S/logs" "$S/config"
    mkdir -p "$S/mods"
    [ -f build/fabric-server-launch.jar ] || curl -sSfL -o build/fabric-server-launch.jar \
        "https://meta.fabricmc.net/v2/versions/loader/$MC/$LOADER/$INSTALLER/server/jar" || exit 1
    cp build/fabric-server-launch.jar "$S/"
    cp "$@" "$S/mods/"
    echo "eula=true" > "$S/eula.txt"
    cat > "$S/server.properties" <<PROPS
online-mode=false
server-port=$PORT
level-type=minecraft\:flat
spawn-protection=0
view-distance=4
simulation-distance=4
spawn-monsters=false
PROPS
    rm -f "$S/stdin"; mkfifo "$S/stdin"
    (cd "$S" && exec "$JAVA_HOME/bin/java" -Xmx1G -jar fabric-server-launch.jar nogui < stdin > console.log 2>&1) &
    PIDS+=($!)
}
mkdir -p build
start_server build/smokeserver 25570 "$JAR" "$FABRIC_API"
exec 3> build/smokeserver/stdin       # keep the fifos open so the servers don't see EOF
start_server build/smokeserver-plain 25571 "$FABRIC_API"
exec 4> build/smokeserver-plain/stdin

for S in build/smokeserver build/smokeserver-plain; do
    for _ in $(seq 1 180); do
        grep -q 'Done (' "$S/console.log" 2>/dev/null && break
        sleep 1
    done
    if ! grep -q 'Done (' "$S/console.log"; then
        echo "$S did not start"; tail -40 "$S/console.log"
        kill "${PIDS[@]}" 2>/dev/null; exit 1
    fi
done
echo "servers started"

./gradlew :server:runSmokeTest --no-daemon -q ${WITH_XAERO:+-PwithXaero}
echo stop >&3; echo stop >&4
exec 3>&- 4>&-
wait "${PIDS[@]}"

echo "--- server with the mod"
grep -E 'Alloy Waypoints|sharedwaypoints|Syncing|ERROR|Exception' build/smokeserver/console.log | head -40
echo "--- server without the mod"
grep -E 'ERROR|Exception|lost connection|joined' build/smokeserver-plain/console.log | head -20
echo "--- client"
grep -E 'WaypointsSmoke' server/build/run/smoke/logs/latest.log
grep -E 'XaeroSync|Xaero sync|xaerosync' server/build/run/smoke/logs/latest.log | head -10
grep -q '\[WaypointsSmoke\] PASS' server/build/run/smoke/logs/latest.log || exit 1
if grep -q 'Cannot send packets when not in game' server/build/run/smoke/logs/latest.log; then
    echo 'client sent a packet before the game was in play'; exit 1
fi
if [ -n "${WITH_XAERO:-}" ]; then
    grep -q "Syncing shared waypoints to .*Xaero's Minimap" build/smokeserver/console.log || { echo 'no Xaero sync'; exit 1; }
fi
