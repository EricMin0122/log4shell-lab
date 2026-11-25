# Log4Shell 비교 실습 랩 (CVE-2021-44228)

Log4j 2 취약점이 **log4j 버전**과 **JDK 버전**에 따라 어떻게 다르게 동작하는지
4개 조건으로 비교하는 격리 실습 환경. (컴퓨터공학 특론 팀 프로젝트)

> 운반체: **Tomcat 9 + 최소 취약 WAR**. (이전 Solr 기반에서 전환 — 조건 ③ 로컬 가젯 체인이
> Tomcat 의 `BeanFactory`+`EL` 을 필요로 하기 때문. Solr/Jetty 에는 해당 가젯이 없음.)

## 저장소 구성
- `docker-compose.yml` — 피해자 3종 (담당 A)
- `vuln-app/` — 공통 취약 웹앱(요청 로깅, log4j 번들)
- `victim-v1|v2|v3/` — 조건별 Dockerfile (JDK/log4j 조합)
- `docs/victim-handoff.md` — 상세 인계 문서 (B·C용, **변경 요약 포함**)
- `attacker/` — 공격자 컨테이너 (담당 B, 추후)
- `verify/` — 지표 수집·결과 (담당 C, 추후)

## 빠른 시작
```
docker compose build
docker compose up -d
docker compose ps
```

## 조건 / 피해자 매핑
| 서비스 | 조건 | log4j | JDK | 기대 동작 |
|---|---|---|---|---|
| victim-v1 | ① | 2.14.1 | 8u181 | JNDI 질의 + 원격 클래스 로딩(RCE) |
| victim-v2 | ②③ | 2.14.1 | 8u212 | 원격 로딩 X / BeanFactory 로컬 가젯 RCE |
| victim-v3 | ④ | 2.17.1 | 11 | JNDI 질의 자체 차단 |

- ②③ 는 동일 victim-v2, 페이로드만 차이. ①↔② 는 JDK 패치레벨(8u191 경계)만 차이.
- 트리거: `http://victim-vN:8080/?x=<payload>` 또는 `User-Agent` 헤더.

네트워크는 `internal: true` 로 외부 egress 가 차단된 격리 상태. 공격자 연결 규약·검증 방법·
구성 내역은 `docs/victim-handoff.md` 참고.

> 격리된 실습 환경에서의 재현 목적. 외부 실제 시스템 대상 금지.
