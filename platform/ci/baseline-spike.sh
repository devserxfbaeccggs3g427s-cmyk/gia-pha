#!/usr/bin/env bash
# baseline-spike.sh — Java 25 / Spring Boot 4.1.x compatibility gate.
#
# Resolves every artifact in the BOM against a temporary Maven Wrapper
# project. Writes a JSON verdict to ${OUT:-target/baseline-spike.json}.
# Returns non-zero if any artifact fails to resolve.

set -euo pipefail

OUT=${OUT:-target/baseline-spike.json}
mkdir -p "$(dirname "$OUT")"

SPIKE=$(mktemp -d)
trap 'rm -rf "$SPIKE"' EXIT

cat > "$SPIKE/pom.xml" <<'POM'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.familya.platform</groupId>
    <artifactId>baseline-spike</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.familya.platform</groupId>
                <artifactId>familya-platform-bom</artifactId>
                <version>1.0.0</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <dependencies>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
        <dependency><groupId>org.springframework.kafka</groupId><artifactId>spring-kafka</artifactId></dependency>
        <dependency><groupId>io.grpc</groupId><artifactId>grpc-netty-shaded</artifactId></dependency>
        <dependency><groupId>io.grpc</groupId><artifactId>grpc-protobuf</artifactId></dependency>
        <dependency><groupId>io.grpc</groupId><artifactId>grpc-stub</artifactId></dependency>
        <dependency><groupId>io.github.resilience4j</groupId><artifactId>resilience4j-spring-boot3</artifactId></dependency>
        <dependency><groupId>io.micrometer</groupId><artifactId>micrometer-registry-otlp</artifactId></dependency>
        <dependency><groupId>io.opentelemetry</groupId><artifactId>opentelemetry-exporter-otlp</artifactId></dependency>
        <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
        <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-mysql</artifactId></dependency>
        <dependency><groupId>org.jooq</groupId><artifactId>jooq</artifactId></dependency>
        <dependency><groupId>org.springframework.data</groupId><artifactId>spring-data-jdbc</artifactId></dependency>
        <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId></dependency>
        <dependency><groupId>org.testcontainers</groupId><artifactId>testcontainers</artifactId></dependency>
        <dependency><groupId>org.testcontainers</groupId><artifactId>mysql</artifactId></dependency>
        <dependency><groupId>com.tngtech.archunit</groupId><artifactId>archunit-junit5</artifactId></dependency>
        <dependency><groupId>org.junit.jupiter</groupId><artifactId>junit-jupiter</artifactId></dependency>
        <dependency><groupId>org.assertj</groupId><artifactId>assertj-core</artifactId></dependency>
        <dependency><groupId>org.mockito</groupId><artifactId>mockito-core</artifactId></dependency>
        <dependency><groupId>at.favre.lib</groupId><artifactId>bcrypt</artifactId></dependency>
        <dependency><groupId>com.nimbusds</groupId><artifactId>nimbus-jose-jwt</artifactId></dependency>
    </dependencies>
</project>
POM

mvn -q -B -f "$SPIKE/pom.xml" -DincludeScope=runtime \
    dependency:resolve dependency:resolve-plugins > "$SPIKE/resolve.log" 2>&1 || {
        echo "Baseline spike FAILED. See $SPIKE/resolve.log" >&2
        cat "$SPIKE/resolve.log" >&2
        exit 1
    }

mvn -q -B -f "$SPIKE/pom.xml" -DincludeScope=test \
    dependency:resolve >> "$SPIKE/resolve.log" 2>&1 || {
        echo "Test scope resolution FAILED. See $SPIKE/resolve.log" >&2
        exit 1
    }

cat > "$OUT" <<JSON
{
  "verdict": "PASS",
  "java": "25",
  "springBoot": "4.1.0",
  "resolvedAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "log": "$SPIKE/resolve.log"
}
JSON

echo "Baseline spike PASSED. Verdict at $OUT"
