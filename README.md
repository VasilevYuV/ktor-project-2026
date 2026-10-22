# Kotlin 2026 — API задач

Учебный backend на Kotlin/Ktor для четырёх контрольных точек по дисциплине
«Кроссплатформенная разработка на языке Kotlin». Один проект последовательно
развивается от базового API до приложения с БД, JWT, ролями и уведомлениями.

**Текущий этап: КТ1 — базовое API.** Данные хранятся в памяти и теряются после
перезапуска. Все реализованные маршруты публичные. База данных, аутентификация,
Swagger, WebSocket и Docker запланированы в следующих этапах.

## Требования и план коммитов

Требования прочитаны в NewLXP 22.10.2026:

- [КТ1 — Базовое API](https://newlxp.ru/education/7238c311-a163-4b7f-b741-b4920db93ee8/disciplines/ddfad9f6-71bf-4e24-8a3c-54010fb6d3a3/topics/131461bc-13ef-4087-8edc-729618f3421c): Ktor, GET/POST/DELETE, JSON, query/path-параметры, корректные статусы, Git и README.
- [КТ2 — CRUD + Аутентификация](https://newlxp.ru/education/7238c311-a163-4b7f-b741-b4920db93ee8/disciplines/ddfad9f6-71bf-4e24-8a3c-54010fb6d3a3/topics/3f88d93a-2385-4163-a639-7d438f7a144d): пять CRUD-маршрутов, отдельные DTO создания и обновления, регистрация, BCrypt/Argon2, JWT со сроком действия и проверяемым claim, защита записи и публичное чтение.
- [КТ3 — API с документацией и тестами](https://newlxp.ru/education/7238c311-a163-4b7f-b741-b4920db93ee8/disciplines/ddfad9f6-71bf-4e24-8a3c-54010fb6d3a3/topics/74762e65-9695-4a8e-9bf3-e74b57f88589): Swagger UI/OpenAPI для всех маршрутов, API-тесты, StatusPages, логирование запросов и инструкция тестирования.
- [КТ4 — Итоговый проект](https://newlxp.ru/education/7238c311-a163-4b7f-b741-b4920db93ee8/disciplines/ddfad9f6-71bf-4e24-8a3c-54010fb6d3a3/topics/4d47fb19-c491-4539-9157-459fc0532537): совместная работа API/БД/аутентификации/документации, роли, WebSocket-уведомления, воспроизводимый Docker-деплой и полный README.

| № | Коммит | Содержание и проверка | Статус |
|---|---|---|---|
| 1 | `feat: implement KT1 task API with layered modules` | Основа проекта, JSON, создание/чтение/удаление, фильтры, ошибки, API-тесты, запуск сервера | Реализован и проверен локально |
| 2 | `feat: persist tasks with PostgreSQL and complete CRUD` | Exposed, схема БД, PostgreSQL, PUT и отдельный UpdateTaskRequest; CRUD и сохранность данных после перезапуска | Запланирован |
| 3 | `feat: add registration and JWT authentication` | BCrypt, уникальность логина, вход, срок JWT, проверка userId, защита POST/PUT/DELETE; тесты 401 и неверных учётных данных | Запланирован |
| 4 | `feat: document and test the API with OpenAPI` | Swagger/OpenAPI, CallLogging, расширение тестов API и централизованных ошибок, примеры Authorization | Запланирован |
| 5 | `feat: enforce user and admin permissions` | Роли USER/ADMIN, безопасное создание администратора, ограничения выбранных маршрутов; тесты 401/403 и разрешённого доступа | Запланирован |
| 6 | `feat: broadcast task changes over WebSocket` | Уведомления после успешных изменений, аутентификация соединения, проверка доставки нескольким клиентам и отключения | Запланирован |
| 7 | `build: add reproducible Docker deployment` | Dockerfile, Compose с PostgreSQL и сохранением данных, пример настроек, проверка запуска и полного сценария, итоговый README | Запланирован |

Каждый этап включает актуализацию README и проверки, затем остановку перед
коммитом. Тесты и StatusPages появляются уже в КТ1, чтобы сразу проверять HTTP-контракт;
в КТ3 покрытие расширяется на CRUD, аутентификацию и документацию.
PostgreSQL/Exposed упомянуты в учебной части КТ2, а хранение в БД явно обязательно
в КТ4, поэтому интеграция запланирована на второй коммит.

Для сдачи требуется публично доступный Git-репозиторий. Локальная реализация
и успешные проверки сами по себе не означают публикацию или сдачу КТ в NewLXP.

## Архитектура

Modular + Layered: пакеты группируются по функциональным модулям, внутри модуля —
по ответственности. Пока используется один Gradle-модуль: для этого объёма
отдельные Gradle-проекты усложнили бы сборку без практической пользы.

```text
src/main/kotlin/com/vasilevyuv/
├── application/          # Сборка приложения и настройка плагинов Ktor
│   ├── Application.kt
│   ├── Serialization.kt
│   └── Errors.kt
├── common/
│   ├── api/              # Общий JSON-ответ ошибки
│   └── domain/           # Ошибка проверки входных данных
└── tasks/
    ├── api/              # HTTP-маршруты, DTO и разбор параметров
    ├── service/          # Правила, нормализация и проверка данных
    ├── domain/           # Task, TaskStatus и контракт репозитория
    └── data/             # Хранилище в памяти

src/test/kotlin/com/vasilevyuv/tasks/api/
└── TaskRoutesTest.kt     # Проверки HTTP-контракта через Ktor Test Engine
```

Поток запроса: `TaskRoutes → TaskService → TaskRepository`.
`InMemoryTaskRepository` реализует контракт репозитория; конкретная реализация
выбирается в `Application.kt`. Сервис и модели не зависят от HTTP или Ktor.
DTO отделены от модели, идентификатор назначается сервером.

Хранилище принадлежит экземпляру приложения. Для конкурентных запросов используются
`ConcurrentHashMap` и `AtomicLong`; удалённые идентификаторы не переиспользуются.
В следующих этапах появятся `auth`, `notifications` и настройка БД.

## Запуск

Стек: JDK 21, Kotlin 2.4.0, Ktor 3.6.0, Tomcat, Gradle Wrapper 9.7.0.
Версии зафиксированы в Gradle-файлах проекта. Gradle отдельно устанавливать не нужно.
Первый запуск требует доступа к Maven Central и Gradle Plugin Portal для загрузки зависимостей.

Wrapper обновлён до 9.7.0 из-за [ошибки кодировки путей с кириллицей в тестовых
процессах Gradle на Windows](https://github.com/gradle/gradle/issues/30304).
SHA-256 дистрибутива зафиксирован в `gradle-wrapper.properties`.

В терминале из корня проекта:

```powershell
# JAVA_HOME должен указывать на установленный JDK 21.
# Пример для текущего компьютера; на другой машине укажите свой путь.
$env:JAVA_HOME = 'C:\Users\Admin\.jdks\ms-21.0.9'

.\gradlew.bat test
.\gradlew.bat run
```

На Linux/macOS: `./gradlew test` и `./gradlew run` при настроенном JDK 21.
Сервер доступен по адресу `http://localhost:8080`. Остановка — `Ctrl+C`.
Порт и модуль приложения задаются в `src/main/resources/application.yaml`.

После `build` можно запустить JAR, содержащий зависимости:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -jar build/libs/kotlin-2026-all.jar
```

## Маршруты КТ1

| Метод и путь | Назначение | Успешный статус |
|---|---|---|
| `GET /` | Название API и текущая КТ | `200` |
| `GET /tasks` | Список задач по возрастанию ID | `200` |
| `GET /tasks/{id}` | Одна задача по ID | `200` |
| `POST /tasks` | Создать задачу | `201`, заголовок `Location` |
| `DELETE /tasks/{id}` | Удалить задачу | `204`, пустое тело |

Параметры `GET /tasks` можно комбинировать:

- `status`: строго `TODO`, `IN_PROGRESS` или `DONE`.
- `q`: подстрока названия без учёта регистра, до 200 символов после обрезки пробелов.
  Пустой `q` не ограничивает список.

Тело `POST /tasks`:

```json
{
  "title": "Изучить Ktor",
  "description": "Маршруты и JSON",
  "status": "IN_PROGRESS"
}
```

- `title` обязателен: 1–200 символов после обрезки пробелов по краям.
- `description` необязателен: по умолчанию `""`, максимум 2000 символов после обрезки пробелов.
- `status` необязателен: по умолчанию `TODO`.
- Неизвестные поля, в том числе переданный клиентом `id`, отклоняются.
- Path-параметр `id` — положительное целое число в диапазоне Kotlin `Long`.

Ответ создания:

```json
{
  "id": 1,
  "title": "Изучить Ktor",
  "description": "Маршруты и JSON",
  "status": "IN_PROGRESS"
}
```

JSON используется во всех успешных ответах с телом и в обработанных ошибках.
У `204 No Content` тело отсутствует по HTTP-контракту.

## Примеры запросов в PowerShell

```powershell
$baseUrl = 'http://localhost:8080'

# Информация об API
Invoke-RestMethod "$baseUrl/"

# Создание: id назначает сервер
$task = Invoke-RestMethod "$baseUrl/tasks" -Method Post `
    -ContentType 'application/json; charset=utf-8' `
    -Body (@{
        title = 'Изучить Ktor'
        description = 'Маршруты и JSON'
        status = 'IN_PROGRESS'
    } | ConvertTo-Json)

# Список, фильтры и получение по ID
Invoke-RestMethod "$baseUrl/tasks"
Invoke-RestMethod "$baseUrl/tasks?status=IN_PROGRESS&q=ktor"
Invoke-RestMethod "$baseUrl/tasks/$($task.id)"

# Удаление: 204 без тела
Invoke-WebRequest "$baseUrl/tasks/$($task.id)" -Method Delete |
    Select-Object StatusCode
```

Повторное получение или удаление этой задачи вернёт `404`.
В следующих этапах здесь появятся PUT, регистрация, вход и примеры с `Authorization: Bearer`.

## Ошибки

StatusPages преобразует ошибки в общий формат:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "id must be a positive integer"
}
```

| Статус | Код | Когда возвращается |
|---|---|---|
| `400` | `VALIDATION_ERROR` | Некорректные ID, статус, длина полей или пустое название |
| `400` | `INVALID_REQUEST` | Повреждённый JSON, неправильные типы, отсутствующие обязательные или неизвестные поля |
| `404` | `TASK_NOT_FOUND` | Нет задачи с таким ID |
| `404` | `ROUTE_NOT_FOUND` | Неизвестный маршрут |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | Тело передано не как `application/json` |
| `500` | `INTERNAL_ERROR` | Неожиданная ошибка; подробности остаются в серверном логе |

## Проверки

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

После первой успешной загрузки зависимостей можно добавить `--offline`.
HTML-отчёт тестов: `build/reports/tests/test/index.html`.

API-тесты загружают `application.yaml`, как обычный сервер, и создают новое
хранилище для каждого теста. Они проверяют создание, чтение, удаление,
фильтры, статусы, JSON, заголовок Location, валидацию и ошибочные запросы.
Ktor Test Engine не открывает реальный порт; запуск Tomcat проверяется отдельно.

Результаты проверки этапа КТ1 (22.10.2026):

- `./gradlew.bat clean test build --offline` — успешная чистая сборка,
  18 тестов, 0 ошибок, 0 пропусков.
- Собранный JAR запущен с Tomcat на `127.0.0.1:18081`: проверены создание `201`,
  чтение и фильтр, неверный ID `400`, удаление `204`, отсутствие задачи `404`
  и передача кириллицы в JSON.
- На итоговом JAR отдельно проверен ответ `415` с JSON при `Content-Type: text/plain`.
- Проверочные процессы остановлены. Публикация репозитория и сдача в NewLXP
  остаются отдельными шагами.

## Полезные ссылки

- [Сериализация JSON в Ktor](https://ktor.io/docs/server-serialization.html)
- [Обработка ошибок через StatusPages](https://ktor.io/docs/server-status-pages.html)
- [Тестирование Ktor API](https://ktor.io/docs/server-testing.html)
