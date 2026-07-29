#!/usr/bin/env bash
# service-bootstrap.sh — generate the standard service skeleton
# (pom.xml, Flyway V1__init, application.yml, ArchUnit test, OWNERS,
# pipeline, helm values, Dockerfile) for a new service under
# services/<name>/. The script is idempotent: existing files are
# left untouched. Used by the GitOps repo and the CLI bootstrapper
# to onboard a new service without re-typing the boilerplate.
#
# Usage:
#   tools/service-bootstrap.sh <service-name> <base-package> <port> <db-name> <topology>
#
# Example:
#   tools/service-bootstrap.sh member-service com.familya.member 8080 member tree

set -euo pipefail

SERVICE=${1:?service name required}
PKG=${2:?base package required}
PORT=${3:-8080}
DB=${4:-$(echo "$SERVICE" | tr -d '-')}
TOPOLOGY=${5:-tree}

ROOT=$(cd "$(dirname "$0")/.." && pwd)
SVC_DIR="$ROOT/services/$SERVICE"
PKG_PATH=$(echo "$PKG" | tr '.' '/')
mkdir -p "$SVC_DIR/src/main/java/$PKG_PATH"
mkdir -p "$SVC_DIR/src/main/resources/db/migration"
mkdir -p "$SVC_DIR/src/test/java/$PKG_PATH/archunit"
mkdir -p "$SVC_DIR/deploy/helm"
mkdir -p "$SVC_DIR/pipeline"

if [ ! -f "$SVC_DIR/pom.xml" ]; then
cat > "$SVC_DIR/pom.xml" <<POM
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>${PKG}</groupId>
    <artifactId>${SERVICE}</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>jar</packaging>
    <name>Family Tree — ${SERVICE}</name>
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
    </dependencies>
    <dependencies>
        <dependency><groupId>com.familya.platform</groupId><artifactId>familya-platform-spring-boot-starter</artifactId></dependency>
        <dependency><groupId>com.familya.platform</groupId><artifactId>familya-platform-outbox-starter</artifactId></dependency>
        <dependency><groupId>com.familya.platform</groupId><artifactId>familya-platform-security-starter</artifactId></dependency>
        <dependency><groupId>com.familya.platform</groupId><artifactId>familya-platform-observability-starter</artifactId></dependency>
        <dependency><groupId>com.familya.platform</groupId><artifactId>familya-platform-resilience-starter</artifactId></dependency>
        <dependency><groupId>com.familya.platform</groupId><artifactId>familya-platform-grpc-starter</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    </dependencies>
    <build>
        <finalName>\${project.artifactId}</finalName>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.flywaydb</groupId>
                <artifactId>flyway-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.jooq</groupId>
                <artifactId>jooq-codegen-maven</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
POM
fi

if [ ! -f "$SVC_DIR/src/main/resources/db/migration/V1__init.sql" ]; then
cat > "$SVC_DIR/src/main/resources/db/migration/V1__init.sql" <<SQL
-- V1__init.sql — ${SERVICE} schema authority (ADR-002, ADR-009).
-- Replace the placeholder tables below with the real schema.
CREATE TABLE ${DB}_placeholder (
    id            CHAR(36)     NOT NULL,
    created_at    TIMESTAMP(6) NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
SQL
fi

if [ ! -f "$SVC_DIR/src/main/resources/application.yml" ]; then
cat > "$SVC_DIR/src/main/resources/application.yml" <<YAML
spring:
  application:
    name: ${SERVICE}
  datasource:
    url: \${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/${DB}?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}
    username: \${SPRING_DATASOURCE_USERNAME:familya}
    password: \${SPRING_DATASOURCE_PASSWORD:familya}
  flyway:
    locations: classpath:db/migration

familya:
  outbox:
    relay: { enabled: false, interval-ms: 500 }
  grpc:
    server: { enabled: false, port: 9090 }
  kafka: { partitions: 12, replication-factor: 3 }
YAML
fi

if [ ! -f "$SVC_DIR/src/main/java/$PKG_PATH/Application.java" ]; then
mkdir -p "$SVC_DIR/src/main/java/$PKG_PATH"
cat > "$SVC_DIR/src/main/java/$PKG_PATH/Application.java" <<JAVA
package ${PKG};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = { "${PKG}", "com.familya.platform" })
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
JAVA
fi

