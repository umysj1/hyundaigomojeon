#!/usr/bin/env bash
# 길동무 빙고 시연: 에뮬레이터(emulator-5580)가 켜져 있어야 한다. Git Bash에서 실행.
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"; E="-s emulator-5580"; V=http://127.0.0.1:8088
put() { curl -s -m 3 -X PUT "$V/vehicle/$1" >/dev/null; }
TUNNEL_AT="36.46882,127.11708"; OUTSIDE_AT="37.40000,127.10000"
"$ADB" $E forward tcp:8088 tcp:8088 >/dev/null
( while true; do curl -s -m 2 -X POST "$V/browser/session?client=demo" >/dev/null; sleep 1; done ) & HB=$!
trap 'kill $HB; put "controls?speed=0"; put "gear?gear=Park"; put "body?door_rr=0&front_wiper=Off"' EXIT

put "gear?gear=Park"; put "lights?feature=headlamp_mode&value=Off"
"$ADB" $E shell am start -n kr.gildongmu.bingo/.MainActivity >/dev/null; sleep 4
put "seat?area=rear_right&kind=occupied&value=true"; sleep 4          # 뒷좌석 탑승 → 인사
"$ADB" $E shell am start -n kr.gildongmu.bingo/.MainActivity --ez demo_route true >/dev/null 2>&1; sleep 10
"$ADB" $E shell am start -n kr.gildongmu.bingo/.MainActivity >/dev/null 2>&1; sleep 8   # 빙고판 보여 주기
put "gear?gear=Drive"; put "controls?speed=90"
"$ADB" $E shell am start -n kr.gildongmu.bingo/.MainActivity --ez replay true >/dev/null 2>&1; sleep 8
# 터널: 차 위치를 터널 지도 위로 넣는다(에뮬레이터에선 차가 움직이지 않아서). 시연용 위치 = 공주 정지산터널(OSM).
"$ADB" $E shell am start -n kr.gildongmu.bingo/.MainActivity --es at "$TUNNEL_AT" >/dev/null 2>&1; sleep 4
"$ADB" $E shell am start -n kr.gildongmu.bingo/.MainActivity --es at "$OUTSIDE_AT" >/dev/null 2>&1; sleep 2
put "body?front_wiper=Low"; sleep 8; put "body?front_wiper=Off"                                         # 비
for i in $(seq 10); do put "turn-signal?mode=BlinkRight"; sleep 1; put "turn-signal?mode=Off"; sleep 1; done  # 깜빡이
"$ADB" $E shell am start -n kr.gildongmu.bingo/.MainActivity --ez to_rest true >/dev/null 2>&1; sleep 3   # 다음 휴게소까지 건너뛰기
put "controls?speed=0"; put "gear?gear=Park"; sleep 3; put "body?door_rr=1"; sleep 3; put "body?door_rr=0"   # 휴게소 쉬기 → 도감 도장
sleep 6; put "gear?gear=Drive"; put "controls?speed=90"; sleep 65
put "controls?speed=0"; put "gear?gear=Park"; sleep 5                                                     # 도착 후 결과
