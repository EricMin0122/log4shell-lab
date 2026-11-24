# Log4Shell 비교 실습 랩 (CVE-2021-44228)

Log4j 2 취약점이 **log4j 버전**과 **JDK 버전**에 따라 어떻게 다르게 동작하는지
4개 조건으로 비교하는 격리 실습 환경. (컴퓨터공학 특론 팀 프로젝트)

## 저장소 구성
- `docker-compose.yml` — 피해자 3종 (담당 A)
- `victim-newjdk/` — victim-v2 빌드 (JDK 8u212)
- `attacker/` — 공격자 컨테이너 (담당 B, 추후)
- `verify/` — 지표 수집·결과 (담당 C, 추후)
- `docs/victim-handoff.md` — 상세 인계 문서

## 빠른 시작
```
docker compose build victim-v2
docker compose up -d victim-v1 victim-v2 victim-v3
docker compose ps
```

## 조건 / 피해자 매핑
| 서비스 | 조건 | log4j | JDK | 기대 동작 |
|---|---|---|---|---|
| victim-v1 | ① | 2.14.1 | 8u102 | JNDI 질의 + 원격 클래스 로딩(RCE) |
| victim-v2 | ②③ | 2.14.1 | 8u212 | JNDI 질의 O, 원격 로딩 X / 가젯 체인 |
| victim-v3 | ④ | 2.17.1 | 11 | JNDI 질의 자체 차단 |

네트워크는 `internal: true`로 외부 egress가 차단된 격리 상태입니다. 공격자 연결
규약·검증 방법·구성 내역은 `docs/victim-handoff.md`를 참고하세요.

> 격리된 실습 환경에서의 재현 목적. 외부 실제 시스템 대상 금지.
