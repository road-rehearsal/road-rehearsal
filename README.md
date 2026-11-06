# 도로주행 연습 코치 (가칭)

실제 시험 코스와 채점 항목으로, 차가 내 조작을 봐 주는 도로주행 연습 앱입니다. Pleos Connect(Android Automotive OS)용입니다.

> 연습용 앱입니다. 공식 채점이나 합격을 보장하지 않습니다. 현재 앱 골격 단계입니다.

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
- `MainActivity`: 시작·중지와 현재 상태 표시.

## 테스트 환경
에뮬레이터와 Virtual Console(`adb forward tcp:8088 tcp:8088`)로 검증합니다. 실차에서는 시험하지 않았습니다.
