#!/usr/bin/env bash
# 길동무 빙고 시연 영상용: 대본(시연영상 대본 문서)의 장면 순서대로 에뮬레이터를 움직인다.
# 대본 모드 = AI로 미리 만든 대사·카드 설명·일기(assets/script_demo.json). Git Bash에서 실행.
#   bash demo_video.sh            # 일기 0번(차분한 일기)
#   bash demo_video.sh 1          # 일기 1번(신나는 일기), 2번은 동시
# 준비: 에뮬레이터 켜기, 친구는 미리 골라 두기(현냥이 회색 추천), 아이 태블릿 화면은 크롬 http://localhost:8765
DIARY="${1:-0}"
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"; E="-s emulator-5580"; V=http://127.0.0.1:8088
APP="kr.gildongmu.bingo/.MainActivity"
put() { curl -s -m 3 -X PUT "$V/vehicle/$1" >/dev/null; }
app() { "$ADB" $E shell am start -n "$APP" "$@" >/dev/null 2>&1; }
tap() { "$ADB" $E shell input tap "$1" "$2"; }
# 뒷좌석 태블릿에서 누르는 것처럼: 제목에 $1이 들어간 안 채운 칸을 누른다
kidtap() {
  local i; i=$(curl -s localhost:8765/state | python -c "
import json,sys;c=json.loads(sys.stdin.buffer.read().decode('utf-8'))['cells'];r=[i for i,x in enumerate(c) if '$1' in x['title'] and not x['checked']];print(r[0] if r else '')")
  [ -n "$i" ] && curl -s "localhost:8765/tap?i=$i" >/dev/null
}
# 뒷좌석에서 지금 카드의 퀴즈를 푸는 것처럼: $1=right면 정답, wrong이면 오답을 누른다
kidquiz() {
  local a; a=$(curl -s localhost:8765/state | python -c "
import json,sys;s=json.loads(sys.stdin.buffer.read().decode('utf-8'));c=s.get('study') or {}
q=json.load(open('app/src/main/assets/script_demo.json',encoding='utf-8'))['quizzes'].get(c.get('title',''))
print('' if not q or (c.get('quiz') or {}).get('chosen',0)>=0 else (q['a'] if '$1'=='right' else 1-q['a']))")
  [ -n "$a" ] && curl -s "localhost:8765/quiz?a=$a" >/dev/null
}
kidask() { curl -s "localhost:8765/ask?k=$1" >/dev/null; }
scene() { echo; echo "== $*"; }

cd "$(dirname "$0")"
"$ADB" $E forward tcp:8088 tcp:8088 >/dev/null; "$ADB" $E forward tcp:8765 tcp:8765 >/dev/null
( while true; do curl -s -m 2 -X POST "$V/browser/session?client=video" >/dev/null; sleep 1; done ) & HB=$!
trap 'kill $HB; put "controls?speed=0"; put "gear?gear=Park"; put "body?door_rr=0&front_wiper=Off"' EXIT

scene "0. 준비: 빈 차, 친구는 자는 중"
put "gear?gear=Park"; put "lights?feature=headlamp_mode&value=Off"; put "body?door_rr=0&front_wiper=Off"
put "seat?area=rear_right&kind=occupied&value=false"
"$ADB" $E shell am force-stop kr.gildongmu.bingo
# 영상용 초기화: 키우기·도감·일기장만 비운다(친구 선택·관광정보 저장은 남김). 에뮬레이터 앱 사용자 = 10번.
MSYS_NO_PATHCONV=1 "$ADB" $E shell "run-as kr.gildongmu.bingo --user 10 sh -c 'rm -f shared_prefs/growth.xml shared_prefs/dex.xml shared_prefs/diary.xml'"
app --ez script true --ei diary "$DIARY" --ei step_ms 5000; sleep 6

scene "1. 아이 탑승: 친구가 깨고, 출석 도장, 무드등이 친구 색으로"
put "seat?area=rear_right&kind=occupied&value=true"; sleep 12

scene "2. 목적지 입력: 빙고판 + 가는 길 공부 고르기(역사 → 과학·자연 → 맛집)"
app --ez demo_route true; sleep 10
app; sleep 8
tap 1242 685; sleep 1; tap 1542 685; sleep 1; tap 2146 685; sleep 3
tap 1569 934; sleep 6

scene "3. 출발: 달리는 동안 앞 화면은 숫자만, 뒷좌석에 공부 카드"
put "gear?gear=Drive"; put "controls?speed=90"
app --ez replay true; sleep 14
kidquiz right; sleep 8

scene "4. 터널(지도 기반): 정지산터널"
app --es at "36.46882,127.11708"; sleep 9
app --es at "37.40000,127.10000"; sleep 6

scene "5. 비: 와이퍼 → 친구가 우산"
put "body?front_wiper=Low"; sleep 10; put "body?front_wiper=Off"; sleep 2

scene "6. 아이가 할머니께 질문(태블릿에서 누름)"
kidtap "할머니"; sleep 8
kidquiz wrong; sleep 6

scene "6-1. 아이가 '화장실 가고 싶어' → 운전자에게 다음 휴게소 거리를 소리로"
kidask toilet; sleep 12

scene "7. 휴게소: 도장 + 음식"
app --ez to_rest true; sleep 3
put "controls?speed=0"; put "gear?gear=Park"; sleep 3; put "body?door_rr=1"; sleep 3; put "body?door_rr=0"; sleep 8
kidtap "사 먹기"; sleep 8
kidquiz right; sleep 4

scene "8. 다시 출발 → 도착"
put "gear?gear=Drive"; put "controls?speed=90"; sleep 100

scene "9. 도착: 결과 화면 + AI 일기"
put "controls?speed=0"; put "gear?gear=Park"; sleep 15
echo "끝. 앞 화면과 뒷좌석 태블릿에 결과·일기가 떠 있어요."