if [ ! -f "$SVC_DIR/src/test/java/$PKG_PATH/archunit/ArchitectureTest.java" ]; then
cat > "$SVC_DIR/src/test/java/$PKG_PATH/archunit/ArchitectureTest.java" <<JAVA
package ${PKG}.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

@AnalyzeClasses(packages = "${PKG}", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest {
    @ArchTest static final ArchRule domain_does_not_depend_on_spring =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    @ArchTest static final ArchRule no_cross_service_imports =
            noClasses().that().resideInAPackage("${PKG}..")
                    .should().dependOnClassesThat(resideInAnyPackage(
                            "com.familya.identity..",
                            "com.familya.member..",
                            "com.familya.relationship..",
                            "com.familya.treeaccess..",
                            "com.familya.event..",
                            "com.familya.media..",
                            "com.familya.sharing..",
                            "com.familya.search..",
                            "com.familya.transfer..",
                            "com.familya.auditops..",
                            "com.familya.migration..")
                            .and(resideOutsideOfPackage("${PKG}..")));

    @ArchTest static final ArchRule no_cycles = slices().matching("${PKG}.(*)..").should().beFreeOfCycles();
}
JAVA
fi

if [ ! -f "$SVC_DIR/deploy/helm/values-prod.yaml" ]; then
cat > "$SVC_DIR/deploy/helm/values-prod.yaml" <<YAML
name: ${SERVICE}
namespace: familya-${SERVICE%%-service}
replicaCount: 2
image:
  repository: ghcr.io/familya/${SERVICE}
  digest: REPLACE_WITH_SIGNED_DIGEST
  pullPolicy: IfNotPresent
env:
  - name: SPRING_PROFILES_ACTIVE
    value: prod
  - name: SPRING_DATASOURCE_URL
    value: jdbc:mysql://familya-${SERVICE%%-service}.example.internal:3306/${DB}?sslMode=VERIFY_CA
  - name: SPRING_DATASOURCE_USERNAME
    value: ${SERVICE%%-service}
  - name: SPRING_KAFKA_BOOTSTRAP_SERVERS
    value: familya-example.kafka.example.com:9092
serviceAccount:
  name: ${SERVICE%%-service}-workload
keda:
  enabled: true
  lagThreshold: "50"
YAML
fi

if [ ! -f "$SVC_DIR/Dockerfile" ]; then
cat > "$SVC_DIR/Dockerfile" <<DOCKER
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
COPY --chown=65532:65532 target/${SERVICE}.jar /app/app.jar
USER 65532:65532
EXPOSE ${PORT}
ENTRYPOINT ["java","-jar","/app/app.jar"]
DOCKER
fi

if [ ! -f "$SVC_DIR/OWNERS" ]; then
cat > "$SVC_DIR/OWNERS" <<OWN
name: ${SERVICE}
team: ${SERVICE%%-service}
slack: "#familya-${SERVICE%%-service}"
oncall: ${SERVICE%%-service}-oncall@example.com
OWN
fi

if [ ! -f "$SVC_DIR/pipeline/build.yml" ]; then
cat > "$SVC_DIR/pipeline/build.yml" <<PIPE
name: ${SERVICE}
on:
  push:    { paths: [services/${SERVICE}/**, platform/starters/**] }
  pull_request: { paths: [services/${SERVICE}/**, platform/starters/**] }
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '25' }
      - run: mvn -B -ntp -f services/${SERVICE}/pom.xml -DskipTests package
      - run: mvn -B -ntp -f services/${SERVICE}/pom.xml -Dtest=ArchitectureTest test
      - run: mvn -B -ntp -f services/${SERVICE}/pom.xml jib:build
      - run: cosign sign --yes ghcr.io/familya/${SERVICE}:\${{ github.sha }}
PIPE
fi

if [ ! -f "$SVC_DIR/.mvn/wrapper/maven-wrapper.properties" ]; then
mkdir -p "$SVC_DIR/.mvn/wrapper"
cat > "$SVC_DIR/.mvn/wrapper/maven-wrapper.properties" <<WRAP
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip
wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
WRAP
cat > "$SVC_DIR/mvnw" <<'MVNW'
#!/usr/bin/env bash
exec mvn "$@"
MVNW
chmod +x "$SVC_DIR/mvnw"
fi

echo "Bootstrapped ${SERVICE} at ${SVC_DIR}"
