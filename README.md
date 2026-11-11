# 로드 리허설

실제 시험 코스와 채점 항목으로, 차가 내 조작을 봐 주는 도로주행 연습 앱입니다. Pleos Connect(Android Automotive OS)용입니다.

> 연습용 앱입니다. 공식 채점이나 합격을 보장하지 않습니다. 현재 개발 중입니다.

## 설치 파일(APK)로 바로 실행
서명된 release APK: [`apk/안승권_RoadRehearsal_release.apk`](apk/)(minSdk 28, targetSdk 34). Pleos Connect Emulator 2.1.0에서 앱은 user 10으로 실행되므로 설치·권한·실행에 모두 `--user 10`을 붙입니다.

```bash
adb install -r --user 10 apk/안승권_RoadRehearsal_release.apk
adb shell pm grant --user 10 com.roadpractice.coach android.car.permission.CAR_SPEED
adb shell pm grant --user 10 com.roadpractice.coach pleos.car.permission.FUSED_LOCATION
adb shell am start --user 10 -n com.roadpractice.coach/.MainActivity
```
- 나머지 Pleos 권한(문·주행·등화·안전, 음성)은 매니페스트 선언만으로 씁니다(개발 단계 규칙). 알림 권한은 첫 실행 때 묻습니다.
- release APK에는 아래 **시연용 재생이 없습니다.** 차량 신호는 에뮬레이터의 Virtual Console(`adb forward tcp:8088 tcp:8088` 뒤 http://127.0.0.1:8088/)로 넣습니다. 기어·브레이크·방향지시등·문·안전띠·속도를 바꾸면 그대로 채점됩니다. 시연 영상의 주행 장면은 debug 빌드의 시연용 재생으로 찍었고 화면에 "시연용 재생"이라고 표시됩니다.

## 소스로 빌드해 실행
요구 사항: JDK 17, Android SDK, Pleos Connect Emulator 2.1.0

```bash
./gradlew testDebugUnitTest
./gradlew installDebug
adb shell pm grant --user 10 com.roadpractice.coach android.car.permission.CAR_SPEED
adb shell pm grant --user 10 com.roadpractice.coach pleos.car.permission.FUSED_LOCATION
adb shell am start --user 10 -n com.roadpractice.coach/.MainActivity
adb logcat -s RoadCoach:I
```

## 화면
Jetpack Compose와 Material 3로 만들었습니다. 디자인 기준은 [DESIGN.md](DESIGN.md)이고, 앱 창이 넓으면 두 판, 좁으면 한 줄로 놓습니다. 글꼴 Pretendard는 SIL Open Font License 1.1입니다([licenses/Pretendard-OFL.txt](licenses/Pretendard-OFL.txt)).

## 구조
- `SignalHub`: 차량 신호와 위치를 `VehicleState` 하나로 모읍니다. 차량 제어는 호출하지 않습니다.
- `CoachService`: 포그라운드 서비스. 내비가 앞에 있어도 신호 수신이 멈추지 않게 합니다.
- `ExamRules`, `CoachSession`: 차량 신호로 판정하는 17개 항목(5번 미출발 포함)과 두 모드(시험은 침묵, 연습은 한 문장).
- `ScoreSheet`: 차가 본 항목, 동승자 체크 26개(43번 포함), 실격 사유를 합친 결과표(100점에서 감점, 70점 합격선).
- `ExamLog`: SQLite 기록. 감점이 생길 때마다 저장하고, 정차 후 체크와 제외 표시를 저장합니다. 위치나 개인정보는 저장하지 않습니다.
- `CourseTracker`, `CourseLoader`: 코스 진행, 이탈, 지점(서행·일시정지·횡단보도·유턴) 판정과 안내. 지점은 코스 거리로 판정해 왕복 도로에서 중복되지 않습니다.
  - 채점 항목 보기(11/8): 처음 화면에서 별표 26의 46개 항목과 실격 사유 11개를 판정 방식(차가 자동·코스 지점·동승자 체크·보지 않음)별로 봅니다. 정차 중에만 열림. 목록은 `ItemCatalog.kt`가 ExamItem·PassengerItem에서 만들고 `ItemCatalogTest`가 원문 번호·감점과 대조합니다.
  - 길 안내: 좌·우회전·유턴을 300m, 속도에 맞춘 가까운 거리(속도×2.222+37m, 80~200m, FHWA-RD-98-057 5장), 회전 약 3초 앞("지금 좌회전해요")에 음성으로 말하고, 화면에 화살표와 남은 거리를 띄웁니다(두 모드 모두). 회전 앞에서 10초 넘게 섰다가 출발하면 한 번 더, 다음 회전이 10초 안이면 "우회전 뒤 바로 좌회전"으로 묶고 화살표를 두 개 띄웁니다. 연습 모드에서는 제한속도를 넘는 순간 말하고, 횡단보도는 좌·우회전하는 곳에 있을 때만 예고합니다. 돌아야 할 곳을 지나치면(회전 지점 근처에서 코스 선과 30m 넘게 떨어지면) "지점을 지났어요"라고 알립니다.
  - 구간: 제한속도 구간과 어린이보호구역에서 제한속도 표지를 띄우고, 보호구역 제한속도 초과·지정 제한속도 10km/h 초과는 실격으로 기록합니다(코스 자료에 구간이 있을 때만. 차량 신호의 제한속도는 에뮬레이터에서 0만 옵니다).
  - 5번 "20초 내 미출발": 기어를 P에서 뺀 뒤 20초 안에 움직이지 않으면 감점합니다(처음 출발만).
  - 결과표에 코스 지점별 결과(회전 안내, 횡단보도를 지날 때 속도(참고), 구간 최고 속도)와 같은 코스 지난번과의 감점 비교를 보여 줍니다.
  - 횡단보도: 연습 모드에서 "보행자를 확인해요" 안내만 하고 감점하지 않습니다. 43번(횡단보도 직전 일시정지)은 정차 후 동승자 체크입니다. 앱은 신호 색과 보행자를 볼 수 없기 때문입니다.
- 내비 등 다른 화면이 앞에 있어도 `CoachService`가 판정을 계속합니다. 끝나면 음성과 알림으로 알리고, 알림이나 하단 바 아이콘으로 결과표에 돌아옵니다. 주행 중 앱이 멈추면 다음 실행 때 그때까지의 기록을 "중간에 멈춤"으로 보여 줍니다.
- `MainActivity`: 코스 선택(뽑기는 덜 해 본 코스 먼저), 모드 선택, 주행 중 안내, 종료 후 결과표와 동승자 체크, 최근 기록. 주행 중에는 입력을 받지 않습니다.

## 코스 자료
`app/src/main/assets/courses.json`. 시험장이 공개한 코스 순서를 보고 OpenStreetMap 도로로 그린 연습용 좌표입니다. 공단이 준 자료가 아닙니다. 서행·일시정지 지점과 횡단보도 위치는 시연용 가상입니다.

Map data © OpenStreetMap contributors, ODbL.

### 내가 기록한 코스
폰으로 기록한 길로 만든 코스는 저장소에 넣지 않고(사용자 위치) 기기 안 `files/courses_local.json`에 둡니다. 형식은 `courses.json`과 같고, 지점에 `LEFT`·`RIGHT`·`SPEED`·`SCHOOL`과 `limit`(km/h)을 쓸 수 있습니다. 코스 고르기 화면에 "내 기록 ○km"로 따로 나옵니다.

```bash
adb push courses_local.json /data/local/tmp/
adb shell "run-as com.roadpractice.coach --user 10 cp /data/local/tmp/courses_local.json files/"
```

## 시연용 재생 (디버그 빌드만)
에뮬레이터는 차량 속도를 밖에서 넣을 수 없어(`inject-vhal-event`는 user 빌드에서 막힘), 폰으로 기록한 CSV(시각·위도·경도·속도)를 앱이 차량 신호 대신 넣는 재생 모드가 있습니다. 화면에 **"시연용 재생"**이라고 표시하며 release 빌드에서는 꺼집니다.

```bash
adb push drive.csv /data/local/tmp/ && adb shell "run-as com.roadpractice.coach --user 10 cp /data/local/tmp/drive.csv files/"
adb shell am start --user 10 -n com.roadpractice.coach/.MainActivity --es replay drive.csv --es course <코스 id> --es mode EXAM --ei replaySpeed 4
```

## 시험
- `./gradlew testDebugUnitTest`: 판정·코스·결과표·채점 항목 단위 시험(127개, 11/11).
- 실제 주행 기록으로 돌려 보기: `./gradlew testDebugUnitTest -PreplayDir=<기록 폴더>`. 폴더에 기록 CSV와 코스 JSON이 없으면 이 시험은 건너뜁니다.

## release 빌드
`keystore.properties`(storeFile, storePassword, keyAlias, keyPassword)와 키 파일을 저장소 밖 또는 git 제외로 두고 `./gradlew assembleRelease`. 없으면 서명 없이 빌드됩니다. 결과 `app/build/outputs/apk/release/app-release.apk`를 `apk/안승권_RoadRehearsal_release.apk`로 복사합니다. 서명 확인: `apksigner verify -v --print-certs <apk>`.

## 테스트 환경
에뮬레이터와 Virtual Console(`adb forward tcp:8088 tcp:8088`)로 검증합니다. 실제 주행 기록은 폰으로 모은 뒤 위 재생으로 확인했습니다. 실차에 앱을 설치해 시험하지는 않았습니다.
