# ==============================================================================
# CEFA TV App — Makefile de desarrollo local
# Plantilla de cefa-dev-skills (reglas en docs/llm/rules/common/makefile.md).
# ==============================================================================

APP_NAME := cefa-tv-app
NETWORK_NAME := cefa-tv-net

.PHONY: help build up down rebuild format format-check lint test check clean dev logs

help: ## Muestra los comandos disponibles y una breve descripción
	@echo "Uso: make [objetivo]"
	@echo ""
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'
	@echo ""

build: ## Construye la aplicación Next.js para producción
	@echo "Construyendo $(APP_NAME)..."
	@pnpm run build

up: ## Levanta el entorno local con Docker Compose (asegura la red compartida)
	@echo "Levantando entorno Docker para $(APP_NAME)..."
	@docker network inspect $(NETWORK_NAME) >/dev/null 2>&1 || docker network create $(NETWORK_NAME)
	@docker compose up -d

down: ## Detiene el entorno local de Docker
	@echo "Deteniendo entorno Docker de $(APP_NAME)..."
	@docker compose down

rebuild: ## Reconstruye las imágenes y vuelve a levantar los servicios
	@echo "Reconstruyendo y levantando servicios de $(APP_NAME)..."
	@docker network inspect $(NETWORK_NAME) >/dev/null 2>&1 || docker network create $(NETWORK_NAME)
	@docker compose up -d --build

format: ## Aplica el formateador configurado (Prettier)
	@echo "Aplicando formato con Prettier..."
	@pnpm run format

format-check: ## Comprueba el formato con Prettier sin modificar archivos
	@echo "Verificando formato con Prettier..."
	@pnpm prettier --check .

lint: ## Ejecuta las comprobaciones de lint (ESLint) sin modificar archivos
	@echo "Ejecutando ESLint en $(APP_NAME)..."
	@pnpm run lint

test: ## Ejecuta la batería de pruebas disponible en el repositorio
	@echo "Ejecutando tests de $(APP_NAME)..."
	@if grep -q '"test":' package.json; then pnpm test; else echo "No hay suite de pruebas configurada en $(APP_NAME)."; fi

check: format-check lint test ## Ejecuta de forma conjunta validaciones de formato, lint y pruebas
	@echo "Todas las comprobaciones han pasado satisfactoriamente."

clean: ## Elimina artefactos temporales generados por la compilación (.next, out, dist)
	@echo "Limpiando artefactos de $(APP_NAME)..."
	@rm -rf .next out dist
	@echo "Limpieza completada."

dev: ## Arranca el servidor de desarrollo local de Next.js
	@echo "Iniciando servidor de desarrollo de $(APP_NAME)..."
	@pnpm dev

logs: ## Muestra y sigue los logs de los contenedores Docker
	@docker compose logs -f
