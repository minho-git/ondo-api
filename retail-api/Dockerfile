# 소매 API 배포 이미지.
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

# 이 이미지는 배포용이다. 기본값이 local 이면 컨테이너 안의 localhost 를 봐서
# DB 를 못 찾는다 — 배포 프로필을 여기서 박아 둔다.
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
