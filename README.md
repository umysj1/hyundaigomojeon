# 길동무 빙고

차가 스스로 칸을 채우는 가족 이동 빙고. 이번 길의 톨게이트·휴게소·터널로 빙고판을 만들고, 아이가 고른 도트 친구가 뒷좌석 태블릿에서 가는 길 이야기와 퀴즈를 들려주며 함께 자랍니다.

현대자동차그룹 차량용 앱 개발 공모전 출품작 (Pleos Connect 앱).

## APK

- `apk/김주원_길동무빙고_release.apk` (release 빌드, 관광정보 인증키 미포함)
- 설치: `adb install -r -g apk/김주원_길동무빙고_release.apk`

## 테스트 환경

| 항목 | 값 |
| --- | --- |
| 에뮬레이터 | Pleos Connect Emulator v2.1.0 (AVD `pleos_connect_21`) |
| 화면 규격 | 2560×1440 (가로) |
| Android | minSdk 28, targetSdk 35 |
| SDK | Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1 |
| 빌드 | JDK 21, Android SDK 35, Gradle 8.10.2, Kotlin |

## 실행 방법

1. 에뮬레이터 실행: `emulator -avd pleos_connect_21 -gpu host -no-snapshot -port 5580`
2. APK 설치(권한 자동 허용 `-g`): `adb -s emulator-5580 install -r -g apk/김주원_길동무빙고_release.apk`
3. 앱 실행 → 친구(강아지·고양이·아기 공룡)와 색 고르기
4. 내비에 목적지를 넣으면 빙고판이 만들어집니다. 에뮬레이터에서는 차가 경로 위를 움직이지 않으므로 시험 주행(안내 목록을 차례로 지난 것처럼 넣기, 화면에 "시뮬레이션 입력" 표시)으로 진행합니다.
   - 한 번에 보기: `bash demo.sh` (서울→전주 시나리오)
   - 시연 영상용 자동 진행: `bash demo_video.sh 1` (탑승→빙고→터널→비→공부 카드·퀴즈→부탁 버튼→휴게소 도감→도착 일기)
5. 차량 값(와이퍼·기어·문·착석·깜빡이)은 에뮬레이터 가상 콘솔로 바꾸면 실제 SDK를 거쳐 앱에 들어옵니다.

### 필요한 설정

- 권한: `CAR_WINDOWS`(와이퍼), `CONTROL_CAR_LIGHTS`(무드등) 등은 `install -g`로 자동 허용
- 뒷좌석 화면: 앱이 8765 포트로 웹 화면을 내보냅니다. 에뮬레이터에서는 `adb forward tcp:8765 tcp:8765` 후 PC 브라우저에서 http://127.0.0.1:8765 (실차에서는 차 화면의 QR을 같은 와이파이 태블릿으로 찍기)
- 관광정보: release APK에는 TourAPI 키가 없고, 시연 지역(전주) 관광정보가 `app/src/main/assets/study/`에 들어 있어 키 없이 공부 카드와 퀴즈가 나옵니다. 다른 지역을 쓰려면 `local.properties`에 `tourapi.key=...`를 넣고 빌드합니다.

## 소스에서 빌드

```
gradle clean assembleRelease -PnoTourKey
```

## 하는 일

- 빙고판: 내비 경로가 시작되면 출발 전에 받은 안내 목록(톨게이트·휴게소·IC 이름)으로 4×4(1시간 미만 3×3)를 만듭니다.
- 자동 체크: 안내 지점 통과(NaviHelper TBT), 비=와이퍼, 휴게소 쉬기=P+문 열림, 깜빡이 10번, 거리·시간, 도착.
- 터널: Pleos SDK에 터널 정보가 없어서 OpenStreetMap `tunnel=yes` 도로(assets/tunnels.json)와 현재 위치(NaviHelper)를 비교해 판정합니다.
- 도트 친구 키우기, 휴게소 도감, 도착 일기, 공부 카드 퀴즈, 뒷좌석 부탁 버튼, 일기장.
- 안전: 주행 중 앞 화면은 큰 숫자와 방금 채운 칸만, 알림은 음성(Pleos TextToSpeech). 빙고판 전체는 P에서만 앞 화면에 보입니다.
- 시연판의 친구 대사·일기 문장은 생성형 AI로 미리 만든 예시에 실제 주행 값을 채운 것이며, 화면에 "AI가 쓴 일기"로 표시합니다.

## 에뮬레이터에서 확인한 것

- 뒷좌석 착석은 앉으면 SDK가 false를 줍니다 → 반대로 읽습니다.
- 새 경로 직후 이전 경로 안내 목록이 한 번 더 옵니다 → 건너뜁니다.
- 터널 안내는 내비 안내 목록에 없습니다 → 지도 데이터로 판정합니다.
- 주행 중 화면은 `distractionOptimized` 메타데이터가 있어야 보입니다.

## 데이터 출처

- 관광정보: 한국관광공사 TourAPI (공공누리)
- 터널 도로: © OpenStreetMap contributors, ODbL
