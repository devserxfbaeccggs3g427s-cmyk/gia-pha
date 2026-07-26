SHELL := /bin/bash
# Convenience targets for the microservice-decomposition research lane.
# Each service is a fully independent Maven project (Spring Boot parent 4.1.0
# declared directly). No service-common, no services-parent, no shared module.

COMPOSE        := docker compose -f infra/docker-compose.yml
SERVICES_DIR   := services
SERVICES_LIST  := identity-service audit-service tree-service members-service \
                  relationships-service events-service media-metadata-service \
                  binary-storage-service sharing-service reporting-service \
                  transfer-service gateway-service

.PHONY: help up down down-volumes logs ps ca ca-rotate seed bench \
        build-services build-all build-% test-% validate

help: ## Show this help.
	@awk 'BEGIN{FS=":.*##"} /^[a-zA-Z_-]+:.*##/{printf "  %-18s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

up: ## Start the foundation infra + all 12 microservices + gateway.
	$(COMPOSE) up -d --build
	@echo "Waiting for infra + services..."
	@for svc in mysql rabbitmq prometheus grafana; do \
	  $(COMPOSE) wait || true; \
	done

down: ## Stop all containers.
	$(COMPOSE) down

down-volumes: ## Stop and delete volumes (DESTRUCTIVE).
	$(COMPOSE) down -v

logs: ## Tail logs from all containers.
	$(COMPOSE) logs -f --tail=200

ps: ## Show container status.
	$(COMPOSE) ps

build-services: ## Build every service independently (mvn -f services/<svc>/pom.xml).
	@for svc in $(SERVICES_LIST); do \
	  echo ">> building $$svc"; \
	  mvn -q -f $(SERVICES_DIR)/$$svc/pom.xml -DskipTests package || exit 1; \
	done

build-all: build-services ## Build all services.

build-%: ## Build a single service. e.g. `make build-identity-service`.
	mvn -f $(SERVICES_DIR)/$*/pom.xml -DskipTests package

test-%: ## Test a single service. e.g. `make test-identity-service`.
	mvn -f $(SERVICES_DIR)/$*/pom.xml test

validate: ## Validate compose file + every service POM is well-formed XML.
	@$(COMPOSE) config -q && echo "compose: ok"
	@python3 -c "import os, xml.etree.ElementTree as ET; \
	  [ET.parse(os.path.join('$(SERVICES_DIR)', d, 'pom.xml')) for d in os.listdir('$(SERVICES_DIR)') if os.path.exists(os.path.join('$(SERVICES_DIR)', d, 'pom.xml'))]; \
	  print('services: ok')"

ca: ## Generate the local CA and per-service leaf certs.
	./tools/ca/make-ca.sh

ca-rotate: ## Rotate a single service's leaf cert.
	./tools/ca/rotate-cert.sh $(SERVICE)

seed: ## Apply the import seed.
	./infra/seed/run.sh

bench: ## Run the workload benchmark (Phase 7.1).
	./tools/bench/run.sh
