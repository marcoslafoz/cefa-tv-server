# ==============================================================================
# CEFA TV — Makefile global (orquestador monorepo)
# Plantilla de cefa-dev-skills (reglas en docs/llm/rules/common/makefile.md).
# Sustituye PROJECTS, STOP_ORDER (PROJECTS en orden inverso) y NETWORK_NAME. Solo delega: no contiene lógica de build propia.
# ==============================================================================

PROJECTS := cefa-tv-core cefa-tv-app
NETWORK_NAME := cefa-tv-net
STOP_ORDER := cefa-tv-app cefa-tv-core

.PHONY: help build up down rebuild format lint test check clean prune

help: ## Muestra los comandos disponibles y una breve descripción
	@echo "CEFA TV — Orquestador de desarrollo local"
	@echo "Uso: make [objetivo] (ejecuta en todos los proyectos) o cd cefa-tv && make [objetivo]"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'
	@echo ""

build: ## Construye los artefactos de todos los proyectos
	@for p in $(PROJECTS); do echo "Construyendo $$p..."; $(MAKE) -C $$p build || exit 1; done

up: ## Levanta todos los entornos con Docker Compose (asegura la red compartida)
	@docker network inspect $(NETWORK_NAME) >/dev/null 2>&1 || docker network create $(NETWORK_NAME)
	@for p in $(PROJECTS); do echo "Levantando $$p..."; $(MAKE) -C $$p up || exit 1; done

down: ## Detiene todos los entornos Docker (en orden inverso)
	@for p in $(STOP_ORDER); do echo "Deteniendo $$p..."; $(MAKE) -C $$p down || exit 1; done

rebuild: ## Reconstruye las imágenes de todos los stacks y los vuelve a levantar
	@docker network inspect $(NETWORK_NAME) >/dev/null 2>&1 || docker network create $(NETWORK_NAME)
	@for p in $(PROJECTS); do echo "Reconstruyendo $$p..."; $(MAKE) -C $$p rebuild || exit 1; done

format: ## Aplica el formateo de código en todos los proyectos
	@for p in $(PROJECTS); do echo "Formateando $$p..."; $(MAKE) -C $$p format || exit 1; done

lint: ## Ejecuta las comprobaciones de lint en todos los proyectos
	@for p in $(PROJECTS); do echo "Verificando lint en $$p..."; $(MAKE) -C $$p lint || exit 1; done

test: ## Ejecuta las pruebas en todos los proyectos
	@for p in $(PROJECTS); do echo "Ejecutando pruebas de $$p..."; $(MAKE) -C $$p test || exit 1; done

check: ## Ejecuta las comprobaciones completas en todos los proyectos
	@for p in $(PROJECTS); do echo "Validando $$p..."; $(MAKE) -C $$p check || exit 1; done

clean: ## Limpia artefactos temporales en todos los proyectos
	@for p in $(PROJECTS); do echo "Limpiando $$p..."; $(MAKE) -C $$p clean || exit 1; done

prune: ## Limpia contenedores, imágenes y redes no utilizados de Docker (docker system prune -af)
	@echo "Limpiando recursos no utilizados de Docker..."
	@docker system prune -af
