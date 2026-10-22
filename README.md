# Kotlin 2026 — API задач

Учебный backend на Kotlin/Ktor для четырёх контрольных точек по дисциплине
«Кроссплатформенная разработка на языке Kotlin». Один проект последовательно
развивается от базового API до приложения с БД, JWT, ролями и уведомлениями.

**Текущий этап: КТ4 — итоговый проект с запуском через Docker Compose.** API задач работает
с PostgreSQL, Exposed, BCrypt и JWT. USER и ADMIN создают и обновляют задачи,
удаление доступно только ADMIN. Администратор создаётся при запуске из настроек
окружения; публичная регистрация всегда создаёт USER. Чтение и документация публичные.
После изменения задачи подключённые клиенты получают уведомление через `/ws/tasks`.
Маршруты и WebSocket-handshake описаны в OpenAPI; HTTP API доступно в Swagger UI.
Запросы журналируются через CallLogging, ошибки обрабатываются через StatusPages.
Приложение собирается из исходников внутри Docker и запускается вместе с PostgreSQL;
данные сохраняются в именованном томе между перезапусками и пересозданием контейнеров.

## Требования и план коммитов

Требования прочитаны в NewLXP 22.10.2026:

- [КТ1 — Базовое API](https://newlxp.ru/education/7238c311-a163-4b7f-b741-b4920db93ee8/disciplines/ddfad9f6-71bf-4e24-8a3c-54010fb6d3a3/topics/131461bc-13ef-4087-8edc-729618f3421c): Ktor, GET/POST/DELETE, JSON, query/path-параметры, корректные статусы, Git и README.
- [КТ2 — CRUD + Аутентификация](https://newlxp.ru/education/7238c311-a163-4b7f-b741-b4920db93ee8/disciplines/ddfad9f6-71bf-4e24-8a3c-54010fb6d3a3/topics/3f88d93a-2385-4163-a639-7d438f7a144d): пять CRUD-маршрутов, отдельные DTO создания и обновления, регистрация, BCrypt/Argon2, JWT со сроком действия и проверяемым claim, защита записи и публичное чтение.
- [КТ3 — API с документацией и тестами](https://newlxp.ru/education/7238c311-a163-4b7f-b741-b4920db93ee8/disciplines/ddfad9f6-71bf-4e24-8a3c-54010fb6d3a3/topics/74762e65-9695-4a8e-9bf3-e74b57f88589): Swagger UI/OpenAPI для всех маршрутов, API-тесты, StatusPages, логирование запросов и инструкция тестирования.
- [КТ4 — Итоговый проект](https://newlxp.ru/education/7238c311-a163-4b7f-b741-b4920db93ee8/disciplines/ddfad9f6-71bf-4e24-8a3c-54010fb6d3a3/topics/4d47fb19-c491-4539-9157-459fc0532537): совместная работа API/БД/аутентификации/документации, роли, WebSocket-уведомления, воспроизводимый Docker-деплой и полный README.

| № | Коммит | Содержание и проверка | Статус |
|---|---|---|---|
| 1 | `feat: KT1 task API with layered module structure` | Основа проекта, JSON, создание/чтение/удаление, фильтры, ошибки, API-тесты, запуск сервера | Коммит `662423d` |
| 2 | `feat: PostgreSQL and complete CRUD` | Exposed, схема БД, PostgreSQL, PUT и отдельный UpdateTaskRequest; CRUD и сохранность данных после перезапуска | Коммит `d6120ab` |
| 3 | `feat: KT2 add registration and JWT authentication` | BCrypt, уникальность логина, вход, срок JWT, проверка userId, защита POST/PUT/DELETE; тесты 401 и неверных учётных данных | Коммит `da66646` |
| 4 | `feat: KT3 document and test the API with OpenAPI` | Swagger/OpenAPI, CallLogging, расширение тестов API и централизованных ошибок, примеры Authorization | Коммит `41c1bdf` |
| 5 | `feat: enforce user and admin permissions` | Роли USER/ADMIN, безопасное создание администратора, DELETE только для ADMIN; миграция, тесты 401/403 и разрешённого доступа | Коммит `effc53e` |
| 6 | `feat: broadcast task changes over WebSocket` | Уведомления после успешных изменений, аутентификация соединения, проверка доставки нескольким клиентам и отключения | Коммит `c083d4c` |
| 7 | `build: add reproducible Docker deployment` | Dockerfile, Compose с PostgreSQL и сохранением данных, пример настроек, проверка запуска и полного сценария, итоговый README | Реализован и проверен локально |

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
│   ├── Authentication.kt
│   ├── DatabaseSchema.kt
│   ├── Documentation.kt
│   ├── RequestLogging.kt
│   ├── Serialization.kt
│   ├── WebSockets.kt
│   └── Errors.kt
├── auth/
│   ├── api/              # Регистрация, вход, /auth/me, проверка ADMIN и публичные DTO
│   ├── service/          # Регистрация/вход и создание администратора при запуске
│   ├── domain/           # User, UserRole, ошибки и контракты репозитория/хэширования
│   ├── data/             # UsersTable и PostgresUserRepository
│   └── security/         # BCrypt, JWT, AuthenticatedUser и настройки администратора
├── common/
│   ├── api/              # Общий JSON-ответ ошибки
│   └── domain/           # Ошибка проверки входных данных
├── infrastructure/
│   └── database/         # Настройки PostgreSQL и жизненный цикл пула HikariCP
├── notifications/
│   ├── api/              # WebSocket-маршрут и JSON сообщений
│   └── service/          # Подписки и ограниченная очередь для каждого соединения
└── tasks/
    ├── api/              # HTTP-маршруты, DTO и разбор параметров
    ├── service/          # Правила, нормализация и проверка данных
    ├── domain/           # Task, TaskStatus, контракт репозитория и TaskChangePublisher
    └── data/             # TasksTable и PostgresTaskRepository на Exposed DSL

src/main/resources/
├── db/migration/         # Идемпотентная SQL-миграция ролей существующих пользователей
├── openapi/task-api.yaml # Контракт API: схемы, параметры, примеры, статусы и JWT
├── documentation/       # HTML и настройка Swagger UI; JS/CSS библиотеки берутся из WebJar
├── application.yaml     # Настройки сервера, PostgreSQL, JWT и создания администратора
└── logback.xml          # Вывод журналов в консоль

src/test/kotlin/com/vasilevyuv/
├── application/          # OpenAPI, Swagger, примеры запросов, логирование и обработка ошибок
├── tasks/api/            # Полный CRUD через администратора
├── tasks/service/        # Отсутствие уведомлений при сбое хранилища
├── notifications/        # WebSocket: JWT, доставка, отключение и очереди
├── auth/api/             # Регистрация, JWT, роли, создание администратора и миграция
├── auth/security/        # Ограничения настроек JWT и администратора
└── support/              # Общая тестовая БД и HTTP-помощники
```

Файлы запуска в корне: `Dockerfile` собирает образ, `.dockerignore` ограничивает
контекст сборки, `compose.yaml` описывает приложение и БД, `.env.example` содержит
список настроек без секретов.

Поток запроса: `TaskRoutes → TaskService → TaskRepository`.
`PostgresTaskRepository` реализует контракт репозитория; конкретная реализация
выбирается в `Application.kt`. Сервис и модели не зависят от HTTP или Ktor.
DTO отделены от модели, идентификатор назначается сервером.

Регистрация и вход проходят через `AuthRoutes → AuthService → UserRepository`.
Сервис получает BCrypt через контракт `PasswordHasher`; хэширование выполняется
на `Dispatchers.Default`. Ktor Authentication проверяет JWT и загружает пользователя
из БД в `AuthenticatedUser`. Перед удалением `requireAdmin()` проверяет его роль.
`JwtTokenService` выпускает токен только после успешной проверки пароля.
HTTP-ответы используют отдельные DTO без хэша пароля.

Каждая операция репозитория выполняется в транзакции Exposed на `Dispatchers.IO`,
чтобы блокирующий JDBC не занимал поток обработки запросов. HikariCP переиспользует
соединения и закрывается при остановке приложения или ошибке его настройки.
Идентификаторы выдаёт последовательность PostgreSQL. UPDATE и DELETE проверяют
число изменённых строк: отсутствующая запись возвращает `404`.

`initializeDatabaseSchema()` при запуске создаёт отсутствующие таблицы через
`SchemaUtils.create`, затем выполняет `db/migration/V1__add_user_roles.sql`.
Миграция добавляет `role` с `NOT NULL DEFAULT 'USER'` в БД предыдущего этапа:
существующие пользователи получают USER, их ID, хэши и задачи сохраняются.
`ADD COLUMN IF NOT EXISTS` позволяет повторять запуск, не меняя уже назначенные роли.
Схема и миграция выполняются в одной транзакции с PostgreSQL advisory lock,
чтобы несколько экземпляров приложения не изменяли схему одновременно.
Это отдельный идемпотентный SQL-скрипт без системы версионирования миграций.

| Поле таблицы `tasks` | Тип | Назначение |
|---|---|---|
| `id` | `BIGSERIAL`, первичный ключ | ID, назначаемый PostgreSQL |
| `title` | `VARCHAR(200) NOT NULL` | Название |
| `description` | `VARCHAR(2000) NOT NULL` | Описание, по умолчанию пустое |
| `status` | `VARCHAR(11) NOT NULL` | TODO / IN_PROGRESS / DONE, по умолчанию TODO |

| Поле таблицы `users` | Тип | Назначение |
|---|---|---|
| `id` | `BIGSERIAL`, первичный ключ | ID пользователя для claim `userId` |
| `login` | `VARCHAR(32) NOT NULL`, уникальный индекс | Логин в нижнем регистре |
| `password_hash` | `VARCHAR(60) NOT NULL` | BCrypt-хэш с солью и cost 12 |
| `role` | `VARCHAR(5) NOT NULL` | USER / ADMIN, по умолчанию USER |

Уникальный индекс и `INSERT ... ON CONFLICT DO NOTHING` не позволяют создать
два одинаковых логина даже при одновременной регистрации. Существующий пароль
при конфликте не изменяется. Задачи общие: USER и ADMIN могут создавать и обновлять
любую задачу, ADMIN также может удалить её. Привязка задач к владельцу не вводится.

Фильтрация и сортировка выполняются в SQL. Поиск `q` трактует `%`, `_` и `\`
как обычные символы, а значения передаются в запрос через параметры.

После завершения транзакции `TaskService` публикует `TaskChange` через контракт
`TaskChangePublisher`. `TaskNotificationHub` реализует этот контракт и раздаёт
событие в отдельную очередь каждого подключённого клиента. `NotificationRoutes`
преобразует событие в JSON и отправляет его через WebSocket. В `tasks` нет
зависимости от Ktor WebSockets или реализации модуля `notifications`.
Подписки существуют в памяти текущего приложения; при отключении освобождаются
в `finally`, при остановке приложения закрываются все очереди.

## Запуск

Стек: JDK 21, Kotlin 2.4.0, Ktor 3.6.0, Tomcat, Gradle Wrapper 9.7.0.
Хранение: PostgreSQL 18.6, Exposed 1.6.0, HikariCP 7.0.2, pgJDBC 42.7.14.
Аутентификация: Ktor Authentication/JWT, BCrypt (`at.favre.lib:bcrypt` 0.10.2).
Документация: OpenAPI 3.0.3, Swagger UI 5.34.0; проверка спецификации — Swagger Parser 2.1.48.
Версии зафиксированы в Gradle-файлах проекта. Gradle отдельно устанавливать не нужно.
Для Compose достаточно Docker с Linux-контейнерами и Docker Compose v2 с поддержкой
`up --wait`. Для локальных тестов и запуска через Gradle дополнительно нужен JDK 21.
Первый запуск требует сети для загрузки зависимостей и Docker-образов.

Wrapper обновлён до 9.7.0 из-за [ошибки кодировки путей с кириллицей в тестовых
процессах Gradle на Windows](https://github.com/gradle/gradle/issues/30304).
SHA-256 дистрибутива зафиксирован в `gradle-wrapper.properties`.

### Полный запуск через Docker Compose

Все команды выполняются из корня репозитория. JDK и Gradle на компьютере
для этого способа не требуются: Dockerfile собирает JAR самостоятельно.

1. Создайте `.env` по шаблону, если файл ещё не существует:

   ```powershell
   if (-not (Test-Path .env)) { Copy-Item .env.example .env }
   ```

2. Заполните `DATABASE_PASSWORD` и `JWT_SECRET` в `.env`.
   Для нового тома выберите свой пароль БД. При использовании тома предыдущих
   этапов нужен прежний пароль; в приведённом ниже локальном примере это
   `kotlin-local-dev`. Смена значения в `.env` не меняет пароль уже созданной БД.
   Случайный секрет JWT можно получить в PowerShell 7 и записать в `.env`:

   ```powershell
   [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
   ```

   Для проверки удаления задач задайте также **оба** поля `ADMIN_LOGIN` и
   `ADMIN_PASSWORD`: новый логин и пароль от 12 символов, максимум 72 байта UTF-8.
   Если администратор не нужен, оставьте оба пустыми. Подробнее — в разделе
   «Создание администратора». Значения, содержащие `$` или `#`, заключайте в
   одинарные кавычки по правилам `.env` Compose. Файл `.env` исключён из Git
   и контекста Docker-сборки; пароли не встраиваются в образ.

3. Проверьте конфигурацию без вывода секретов и запустите оба сервиса:

   ```powershell
   docker compose config --quiet
   docker compose up -d --build --wait
   docker compose ps
   Invoke-RestMethod http://localhost:8080/
   ```

Ожидаемый ответ: `{"name":"Task API","checkpoint":"KT4"}`; оба контейнера должны
стать `healthy`. Swagger UI — [http://localhost:8080/docs](http://localhost:8080/docs),
WebSocket — `ws://localhost:8080/ws/tasks`. На Linux/macOS команды Docker те же;
шаблон копируется через `cp .env.example .env`, секрет можно получить
командой `openssl rand -base64 32`.

Compose читает `.env` автоматически. Переменные текущей оболочки имеют приоритет
над этим файлом: при изменении настроек учитывайте значения, ранее заданные
через `$env:...`, или откройте новую сессию. Без обязательных секретов Compose
завершится до запуска контейнеров. После изменения `.env` выполните
`docker compose up -d --wait`: простой `restart` не применяет новое окружение.

| Сервис | Назначение | Адрес с компьютера | Хранение |
|---|---|---|---|
| `app` | Ktor/Tomcat, образ `kotlin-2026-app:local` | `127.0.0.1:8080`, либо `APP_PORT` | JAR в образе; временные файлы в `/tmp` |
| `postgres` | PostgreSQL 18.6 | `127.0.0.1:55432`, либо `POSTGRES_PORT` | Том `kotlin-2026_postgres-data` |

Внутри сети Compose приложение подключается к `postgres:5432/kotlin_tasks`,
пользователь БД — `kotlin`. Порты компьютера на этот адрес не влияют.
Существующие имя проекта `kotlin-2026`, имя тома и точка монтирования
`/var/lib/postgresql` сохранены. Для PostgreSQL 18 данные размещаются внутри
этого каталога. Изменение имени проекта через `-p` создаст другой том.

Dockerfile состоит из двух этапов: Temurin JDK 21 собирает `shadowJar`, затем
Temurin JRE 21 запускает только готовый JAR от UID/GID `10001:10001`.
JDK/JRE и PostgreSQL закреплены по SHA-256 digest; Gradle Wrapper — по версии
и SHA-256 дистрибутива. Обновление базовых образов выполняется явно с обновлением
digest и повторной проверкой. Кэш Gradle хранится в BuildKit, а локальные
`build/`, `.gradle/`, `.git/` и `.env` в сборку не передаются.

Интеграционные тесты запускаются отдельной командой Gradle с Testcontainers;
Dockerfile выполняет сборку JAR и не требует проброса Docker socket.
Файловая система `app` доступна только для чтения, `/tmp` — временный tmpfs.
Рабочий каталог Tomcat направлен в `/tmp/tomcat` через `-Dcatalina.base`.
Сервис работает без дополнительных Linux capabilities. PostgreSQL сохраняет
свои данные в томе. Оба сервиса имеют политику `unless-stopped`.

Приложение запускается после успешного `pg_isready` PostgreSQL. Проверка `app`
выполняет HTTP GET `/`: она подтверждает работу HTTP-сервера; операции с данными
проверяются отдельно через API. На остановку выделено 30 секунд, SIGTERM
передаётся Java-процессу через Docker init.

Полезные команды:

```powershell
docker compose logs --tail=100 app postgres
docker compose stop              # Остановить контейнеры, сохранив их и том
docker compose up -d --wait      # Запустить снова
docker compose down             # Удалить контейнеры и сеть; именованный том остаётся
docker compose up -d --wait      # Пересоздать контейнеры с прежними данными
```

`docker compose down -v` удаляет также данные БД — для обычной остановки его
использовать нельзя. Для проверки сохранности создайте задачу и пользователя,
выполните `down` без `-v`, затем `up -d --wait`: задача, вход и роль должны
сохраниться. При неизменном JWT_SECRET продолжает работать и ранее выданный JWT.
WebSocket после перезапуска подключается заново и перечитывает список задач.

Если порт занят, измените `APP_PORT` или `POSTGRES_PORT` в `.env` и повторите `up`.
Если сервис не становится healthy, проверьте `docker compose logs --tail=100`:
типичные причины — пароль старого тома, короткий JWT_SECRET или конфликт
настроек администратора с существующим аккаунтом. Удаление тома не требуется
для исправления конфигурации.

### Локальный запуск через Gradle

Этот способ удобен для разработки: PostgreSQL работает в Docker, Java — на
компьютере. Остановите контейнер приложения через `docker compose stop app`,
если он уже занимает порт 8080. В PowerShell из корня проекта:

```powershell
# JAVA_HOME должен указывать на установленный JDK 21.
# Пример для текущего компьютера; на другой машине укажите свой путь.
$env:JAVA_HOME = 'C:\Users\Admin\.jdks\ms-21.0.9'

# Пароль локальной учебной БД. Используйте одно значение при последующих запусках.
$env:DATABASE_PASSWORD = 'kotlin-local-dev'

# Случайный секрет подписи JWT. Сохраняйте то же значение между запусками,
# если ранее выданные токены должны продолжать работать.
$jwtKey = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtKey)
$env:JWT_SECRET = [Convert]::ToBase64String($jwtKey)
$rng.Dispose()

docker compose up -d --wait postgres

.\gradlew.bat test
.\gradlew.bat run
```

На Linux/macOS: `./gradlew test` и `./gradlew run` при настроенном JDK 21.
Перед запуском БД и приложения задайте `export DATABASE_PASSWORD='kotlin-local-dev'`,
`export JWT_SECRET="$(openssl rand -base64 32)"` и выполните
`docker compose up -d --wait postgres`.
Сервер доступен по адресу `http://localhost:8080`. Остановка — `Ctrl+C`.
Swagger UI: [http://localhost:8080/docs](http://localhost:8080/docs).
Порт и модуль приложения задаются в `src/main/resources/application.yaml`.

В этом режиме `docker compose up -d --wait postgres` запускает только БД;
используются те же проект `kotlin-2026` и том `kotlin-2026_postgres-data`.
Команда требует настроенных DATABASE_PASSWORD и JWT_SECRET, поскольку Compose
проверяет переменные всего файла, даже при выборе одного сервиса.

Настройки приложения читаются из переменных окружения через `application.yaml`:

| Переменная | Значение по умолчанию |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:55432/kotlin_tasks` |
| `DATABASE_USER` | `kotlin` |
| `DATABASE_PASSWORD` | Обязательна |
| `DATABASE_POOL_SIZE` | `5`, допустимо от 1 до 20 |
| `JWT_SECRET` | Обязательна, минимум 32 байта UTF-8; используйте случайное значение |
| `JWT_ISSUER` | `kotlin-task-api` |
| `JWT_AUDIENCE` | `kotlin-task-client` |
| `JWT_EXPIRES_IN_SECONDS` | `3600` (1 час), допустимо от 1 до 86400 |
| `ADMIN_LOGIN` | Не задан; вместе с ADMIN_PASSWORD включает создание администратора |
| `ADMIN_PASSWORD` | Не задан; минимум 12 символов и максимум 72 байта UTF-8 |

Без секрета JWT, с коротким секретом или недопустимым сроком токена приложение
не запускается. Секрет задаётся окружением, в репозитории рабочего значения нет.
Смена секрета делает ранее выданные токены недействительными. Приложение само
не загружает `.env`: при запуске через Gradle/JAR переменные задаются в оболочке,
а при контейнерном запуске их передаёт Compose. APP_PORT и POSTGRES_PORT относятся
только к публикации портов Compose; внутри контейнеров используются 8080 и 5432.

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

### Создание администратора

Без `ADMIN_LOGIN` и `ADMIN_PASSWORD` приложение запускается без создания ADMIN.
Для первого запуска задайте **обе** переменные в той же PowerShell-сессии,
где уже настроены БД и JWT. Выберите новый логин: 3–32 латинские буквы, цифры или `_`.
Пароль администратора должен содержать не менее 12 символов и не более 72 байт UTF-8.

```powershell
$env:ADMIN_LOGIN = 'task_admin'
$adminSecurePassword = Read-Host 'Новый пароль администратора (от 12 символов)' -AsSecureString
$env:ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $adminSecurePassword).Password
$adminSecurePassword.Dispose()
.\gradlew.bat run
```

Секрет вводится скрыто и передаётся приложению через окружение процесса.
В коде и конфигурации нет стандартного пароля; в БД хранится BCrypt-хэш с солью.
Создание выполняется до начала обработки HTTP-запросов. Повторный запуск с теми же
логином и паролем сохраняет существующий ADMIN и его хэш. Уникальность логина
защищает также от одновременной инициализации несколькими экземплярами.

Если логин уже принадлежит USER или пароль существующего ADMIN не совпадает,
запуск завершается ошибкой: автоматического повышения прав и сброса пароля нет.
Неполная пара настроек и неверный формат также останавливают запуск.
Используйте свободный логин, а не аккаунт, созданный через `/auth/register`.
Изменение `ADMIN_PASSWORD` не является способом смены существующего пароля.

После первого успешного запуска можно остановить приложение, убрать обе
переменные и запустить его снова. Учётная запись ADMIN и её пароль останутся в БД:

```powershell
Remove-Item Env:ADMIN_LOGIN, Env:ADMIN_PASSWORD -ErrorAction SilentlyContinue
.\gradlew.bat run
```

Для входа администратор использует обычный `POST /auth/login`. Публичных маршрутов
назначения роли или создания ADMIN нет. Поле `role` при регистрации отклоняется.
При запуске через Docker задайте эти поля в `.env` и выполните
`docker compose up -d --wait`. После успешного создания можно очистить оба поля
в `.env` и повторить `up`: Compose пересоздаст `app` с новым окружением, аккаунт
ADMIN сохранится в томе PostgreSQL.

## Маршруты

| Метод и путь | Назначение | Успешный статус | Доступ |
|---|---|---|---|
| `GET /` | Название API и текущая КТ | `200` | Публичный |
| `GET /docs` | Swagger UI | `200`, HTML | Публичный |
| `GET /openapi.yaml` | Спецификация OpenAPI | `200`, YAML | Публичный |
| `POST /auth/register` | Зарегистрировать пользователя | `201` | Публичный |
| `POST /auth/login` | Проверить пароль и получить JWT | `200` | Публичный |
| `GET /auth/me` | Текущий пользователь: ID, логин и роль | `200` | Bearer JWT, USER / ADMIN |
| `GET /ws/tasks` (WebSocket Upgrade) | Подписка на изменения задач | `101`, затем JSON-кадры | Bearer JWT, USER / ADMIN |
| `GET /tasks` | Список задач по возрастанию ID | `200` | Публичный |
| `GET /tasks/{id}` | Одна задача по ID | `200` | Публичный |
| `POST /tasks` | Создать задачу | `201`, заголовок `Location` | Bearer JWT, USER / ADMIN |
| `PUT /tasks/{id}` | Полностью обновить изменяемые поля задачи | `200` | Bearer JWT, USER / ADMIN |
| `DELETE /tasks/{id}` | Удалить задачу | `204`, пустое тело | Bearer JWT, только ADMIN |

### Регистрация и вход

Оба маршрута принимают JSON с обязательными строками `login` и `password`:

```json
{
  "login": "student",
  "password": "Study-password-2026!"
}
```

- Логин: 3–32 латинские буквы, цифры или `_`; пробелы по краям убираются,
  регистр не учитывается (`Student` и `student` — один логин).
- Пароль: минимум 8 символов, максимум 72 байта UTF-8; строка только из пробелов
  недопустима. Регистр и пробелы значимы. Ограничение в байтах связано с BCrypt:
  например, 36 русских букв занимают 72 байта. Длинные пароли отклоняются,
  а не обрезаются.
- Неизвестные поля, включая `id` и `role`, отклоняются.
- Регистрация всегда назначает USER и возвращает `{"id":1,"login":"student","role":"USER"}`. Занятый логин —
  `409 LOGIN_ALREADY_TAKEN`.
- Неверный пароль и неизвестный логин дают одинаковый `401 INVALID_CREDENTIALS`.
  Даже для неизвестного пользователя выполняется BCrypt-проверка с фиктивным хэшем.
  Ошибки формата учётных данных дают `400`.

Успешный вход возвращает `Cache-Control: no-store` и тело:

```json
{
  "accessToken": "<JWT>",
  "expiresIn": 3600,
  "tokenType": "Bearer"
}
```

Токен подписан HS256. Проверяются подпись, `iss`, `aud`, обязательный срок `exp`
и `userId` — строка с положительным ID существующего пользователя. Выпущенный
токен также содержит `iat`; пароль и его хэш в JWT не попадают. Удаление пользователя
из БД прекращает доступ по его токену. Для записи нужен заголовок
`Authorization: Bearer <JWT>`; отсутствующий, неверный или просроченный токен
даёт `401 UNAUTHORIZED` с `WWW-Authenticate: Bearer` до обработки тела запроса.
После истечения срока нужно снова войти; refresh-токены и маршрут выхода пока
не реализованы. При размещении вне локального компьютера используйте HTTPS.

`GET /auth/me` с Bearer JWT возвращает ID, логин и актуальную роль с
`Cache-Control: no-store`. Роль читается из PostgreSQL на каждом защищённом запросе,
а не из claims JWT, query или заголовков клиента. Поэтому изменение роли в БД
действует со следующим запросом и для ранее выданного токена.
При удалении задачи без действительного JWT возвращается `401`; с JWT пользователя
USER — `403 FORBIDDEN`. Проверка роли предшествует проверке ID и поиску задачи.

### Задачи

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

## OpenAPI и Swagger UI

После запуска откройте [Swagger UI](http://localhost:8080/docs).
[Спецификация YAML](http://localhost:8080/openapi.yaml) доступна отдельно;
её исходник — `src/main/resources/openapi/task-api.yaml`.

Спецификация описывает девять HTTP-операций API, WebSocket-handshake и два маршрута документации:
параметры пути и фильтров, JSON-схемы, обязательные поля, примеры, успешные ответы,
ошибки и заголовки `Location`, `Cache-Control`, `WWW-Authenticate`.
Схема `bearerAuth` обязательна у `/auth/me`, `/ws/tasks` и POST/PUT/DELETE задач.
DELETE дополнительно требует ADMIN; ответ `403` описан отдельно от `401`.
Статические файлы интерфейса раздаются под `/docs/` и `/docs/assets/`.

Проверка через интерфейс:

1. В `POST /auth/register` нажмите **Try it out**, укажите новый логин и пароль,
   затем **Execute**. Ожидается `201` с ролью USER; повтор того же логина даст `409`.
2. Выполните `POST /auth/login` с этими данными. Скопируйте `accessToken` из ответа `200`.
3. Нажмите **Authorize**, вставьте только токен, без слова `Bearer`, подтвердите.
   Swagger сам добавит заголовок `Authorization: Bearer <JWT>`.
4. Проверьте `/auth/me`: роль USER. Выполните создание задачи, GET по ID, PUT со всеми
   тремя полями: ожидаются `201`, `200`, `200`. DELETE вернёт `403`, задача сохранится.
5. Войдите через `/auth/login` администратором, созданным при запуске. В Authorize
   выполните **Logout**, затем вставьте его токен. `/auth/me` вернёт ADMIN,
   DELETE — `204`; повторный GET вернёт `404`.
6. Удалите авторизацию через **Logout** в окне Authorize: запись должна вернуть `401`,
   чтение продолжит работать. Это удаляет токен из интерфейса, не отзывает его на сервере.

JS, CSS и значок Swagger входят в зависимости JAR (WebJar). После сборки
документация работает без CDN; UI и API используют один origin, настройка CORS
для них не нужна. Токен не сохраняется между перезагрузками страницы,
внешний валидатор Swagger отключён. Версия UI задаётся в `libs.versions.toml`
и подставляется в HTML при `processResources`.

Контракт хранится явно в YAML, поэтому при изменении маршрута обновляйте его
вместе с кодом. Тесты проверяют валидность спецификации, список операций,
правила авторизации и выполняют примеры регистрации/входа/CRUD на настоящей БД.

## WebSocket-уведомления

Адрес: `ws://localhost:8080/ws/tasks` (при HTTPS — `wss://`). Соединение открывают
USER и ADMIN с заголовком `Authorization: Bearer <JWT>`, полученным через обычный
`POST /auth/login`. При отсутствии или недействительности JWT handshake получает
`401 UNAUTHORIZED` с JSON до перехода на WebSocket. Проверяются те же подпись,
issuer, audience, exp и существующий пользователь, что и у защищённых HTTP-маршрутов.
Токен в URL/query не принимается.

После ответа `101 Switching Protocols` сервер отправляет текстовые JSON-кадры:

```json
{"type":"READY"}
{"type":"TASK_CREATED","taskId":1}
{"type":"TASK_UPDATED","taskId":1}
{"type":"TASK_DELETED","taskId":1}
```

`READY` подтверждает установку подписки. После него загрузите `GET /tasks` для
начального состояния. События содержат только тип и ID: актуальные поля читаются
через `GET /tasks/{id}` или повторную загрузку списка. К моменту уведомления
транзакция PostgreSQL уже завершена; событие может прийти раньше HTTP-ответа
на изменяющий запрос. Повторный успешный PUT тоже порождает `TASK_UPDATED`.
Чтение, неверные данные, отказ `401`/`403`, отсутствующая задача и сбой записи
не порождают событий об успешном изменении.

Каждый подключённый клиент получает собственный поток, включая изменения,
которые он инициировал через HTTP. Сообщения от клиента не изменяют задачи
и не пересылаются другим: сервер закрывает такое соединение с кодом `1008`.
Для изменения задач используются POST/PUT/DELETE с существующими проверками ролей.
Истечение `exp` закрывает соединение с `1008`, даже если задач никто не меняет.
Перед отправкой события повторно проверяется существование пользователя в БД;
удалённому пользователю событие не отправляется, соединение закрывается с `1008`.

У каждого подписчика очередь на 64 события. Запись задачи не ждёт сетевой отправки;
переполнение очереди отключает только отстающего подписчика с кодом `1013`.
Исходящая очередь Ktor также ограничена (16 кадров), ожидание отправки — 5 секунд.
Сервер отправляет ping каждые 15 секунд, timeout — 15 секунд, максимальный кадр —
4096 байт. Входящая очередь ограничена 8 кадрами и закрывается при переполнении.

Доставка действует в пределах **одного экземпляра приложения**, без хранения
истории и повторной отправки. При разрыве, перегрузке или перезапуске события
могут быть потеряны: переподключитесь, дождитесь `READY` и перечитайте `GET /tasks`.
Изменения напрямую в БД и через другие экземпляры приложения не транслируются.
При одновременных изменениях уведомления служат сигналом перечитать состояние;
их порядок не заменяет порядок транзакций в БД. Гарантированной доставки
при сбое между commit БД и публикацией события на этом этапе нет.

### Ручная проверка в PowerShell 7

Откройте две PowerShell-сессии. В каждой войдите через `/auth/login`, как в примере
HTTP ниже, чтобы получить `$session.accessToken`, и выполните:

```powershell
$socket = [System.Net.WebSockets.ClientWebSocket]::new()
$socket.Options.SetRequestHeader('Authorization', "Bearer $($session.accessToken)")
$cancel = [System.Threading.CancellationToken]::None
try {
    $null = $socket.ConnectAsync([Uri]'ws://localhost:8080/ws/tasks', $cancel).GetAwaiter().GetResult()
    $buffer = [byte[]]::new(4096)
    while ($socket.State -eq [System.Net.WebSockets.WebSocketState]::Open) {
        $message = [System.IO.MemoryStream]::new()
        try {
            do {
                $pending = $socket.ReceiveAsync([ArraySegment[byte]]::new($buffer), $cancel)
                while (-not $pending.IsCompleted) { Start-Sleep -Milliseconds 100 }
                $frame = $pending.GetAwaiter().GetResult()
                if ($frame.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
                    Write-Output "Closed: $($frame.CloseStatus) $($frame.CloseStatusDescription)"
                    $null = $socket.CloseOutputAsync($frame.CloseStatus, 'Acknowledged', $cancel).GetAwaiter().GetResult()
                    return
                }
                $message.Write($buffer, 0, $frame.Count)
            } until ($frame.EndOfMessage)
            [Text.Encoding]::UTF8.GetString($message.ToArray())
        } finally { $message.Dispose() }
    }
} finally {
    $socket.Abort()
    $socket.Dispose()
}
```

После `READY` в обоих окнах создайте и обновите задачу через Swagger или третью
PowerShell-сессию, затем удалите её как ADMIN. Оба слушателя должны получить
CREATED, UPDATED и DELETED с одним ID. Ошибочное удаление от USER (`403`) не
создаёт события. Закройте один слушатель через `Ctrl+C`: второй продолжит получать
события. Новое соединение начнётся с READY без истории. После истечения JWT
нужно снова войти и открыть новое соединение.

Swagger UI показывает контракт handshake и схемы сообщений, но **Try it out**
не открывает WebSocket. Стандартный браузерный `new WebSocket()` не позволяет
задать заголовок Authorization; для этого контракта используйте клиент с поддержкой
заголовков, например приведённый .NET-клиент или Ktor Client.

## Логирование запросов

`configureRequestLogging()` устанавливает Ktor CallLogging. Logger `http.requests`
выводит одну строку уровня INFO после обработки запроса, включая ошибки:

```text
method=GET path=/tasks status=200 durationMs=7
method=POST path=/tasks status=401 durationMs=1
```

Logback добавляет дату, поток и уровень. В строке есть метод, путь без query,
итоговый HTTP-статус и длительность в миллисекундах. Заголовки, cookie, параметры
query и тела запросов/ответов не записываются: логин, пароль и JWT из этих частей
запроса не попадают в журнал доступа. Запросы файлов Swagger также журналируются.
Неожиданные исключения отдельно пишутся в серверный журнал на уровне ERROR,
клиент получает безопасный `500 INTERNAL_ERROR` без внутренних подробностей.
Для WebSocket журналируется HTTP-handshake (`101` или `401`); его длительность
не является временем жизни соединения. Содержимое WebSocket-сообщений не журналируется.

## Примеры запросов в PowerShell

```powershell
$baseUrl = 'http://localhost:8080'

# Информация об API
Invoke-RestMethod "$baseUrl/"

# Регистрация выполняется один раз: повтор того же логина вернёт 409.
$credentials = @{ login = 'student'; password = 'Study-password-2026!' } | ConvertTo-Json
Invoke-RestMethod "$baseUrl/auth/register" -Method Post `
    -ContentType 'application/json; charset=utf-8' -Body $credentials

# Вход и заголовок для защищённых маршрутов
$session = Invoke-RestMethod "$baseUrl/auth/login" -Method Post `
    -ContentType 'application/json; charset=utf-8' -Body $credentials
$authHeaders = @{ Authorization = "Bearer $($session.accessToken)" }
Invoke-RestMethod "$baseUrl/auth/me" -Headers $authHeaders # role: USER

# Создание: id назначает сервер
$task = Invoke-RestMethod "$baseUrl/tasks" -Method Post -Headers $authHeaders `
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
Invoke-RestMethod "$baseUrl/tasks/$($task.id)" -Method Put -Headers $authHeaders `
    -ContentType 'application/json; charset=utf-8' `
    -Body (@{
        title = 'Изучить PostgreSQL и Exposed'
        description = 'Полный CRUD готов'
        status = 'DONE'
    } | ConvertTo-Json)

# Для удаления войдите под администратором, созданным при запуске.
# DELETE с $authHeaders обычного пользователя вернёт 403.
$adminLogin = Read-Host 'Логин администратора'
$adminSecurePassword = Read-Host 'Пароль администратора' -AsSecureString
$adminCredentials = @{
    login = $adminLogin
    password = [System.Net.NetworkCredential]::new('', $adminSecurePassword).Password
} | ConvertTo-Json
$adminSession = Invoke-RestMethod "$baseUrl/auth/login" -Method Post `
    -ContentType 'application/json; charset=utf-8' -Body $adminCredentials
$adminSecurePassword.Dispose()
Remove-Variable adminCredentials
$adminHeaders = @{ Authorization = "Bearer $($adminSession.accessToken)" }
Invoke-RestMethod "$baseUrl/auth/me" -Headers $adminHeaders # role: ADMIN

# Удаление администратором: 204 без тела
Invoke-WebRequest "$baseUrl/tasks/$($task.id)" -Method Delete -Headers $adminHeaders |
    Select-Object StatusCode
```

Повторное получение или удаление администратором этой задачи вернёт `404`.
Удаление без токена вернёт `401`, с ролью USER — `403`.
Чтение не требует заголовка авторизации.

Для ручной проверки сохранности создайте задачу и запомните её ID, остановите
приложение, выполните `docker compose restart postgres`, затем снова запустите
приложение. `GET /tasks/{id}` должен вернуть ту же задачу. После PUT и перезапуска
должны сохраниться новые поля, после DELETE и перезапуска — возвращаться `404`.
Учётная запись с хэшем пароля также сохраняется. При неизменном `JWT_SECRET` выданный JWT
работает после перезапуска до наступления `exp`.

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
| `400` | `VALIDATION_ERROR` | Некорректные ID, статус, поля задачи или формат логина/пароля |
| `400` | `INVALID_REQUEST` | Повреждённый JSON, неправильные типы, отсутствующие обязательные или неизвестные поля |
| `401` | `INVALID_CREDENTIALS` | Неизвестный логин или неверный пароль |
| `401` | `UNAUTHORIZED` | Для записи задачи, `/auth/me` или WebSocket-handshake отсутствует действительный Bearer JWT |
| `403` | `FORBIDDEN` | Токен действителен, но для удаления задачи требуется ADMIN |
| `409` | `LOGIN_ALREADY_TAKEN` | Логин уже зарегистрирован |
| `404` | `TASK_NOT_FOUND` | Нет задачи с таким ID |
| `404` | `ROUTE_NOT_FOUND` | Неизвестный маршрут |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | Тело передано не как `application/json` |
| `500` | `INTERNAL_ERROR` | Неожиданная ошибка; подробности остаются в серверном логе |

## Проверки

```powershell
.\gradlew.bat test
.\gradlew.bat build

# Проверки КТ3 (OpenAPI, примеры API, ошибки и журналирование)
.\gradlew.bat test --tests 'com.vasilevyuv.application.*'

# Роли, создание администратора, миграция и настройки
.\gradlew.bat test --tests '*RoleAccessTest' --tests '*AdminBootstrap*Test'

# WebSocket и отсутствие событий при сбое хранилища
.\gradlew.bat test --tests 'com.vasilevyuv.notifications.*' --tests '*TaskNotificationFailureTest'
```

После первой успешной загрузки зависимостей можно добавить `--offline`.
HTML-отчёт тестов: `build/reports/tests/test/index.html`.

API-тесты используют Testcontainers 2.0.5: один одноразовый PostgreSQL 18.6 на случайном
порту для каждого тестового класса. Локальная БД Compose не требуется и не очищается.
Нужен работающий Docker; отсутствие Docker приводит к ошибке, а не к пропуску тестов.
Testcontainers автоматически останавливает и удаляет свой контейнер после тестов.
Проверки плагинов `ErrorsTest` и `RequestLoggingTest` используют небольшое тестовое
приложение без БД; аварийные маршруты существуют только в тестах.

Ktor Test Engine запускает настоящий модуль приложения с настройками тестовой БД.
Перед обычным тестом очищаются только таблицы одноразового контейнера.
В проверке сохранности данных несколько экземпляров приложения последовательно
подключаются к той же БД без очистки. HTTP-порт тестовый движок не открывает;
загрузка `application.yaml` и запуск Tomcat проверяются отдельно на собранном JAR.
Тесты сами задают отдельный тестовый секрет JWT и пароль тестовой БД —
переменные окружения для них не требуются. CRUD-тесты создают ADMIN при запуске
и получают токен через настоящий вход. Отдельные тесты проходят публичную
регистрацию USER и проверяют разрешённые и запрещённые действия обеих ролей.

Покрытие: CRUD, фильтры, JSON и HTTP-статусы, границы полей, ошибочные PUT без
изменения данных, отсутствие upsert, идемпотентность PUT, буквальный поиск спецсимволов,
кириллица, конкурентное создание и сохранность изменений между экземплярами приложения.
Дополнительно проверяются соль и стоимость BCrypt непосредственно в БД,
уникальность логина при конкуренции, границы пароля в UTF-8, отсутствие приватных
полей в ответах, вход после перезапуска, публичное чтение, защита трёх методов
записи, подпись/алгоритм JWT, issuer/audience, срок действия, обязательные claims,
неизвестный и удалённый пользователь, приоритет аутентификации перед валидацией.

КТ3 добавляет проверку OpenAPI официальным Swagger Parser, доступности HTML/JS/CSS,
изоляции статических ресурсов от конфигурации приложения и выполнения примеров
из YAML. Отдельно проверяются безопасный JSON `500`, работоспособность после ошибки,
единый формат ошибок, итоговые статусы/длительность в журнале и отсутствие
паролей, JWT, cookie и тел в журнале доступа.

КТ4 добавляет проверки матрицы доступа и `/auth/me`, приоритета `401`/`403`,
невозможности повысить роль через запрос или claim JWT, применения изменения роли
к прежнему токену и отказа доступа удалённому пользователю. Проверяются безопасное
создание ADMIN, повторный и конкурентный запуск, отказ при конфликте с USER
или другим паролем, продолжение работы после удаления настроек создания.
Миграция проверяется на предыдущей схеме без `role`: пользователи, хэши,
задачи и действующие JWT сохраняются.

WebSocket-тесты проверяют доставку CREATED/UPDATED/DELETED обоим ролям,
параллельные изменения, неуспешный handshake, отказ токену из query,
отсутствие событий при ошибках и чтении, отключение и повторное подключение
без истории, запрет сообщений клиента, истечение JWT и удаление пользователя.
Отдельно проверяются переполнение очереди, независимость активного клиента
от медленного, освобождение подписки и отсутствие публикации при ошибке хранилища.

Результаты текущего этапа КТ4 — Docker (22.10.2026):

- `./gradlew.bat test build --console=plain --no-daemon` — успешная сборка,
  87 тестов, 0 ошибок и 0 пропусков. Отдельно успешно собран Linux-образ
  из исходников через Dockerfile; локальный JAR в образ не копируется.
- `docker compose up -d --build --wait` с отдельным проверочным env-файлом
  запустил оба сервиса до состояния `healthy`. HTTP проверен на порту 18081.
  Приложение работает от UID 10001 с файловой системой только для чтения,
  Tomcat использует `/tmp/tomcat`, в `/app` находится только `app.jar`.
- Через настоящий HTTP проверены регистрация `201`, повторная регистрация `409`,
  неверный пароль `401`, вход USER и ADMIN, роли в `/auth/me`, CRUD
  (`201/200/204`), чтение и фильтр, запрет записи без JWT (`401`) и удаления
  от USER (`403`). Swagger UI, все локальные ресурсы и OpenAPI доступны.
- Настоящие WebSocket-клиенты USER и ADMIN получили события создания и
  обновления; подключение без JWT вернуло `401`. После пересоздания контейнеров
  старый JWT позволил подключиться заново и получить обновление и удаление.
- Выполнены два цикла `docker compose down` **без `-v`** и `up -d --wait`.
  Контейнеры действительно пересозданы; задача, ID пользователей, роли и хэши
  паролей сохранены. Прежние JWT и вход с паролем работают. После удаления
  задачи и второго пересоздания чтение возвращает `404`.
- Дополнительно проверен первый запуск в отдельном Compose-проекте с новым
  пустым томом и случайным паролем БД: схема создаётся автоматически,
  регистрация и работа с задачами доступны. Пустые настройки ADMIN не создают
  аккаунт; пустые обязательные секреты отклоняются Compose до запуска.
- Проверочные записи удалены; ранее существовавшие записи основного тома
  не изменились. Контейнеры проекта остановлены, основной том
  `kotlin-2026_postgres-data` сохранён. Временный проект с новой БД и его том
  удалены; контейнеры Testcontainers очищены автоматически.

История проверки КТ4 — WebSocket (22.10.2026, коммит `c083d4c`):

- `./gradlew.bat test build` — успешная сборка, 87 тестов, 0 ошибок и 0 пропусков.
  К 74 проверкам предыдущего этапа добавлены 8 WebSocket-проверок на PostgreSQL,
  4 проверки очередей и подписок, 1 проверка отсутствия публикации при сбое хранилища.
- Тесты подтверждают доставку всем подписчикам, конкурентные изменения,
  применение JWT при подключении, закрытие по exp, отказ удалённому пользователю,
  отсутствие событий при ошибках, переподключение без истории и ограничения очередей.
- OpenAPI проходит Swagger Parser; handshake, авторизация и схемы уведомлений
  согласованы с кодом. Существующие HTTP-примеры регистрации, входа и CRUD работают.
- Собранный JAR проверен с Tomcat на `127.0.0.1:18081` через настоящие
  HTTP-запросы и .NET ClientWebSocket. Отсутствующий и неверный JWT дают `401`;
  USER и ADMIN подключаются с `101` и получают READY.
- Оба клиента получили CREATED, UPDATED и DELETED с правильным ID.
  Удаление от USER (`403`) не создало события; после отключения первого клиента
  второй продолжил получать изменения. Новое соединение не получило историю.
  Поддельное событие от клиента завершило только его соединение с кодом `1008`.
- В журнале handshake подтверждены `401` и `101`. Проверочные записи удалены,
  соединения и Java-процесс закрыты, PostgreSQL Compose остановлен, том сохранён.
  Контейнеры Testcontainers удалены автоматически.

История проверки КТ4 — роли (22.10.2026, коммит `effc53e`):

- `./gradlew.bat test build` — успешная сборка, 74 теста, 0 ошибок и 0 пропусков.
  К 59 тестам предыдущего этапа добавлены 5 проверок доступа, 8 проверок
  создания администратора и миграции, 2 проверки настроек администратора.
- В тестах PostgreSQL подтверждены сохранность данных при переходе со схемы
  без `role`, отсутствие автоматического повышения USER, отказ при неверном
  пароле существующего ADMIN и применение изменения роли к прежнему JWT.
- OpenAPI проходит Swagger Parser; примеры регистрации и CRUD выполняются
  с разделением прав USER/ADMIN, включая `403` и успешное удаление администратором.
- Собранный JAR проверен через настоящий HTTP с Tomcat на `127.0.0.1:18081`.
  Подтверждены настройки из `application.yaml`, запуск без создания ADMIN,
  регистрация USER `201`, запрет поля role `400`, `/auth/me` для обеих ролей,
  создание/обновление USER `201`/`200` и удаление: без JWT `401`, USER `403`, ADMIN `204`.
- После перезапуска сохранены ID и хэш ADMIN. После удаления настроек создания
  его ранее выданный JWT продолжает работать. Попытка создать ADMIN с логином
  существующего USER завершает запуск ошибкой и сохраняет роль USER.
- Проверочные пароли, JWT и секрет подписи отсутствуют в журналах запуска
  и HTTP-проверки. Проверочные строки удалены; Java-процессы и PostgreSQL Compose
  остановлены, том сохранён. Контейнеры Testcontainers удалены автоматически.

История проверки КТ3 (22.10.2026, коммит `41c1bdf`):

- `./gradlew.bat test build` — успешная сборка, 59 тестов, 0 ошибок и 0 пропусков.
  Сохранены 50 проверок КТ2; добавлены 5 проверок документации и её примеров,
  2 проверки ошибок и 2 проверки журналирования.
- Swagger Parser принимает OpenAPI без ошибок и предупреждений; примеры
  регистрации, входа и полного CRUD из спецификации выполняются на PostgreSQL.
- Собранный JAR проверен с Tomcat на `127.0.0.1:18081`: `/docs`, YAML,
  CSS, значок и оба JS-ресурса возвращают `200` без обращения к CDN.
- В Chrome Swagger отображает схемы, примеры и защиту Bearer; запрос
  `GET /tasks` через **Try it out → Execute** возвращает `200` и JSON.
- Настоящие HTTP-запросы записывают итоговые статусы, включая `401`, и длительность.
  Тестовые маркеры пароля, токена, cookie и query отсутствуют в журнале.
- Проверочные Java-процессы и PostgreSQL Compose остановлены, том сохранён;
  контейнеры Testcontainers удалены. Публикация и сдача КТ в NewLXP не выполнялись.

История проверки КТ2 JWT (22.10.2026, коммит `da66646`):

- `./gradlew.bat test build` — сборка успешна; 50 тестов, 0 ошибок и 0 пропусков:
  28 CRUD, 12 регистрации/входа, 7 защиты JWT на настоящем PostgreSQL
  и 3 проверки настроек JWT.
- Собранный JAR запускается с настройками БД и JWT из окружения через
  `application.yaml`; Tomcat проверен на `127.0.0.1:18081`.
- Через настоящий HTTP проверены регистрация `201`, повторный логин `409`,
  неверный пароль `401`, успешный вход `200`, публичное чтение `200`.
- POST/PUT/DELETE без токена возвращают `401`; неверный токен также отклоняется.
  С выданным JWT подтверждены создание `201`, обновление `200`, удаление `204`
  и последующее чтение удалённой задачи `404`.
- После перезапуска приложения и PostgreSQL сохранены задача и возможность
  входа. Ранее выданный токен продолжает разрешать запись при том же секрете.
- Проверочные строки удалены, приложение и контейнер Compose остановлены,
  том БД сохранён. Одноразовые контейнеры Testcontainers удалены автоматически.

История проверки КТ2 CRUD (22.10.2026, коммит `d6120ab`):

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
- [JWT в Ktor](https://ktor.io/docs/server-jwt.html)
- [BCrypt: библиотека и ограничение 72 байта](https://github.com/patrickfav/bcrypt)
- [Спецификация OpenAPI 3.0.3](https://spec.openapis.org/oas/v3.0.3.html)
- [Swagger UI](https://github.com/swagger-api/swagger-ui)
- [Swagger Parser](https://github.com/swagger-api/swagger-parser)
- [CallLogging в Ktor](https://ktor.io/docs/server-call-logging.html)
- [WebSockets в Ktor Server](https://ktor.io/docs/server-websockets.html)
- [WebSockets в Ktor Client](https://ktor.io/docs/client-websockets.html)
- [Настройка очередей WebSocket в Ktor](https://api.ktor.io/ktor-websockets/io.ktor.websocket/-web-socket-channels-config/index.html)
- [Многоэтапная сборка Docker](https://docs.docker.com/build/building/multi-stage/)
- [Порядок запуска сервисов Compose](https://docs.docker.com/compose/how-tos/startup-order/)
- [Переменные окружения и .env в Compose](https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/)
