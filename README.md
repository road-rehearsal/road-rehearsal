# 도로주행 연습 코치 (가칭)

실제 시험 코스와 채점 항목으로, 차가 내 조작을 봐 주는 도로주행 연습 앱입니다. Pleos Connect(Android Automotive OS)용입니다.

> 연습용 앱입니다. 공식 채점이나 합격을 보장하지 않습니다. 현재 개발 중입니다.

## 실행
요구 사항: JDK 17, Android SDK, Pleos Connect Emulator 2.1.0

```bash
./gradlew testDebugUnitTest
./gradlew installDebug
adb shell pm grant --user 10 com.roadpractice.coach android.car.permission.CAR_SPEED
adb shell pm grant --user 10 com.roadpractice.coach pleos.car.permission.FUSED_LOCATION
adb shell am start --user 10 -n com.roadpractice.coach/.MainActivity
adb logcat -s RoadCoach:I
```

## 구조
- `SignalHub`: 차량 신호와 위치를 `VehicleState` 하나로 모읍니다. 차량 제어는 호출하지 않습니다.
- `CoachService`: 포그라운드 서비스. 내비가 앞에 있어도 신호 수신이 멈추지 않게 합니다.
- `ExamRules`, `CoachSession`: 차량 신호로 판정하는 16개 항목과 두 모드(시험은 침묵, 연습은 한 문장).
- `ScoreSheet`: 차가 본 항목, 동승자 체크 29개, 실격 사유를 합친 결과표(100점에서 감점, 70점 합격선).
- `ExamLog`: SQLite 기록. 감점이 생길 때마다 저장하고, 정차 후 체크와 제외 표시를 저장합니다. 위치나 개인정보는 저장하지 않습니다.
- `CourseTracker`, `CourseLoader`: 코스 진행, 이탈, 지점(서행·일시정지·횡단보도·유턴) 판정과 안내. 지점은 코스 거리로 판정해 왕복 도로에서 중복되지 않습니다.
- `MainActivity`: 코스 선택(뽑기는 덜 해 본 코스 먼저), 모드 선택, 주행 중 안내, 종료 후 결과표와 동승자 체크, 최근 기록. 주행 중에는 입력을 받지 않습니다.

## 코스 자료
`app/src/main/assets/courses.json`. 시험장이 공개한 코스 순서를 보고 OpenStreetMap 도로로 그린 연습용 좌표입니다. 공단이 준 자료가 아닙니다. 서행·일시정지 지점과 횡단보도 위치는 시연용 가상입니다.

Map data © OpenStreetMap contributors, ODbL.

## 테스트 환경
에뮬레이터와 Virtual Console(`adb forward tcp:8088 tcp:8088`)로 검증합니다. 실차에서는 시험하지 않았습니다.
