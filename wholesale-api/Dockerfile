# 도매 API 배포 이미지.
#
# 2단계로 나눈다. 빌드에 쓰는 JDK·그래들·소스는 1단계에만 있고,
# 실제로 돌아가는 이미지에는 JRE 와 jar 만 남는다. 이미지가 작고, 소스가 안 실린다.

# ── 1단계 · 빌드 ────────────────────────────────────────────
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 빌드 스크립트를 먼저 넣고 의존성만 받아둔다.
# 소스만 고쳤을 때 이 층이 캐시로 남아서 라이브러리를 다시 안 받는다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies

COPY src src
RUN ./gradlew --no-daemon bootJar

# ── 2단계 · 실행 ────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app

# root 로 안 돌린다. 앱이 뚫려도 컨테이너 안에서 할 수 있는 일이 줄어든다.
RUN useradd --system --create-home --shell /usr/sbin/nologin ondo
USER ondo

COPY --from=build /app/build/libs/*.jar app.jar

# 프로필은 여기서 안 박는다 (MUL-104).
#
# 전에는 ENV SPRING_PROFILES_ACTIVE=prod 가 있었다. 그때는 배포 프로필이 하나뿐이라
# 문제가 없었지만, 지금은 dev 와 prod 가 갈렸다. 이미지에 박아 두면 같은 이미지를
# 두 환경에 못 쓰거나, 더 나쁘게는 개발 환경이 자기를 운영이라 부른다.
#
# 어느 환경인지는 태스크 정의(infra/ecs.tf)가 SPRING_PROFILES_ACTIVE 로 준다.
# 안 주면 기본값 local 로 떠서 컨테이너 안 localhost 를 보다 기동에 실패한다 —
# 조용히 잘못된 설정으로 도는 것보다 낫다.

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
