# Kotlin 2026 — API задач

Учебный backend на Kotlin/Ktor для четырёх контрольных точек по дисциплине
«Кроссплатформенная разработка на языке Kotlin». Один проект последовательно
развивается от базового API до приложения с БД, JWT, ролями и уведомлениями.

**Текущий этап: КТ2 — PostgreSQL и полный CRUD.** Задачи сохраняются в PostgreSQL
через Exposed и переживают перезапуск приложения. Реализованы создание, список,
получение по ID, обновление и удаление. Все маршруты пока публичные:
регистрация и JWT входят в следующий коммит КТ2. Swagger, роли, WebSocket и
контейнеризация самого приложения остаются в плане.

## Требования и план коммитов

Требования прочитаны в NewLXP 22.10.2026:

- [КТ1 — Базовое API](https://newlxp.ru/education/7238c311-a163-4b7f-b741-b4920db93ee8/disciplines/ddfad9f6-71bf-4e24-8a3c-54010fb6d3a3/topics/131461bc-13ef-4087-8edc-729618f3421c): Ktor, GET/POST/DELETE, JSON, query/path-параметры, корректные статусы, Git и README.
- [КТ2 — CRUD + Аутентификация](https://newlxp.ru/education/7238c311-a163-4b7f-b741-b4920db93ee8/disciplines/ddfad9f6-71bf-4e24-8a3c-54010fb6d3a3/topics/3f88d93a-2385-4163-a639-7d438f7a144d): пять CRUD-маршрутов, отдельные DTO создания и обновления, регистрация, BCrypt/Argon2, JWT со сроком действия и проверяемым claim, защита записи и публичное чтение.
- [КТ3 — API с документацией и тестами](https://newlxp.ru/education/7238c311-a163-4b7f-b741-b4920db93ee8/disciplines/ddfad9f6-71bf-4e24-8a3c-54010fb6d3a3/topics/74762e65-9695-4a8e-9bf3-e74b57f88589): Swagger UI/OpenAPI для всех маршрутов, API-тесты, StatusPages, логирование запросов и инструкция тестирования.
- [КТ4 — Итоговый проект](https://newlxp.ru/education/7238c311-a163-4b7f-b741-b4920db93ee8/disciplines/ddfad9f6-71bf-4e24-8a3c-54010fb6d3a3/topics/4d47fb19-c491-4539-9157-459fc0532537): совместная работа API/БД/аутентификации/документации, роли, WebSocket-уведомления, воспроизводимый Docker-деплой и полный README.

| № | Коммит | Содержание и проверка | Статус |
|---|---|---|---|
| 1 | `feat: KT1 task API with layered module structure` | Основа проекта, JSON, создание/чтение/удаление, фильтры, ошибки, API-тесты, запуск сервера | Коммит `662423d` |
| 2 | `feat: persist tasks with PostgreSQL and complete CRUD` | Exposed, схема БД, PostgreSQL, PUT и отдельный UpdateTaskRequest; CRUD и сохранность данных после перезапуска | Реализован и проверен локально |
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
├── infrastructure/
│   └── database/         # Настройки PostgreSQL и жизненный цикл пула HikariCP
└── tasks/
    ├── api/              # HTTP-маршруты, DTO и разбор параметров
    ├── service/          # Правила, нормализация и проверка данных
    ├── domain/           # Task, TaskStatus и контракт репозитория
    └── data/             # TasksTable и PostgresTaskRepository на Exposed DSL

src/test/kotlin/com/vasilevyuv/tasks/api/
└── TaskRoutesTest.kt     # Ktor Test Engine + настоящий PostgreSQL в Testcontainers
```

Поток запроса: `TaskRoutes → TaskService → TaskRepository`.
`PostgresTaskRepository` реализует контракт репозитория; конкретная реализация
выбирается в `Application.kt`. Сервис и модели не зависят от HTTP или Ktor.
DTO отделены от модели, идентификатор назначается сервером.

Каждая операция репозитория выполняется в транзакции Exposed на `Dispatchers.IO`,
чтобы блокирующий JDBC не занимал поток обработки запросов. HikariCP переиспользует
соединения и закрывается при остановке приложения или ошибке его настройки.
Идентификаторы выдаёт последовательность PostgreSQL. UPDATE и DELETE проверяют
число изменённых строк: отсутствующая запись возвращает `404`.

`SchemaUtils.create(TasksTable)` при запуске создаёт отсутствующую таблицу.
Существующие данные сохраняются; изменение уже созданной схемы этой командой
не выполняется. Для дальнейших изменений схемы потребуется отдельная миграция.

| Поле таблицы `tasks` | Тип | Назначение |
|---|---|---|
| `id` | `BIGSERIAL`, первичный ключ | ID, назначаемый PostgreSQL |
| `title` | `VARCHAR(200) NOT NULL` | Название |
| `description` | `VARCHAR(2000) NOT NULL` | Описание, по умолчанию пустое |
| `status` | `VARCHAR(11) NOT NULL` | TODO / IN_PROGRESS / DONE, по умолчанию TODO |

Фильтрация и сортировка выполняются в SQL. Поиск `q` трактует `%`, `_` и `\`
как обычные символы, а значения передаются в запрос через параметры.
В следующих этапах появятся функциональные модули `auth` и `notifications`.

## Запуск

Стек: JDK 21, Kotlin 2.4.0, Ktor 3.6.0, Tomcat, Gradle Wrapper 9.7.0.
Хранение: PostgreSQL 18.6, Exposed 1.6.0, HikariCP 7.0.2, pgJDBC 42.7.14.
Версии зафиксированы в Gradle-файлах проекта. Gradle отдельно устанавливать не нужно.
Для локальной БД и интеграционных тестов нужен запущенный Docker с Linux-контейнерами.
Первый запуск требует сети для загрузки зависимостей и Docker-образов.

Wrapper обновлён до 9.7.0 из-за [ошибки кодировки путей с кириллицей в тестовых
процессах Gradle на Windows](https://github.com/gradle/gradle/issues/30304).
SHA-256 дистрибутива зафиксирован в `gradle-wrapper.properties`.

В PowerShell из корня проекта:

```powershell
# JAVA_HOME должен указывать на установленный JDK 21.
# Пример для текущего компьютера; на другой машине укажите свой путь.
$env:JAVA_HOME = 'C:\Users\Admin\.jdks\ms-21.0.9'

# Пароль локальной учебной БД. Используйте одно значение при последующих запусках.
$env:DATABASE_PASSWORD = 'kotlin-local-dev'
docker compose up -d --wait postgres

.\gradlew.bat test
.\gradlew.bat run
```

На Linux/macOS: `./gradlew test` и `./gradlew run` при настроенном JDK 21.
Перед запуском БД и приложения задайте `export DATABASE_PASSWORD='kotlin-local-dev'`
и выполните `docker compose up -d --wait postgres`.
Сервер доступен по адресу `http://localhost:8080`. Остановка — `Ctrl+C`.
Порт и модуль приложения задаются в `src/main/resources/application.yaml`.

Compose сейчас запускает только БД: проект `kotlin-2026`, порт `127.0.0.1:55432`,
том `kotlin-2026_postgres-data`. Приложение запускается через Gradle или JAR.
Полный Compose-деплой приложения вместе с БД относится к этапу 7.

Настройки приложения читаются из переменных окружения через `application.yaml`:

| Переменная | Значение по умолчанию |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:55432/kotlin_tasks` |
| `DATABASE_USER` | `kotlin` |
| `DATABASE_PASSWORD` | Обязательна |
| `DATABASE_POOL_SIZE` | `5`, допустимо от 1 до 20 |

Если БД недоступна или пароль неверен, приложение завершает запуск с ошибкой;
скрытого переключения на хранение в памяти нет. Настройки URL и пользователя
позволяют подключить другую PostgreSQL вместо локального Compose.

Чтобы остановить БД с сохранением данных: `docker compose stop postgres`.
Повторный `docker compose up -d --wait postgres` использует тот же том.
Пароль из окружения инициализирует пользователя только при создании пустой БД;
смена переменной не меняет пароль в существующем томе.

После `build` можно запустить JAR, содержащий зависимости:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -jar build/libs/kotlin-2026-all.jar
```

## Маршруты

| Метод и путь | Назначение | Успешный статус |
|---|---|---|
| `GET /` | Название API и текущая КТ | `200` |
| `GET /tasks` | Список задач по возрастанию ID | `200` |
| `GET /tasks/{id}` | Одна задача по ID | `200` |
| `POST /tasks` | Создать задачу | `201`, заголовок `Location` |
| `PUT /tasks/{id}` | Полностью обновить изменяемые поля задачи | `200` |
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

`PUT /tasks/{id}` использует отдельный `UpdateTaskRequest`: все три поля
`title`, `description` и `status` обязательны. Правила проверки и обрезки пробелов
совпадают с созданием. Пустая строка очищает описание. ID остаётся прежним;
обновление отсутствующей задачи возвращает `404`, новая запись не создаётся.
Повтор одного PUT-запроса приводит к тому же состоянию задачи.

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

# Обновление: все изменяемые поля обязательны
Invoke-RestMethod "$baseUrl/tasks/$($task.id)" -Method Put `
    -ContentType 'application/json; charset=utf-8' `
    -Body (@{
        title = 'Изучить PostgreSQL и Exposed'
        description = 'Полный CRUD готов'
        status = 'DONE'
    } | ConvertTo-Json)

# Удаление: 204 без тела
Invoke-WebRequest "$baseUrl/tasks/$($task.id)" -Method Delete |
    Select-Object StatusCode
```

Повторное получение или удаление этой задачи вернёт `404`.
В следующем этапе здесь появятся регистрация, вход и примеры с `Authorization: Bearer`.

Для ручной проверки сохранности создайте задачу и запомните её ID, остановите
приложение, выполните `docker compose restart postgres`, затем снова запустите
приложение. `GET /tasks/{id}` должен вернуть ту же задачу. После PUT и перезапуска
должны сохраниться новые поля, после DELETE и перезапуска — возвращаться `404`.

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

Тесты используют Testcontainers 2.0.5: один одноразовый PostgreSQL 18.6 на случайном
порту для тестового класса. Локальная БД Compose не требуется и не очищается.
Нужен работающий Docker; отсутствие Docker приводит к ошибке, а не к пропуску тестов.
Testcontainers автоматически останавливает и удаляет свой контейнер после тестов.

Ktor Test Engine запускает настоящий модуль приложения с настройками тестовой БД.
Перед обычным тестом очищается только таблица одноразового контейнера.
В проверке сохранности данных несколько экземпляров приложения последовательно
подключаются к той же БД без очистки. HTTP-порт тестовый движок не открывает;
загрузка `application.yaml` и запуск Tomcat проверяются отдельно на собранном JAR.

Покрытие: CRUD, фильтры, JSON и HTTP-статусы, границы полей, ошибочные PUT без
изменения данных, отсутствие upsert, идемпотентность PUT, буквальный поиск спецсимволов,
кириллица, конкурентное создание и сохранность изменений между экземплярами приложения.

Результаты текущего этапа КТ2 CRUD (22.10.2026):

- `./gradlew.bat test build` — сборка успешна; 28 тестов на настоящем PostgreSQL,
  0 ошибок, 0 пропусков.
- Compose поднимает PostgreSQL 18.6 до состояния healthy. Собранный JAR успешно
  читает настройки БД из `application.yaml` и запускает Tomcat на `127.0.0.1:18081`.
- Через настоящий HTTP проверены POST `201`, PUT `200`, чтение и фильтр,
  ошибочный PUT `400`, DELETE `204` и PUT отсутствующей задачи `404`.
- После перезапуска приложения и PostgreSQL подтверждены обновлённые поля,
  включая кириллицу. После удаления и ещё одного запуска приложения — `404`.
- Проверочные процессы приложения и контейнер Compose остановлены; том БД
  сохранён. Одноразовые контейнеры Testcontainers удалены автоматически.

История проверки КТ1 (22.10.2026, коммит `662423d`):

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
- [Транзакции Exposed](https://www.jetbrains.com/help/exposed/transactions.html)
- [PostgreSQL в Testcontainers](https://java.testcontainers.org/modules/databases/postgres/)
- [Официальный образ PostgreSQL](https://hub.docker.com/_/postgres)
