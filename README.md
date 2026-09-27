# Explore With Me

Backend-сервис для публикации событий, подбора мероприятий и управления заявками на участие.

Проект реализует REST API для основной платформы событий и отдельного сервиса статистики просмотров.

## Стек

- Java 21
- Spring Boot 3.3.0
- Spring Cloud 2023.0.2
- Spring Cloud Config, Netflix Eureka, Gateway
- Spring Web
- Spring Data JPA
- Hibernate
- PostgreSQL
- Maven
- Docker / Docker Compose
- Checkstyle
- SpotBugs
- JaCoCo

## Описание проекта

Explore With Me позволяет пользователям создавать события, подавать заявки на участие, модерировать публикации и
получать подборки мероприятий.

Проект разделён на прикладные и инфраструктурные модули:

- `core/main-service` — публичный, приватный и административный API;
- `stats-service` — DTO, клиент и сервер статистики;
- `infra/discovery-server` — реестр сервисов Eureka;
- `infra/config-server` — централизованные конфигурации;
- `infra/gateway-server` — единая точка входа и маршрутизация API.

## Основные возможности

- Создание и редактирование событий
- Публикация и отклонение событий администратором
- Поиск событий по параметрам
- Управление категориями событий
- Создание подборок событий
- Подача и обработка заявок на участие
- Подтверждение или отклонение заявок инициатором события
- Сбор статистики просмотров
- Получение аналитики по посещаемости

## Архитектура

Проект построен как многомодульное Maven-приложение.

Основные слои приложения:

- `controller` — REST API
- `service` — бизнес-логика
- `repository` — доступ к данным
- `model` — JPA-сущности
- `dto` — входные и выходные модели API
- `mapper` — преобразование между DTO и entity
- `exception` — обработка ошибок

## Основные сущности

- `User` — пользователь системы
- `Event` — событие
- `Category` — категория события
- `Compilation` — подборка событий
- `ParticipationRequest` — заявка на участие
- `EndpointHit` — запись статистики обращения к endpoint

## Примеры API

### Публичный API

```http
GET /events
GET /events/{id}
GET /categories
GET /categories/{catId}
GET /compilations
GET /compilations/{compId}
```

### Приватный API

```http
POST /users/{userId}/events
PATCH /users/{userId}/events/{eventId}
GET /users/{userId}/events
POST /users/{userId}/requests
PATCH /users/{userId}/requests/{requestId}/cancel
```

### Административный API

```http
POST /admin/categories
PATCH /admin/categories/{catId}
DELETE /admin/categories/{catId}

POST /admin/users
GET /admin/users
DELETE /admin/users/{userId}

PATCH /admin/events/{eventId}
POST /admin/compilations
PATCH /admin/compilations/{compId}
DELETE /admin/compilations/{compId}
```

### Сервис статистики

```http
POST /hit
GET /stats
```

## Запуск проекта

### Через Maven

```bash
mvn clean package
```

Для локального запуска сначала запустите `discovery-server`, затем `config-server`, после него `stats-server` и
`main-service`, и последним — `gateway-server`. Eureka доступна на `http://localhost:8761`, API — на
`http://localhost:8080`.

### Через Docker Compose

```bash
docker-compose up --build
```

Docker Compose публикует Eureka на порту `8761` и Gateway на порту `8080`. Остальные сервисы регистрируются в
Eureka на случайных внутренних портах.

## Проверка качества кода

В проекте настроены инструменты статического анализа и проверки качества:

```bash
mvn clean test
mvn clean package -P check
mvn clean verify -P coverage
```

Используются:

- Checkstyle
- SpotBugs
- JaCoCo

## Что демонстрирует проект

- разработку многомодульного Spring Boot backend-приложения;
- проектирование REST API;
- работу с PostgreSQL и Hibernate;
- разделение приложения на публичный, приватный и административный API;
- реализацию бизнес-логики модерации событий и заявок;
- взаимодействие основного сервиса со статистическим сервисом;
- Docker-контейнеризацию;
- настройку проверки качества кода через Checkstyle, SpotBugs и JaCoCo.
