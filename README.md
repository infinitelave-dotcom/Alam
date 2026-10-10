# 할일 알람 (Android)

알람이 울리면 **할일이 화면 가득 엄청 크게** 보이는 안드로이드 알람 앱입니다.

## 기능
- 알람마다 시간 + 할일 + 반복 요일 설정
- 울릴 때 **잠금화면 위로 화면이 켜지며** 전체화면에 할일을 노란 글씨로 화면에 꽉 차게 표시
  (글자 수에 맞춰 자동으로 최대 크기, 배경 검정↔빨강 깜빡임)
- 큰 `✔ 완료 · 끄기` / `5분 뒤 다시` 버튼 (뒤로가기로는 안 꺼짐)
- 시스템 알람 소리(알람 볼륨) 반복 + 진동, 10분 후 자동 종료
- 알림창에서도 완료/5분 뒤 가능
- 시스템 알람시계 방식(`setAlarmClock`)이라 절전·잠금 상태에서도 정확히 울림
- 재부팅/앱 업데이트/시간 변경 후 자동 재예약

## 설치
1. GitHub 저장소의 **Releases** 에서 최신 `TodoAlarm.apk` 를 휴대폰으로 다운로드
   (또는 Actions → 최근 실행 → Artifacts)
2. 열어서 설치 ("출처를 알 수 없는 앱" 설치 허용 필요)
3. 앱을 처음 열면 알림 권한 허용. 화면 위에 노란 경고가 보이면 눌러서 권한을 켜주세요.

## 직접 빌드
Android Studio 로 이 폴더를 열거나:
```
./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
```

## 구조
- `app/src/main/java/com/alam/todoalarm/`
  - `AlarmActivity` — 울릴 때 뜨는 전체화면 (할일 크게 표시)
  - `AlarmService` — 소리/진동/전체화면 알림
  - `AlarmScheduler` / `AlarmReceiver` — 알람 예약·발동, 부팅 시 재예약
  - `MainActivity` / `EditActivity` — 목록, 추가/수정
- `web/` — 이전 웹앱 버전
