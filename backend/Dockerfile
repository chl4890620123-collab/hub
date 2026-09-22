# build strict TypeScript first, then package Spring Boot; runtime contains only the compiled JAR.
FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run typecheck && npm run build

# The real UI: React SPA, built to static files and served by Spring Boot itself (same origin as
# /api/**, so the default empty VITE_API_BASE_URL just means "this origin" - no CORS needed).
FROM node:22-alpine AS web-build
WORKDIR /workspace/web
COPY web/package.json web/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY web/ ./
RUN npm run build

FROM gradle:8.14-jdk21-ubi9 AS backend-build
WORKDIR /workspace/backend
COPY --chown=gradle:gradle backend/build.gradle backend/settings.gradle backend/gradle.properties ./
COPY --chown=gradle:gradle backend/src ./src
COPY --from=frontend-build /workspace/frontend/dist/hub-runtime.js ./src/main/resources/static/js/hub-runtime.js
COPY --from=frontend-build /workspace/frontend/dist/login.js ./src/main/resources/static/js/login.js
COPY --from=web-build /workspace/web/dist/ ./src/main/resources/static/
RUN gradle --no-daemon clean test bootJar

FROM eclipse-temurin:21-jre
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8 JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Duser.language=ko -Duser.country=KR"
WORKDIR /app
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 hub
RUN mkdir -p /app/data/storage
COPY --from=backend-build /workspace/backend/build/libs/hub-backend.jar /app/hub.jar
RUN chown -R hub:hub /app
USER hub
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=10 \
  CMD curl --fail --silent http://127.0.0.1:8080/actuator/health >/dev/null || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/hub.jar"]
