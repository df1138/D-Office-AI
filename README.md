# OfficeClone — Android office suite MVP

OfficeClone 1.4.0 is an offline-first Android project written in Kotlin with Jetpack Compose. It is an extensible starter for documents, spreadsheets, and presentations.

## Current features

### Documents
- Native Android text input with bold, italic, and underline formatting.
- Undo/redo history (up to 100 snapshots).
- Find and replace all, with case-insensitive matching.
- Word and character counter.
- Open and save `.docx` using Android's system document picker, including Google Drive when its Android provider is installed and signed in.
- Google Drive import/export shortcuts use Android Storage Access Framework (SAF), so users can choose Google Drive or another available cloud/storage provider without embedding OAuth secrets in the app.
- DOCX bridge preserves paragraphs and basic bold/italic/underline runs; it is not a full Microsoft Word compatibility engine.

### Spreadsheets
- 50 × 10 editable grid with cell references.
- Arithmetic formulas with `+`, `-`, `*`, `/`, parentheses, and cell references.
- `SUM`, `AVERAGE`/`AVG`, `MIN`, `MAX`, and `COUNT` functions with cell ranges such as `=SUM(A1:A5)`.
- Detects basic formula errors, division by zero, and circular references.
- Open and save `.xlsx` files using Android's system document picker.
- Dedicated import-from-Drive and export-to-Drive actions; select Google Drive in the system file picker to read or write files stored there.
- Multi-sheet workbooks: open and export up to 20 worksheets, switch between tabs, add sheets, and rename them.
- XLSX bridge reads/writes formulas, numbers, booleans, inline strings, and shared strings without third-party spreadsheet libraries.
- Sort rows by a selected column (the first row is kept as the header), ascending or descending.
- Column widths adapt to the visible cell content, with a mobile-friendly maximum width.
- Imported worksheets are capped at 200 rows × 30 columns and XLSX archives are size-limited for safer handling on phones.

### Presentations
- Starter slide canvas, slide selection, and adding text/shape elements.
- PPTX export and drag-to-position editing are not implemented yet.

## Google Drive import/export

OfficeClone uses Android's Storage Access Framework (`OpenDocument` and `CreateDocument`). When the Google Drive app is installed, signed in, and exposes its document provider, choose **Google Drive** in the system file picker to import a DOCX/XLSX file or save/export to Drive. This avoids requesting broad storage permissions and does not require a Drive API key in the source code. The current implementation copies/imports and exports files; it does not provide background sync, shared-drive administration, or Google Docs-native conversion. The cloud buttons open the same Android picker and do not bypass Google's account/permission dialogs.

## Build the Android app

Requirements: Android SDK, JDK 17, and Gradle 8.7. Open the `OfficeClone` directory in Android Studio and run **Build > Build APK(s)**.

Or push the project to GitHub. The workflow `.github/workflows/android-build.yml` runs `gradle --no-daemon assembleDebug` and uploads `app/build/outputs/apk/debug/app-debug.apk` as the `OfficeClone-debug-apk` artifact.

`local.properties` is intentionally excluded because the Android SDK path is machine-specific.

## Known limitations

- DOCX import/export is intentionally lightweight. Complex layouts, tables, images, comments, headers/footers, and advanced styles are not preserved.
- XLSX support is a lightweight compatibility layer: multiple worksheets are supported, but cell styles, merged cells, charts, images, named ranges, external links, and advanced Excel features are not preserved. The formula engine covers common basic cases rather than all Excel functions. Formula references across different sheets are not yet calculated.
- Presentation data is currently in memory and cannot yet be exported to PPTX.
- This environment has no Android SDK or Gradle installation, so a full APK build could not be run here. Build validation should be completed with Android Studio or the included GitHub Actions workflow.


## App icon (v1.4.1)

The launcher icon now uses the supplied OfficeClone artwork. Adaptive and legacy density-specific launcher assets are included under `app/src/main/res`.


## OfficeClone 1.5.0

### Added in this version
- Local autosave for the current document text and spreadsheet workbook in app-private preferences.
- Recovery of the last document draft and workbook when the editor ViewModel starts again.
- A recent-files list on the home screen (name and format history; it is not yet a cloud file browser).
- CSV import/export for the first worksheet, in addition to DOCX and XLSX workflows.
- Branded home screen using the supplied OfficeClone icon.
- Android splash screen using the app launcher icon and navy background.
- GitHub Actions workflow continues to build a debug APK and publish it as an artifact.

### Current limitations
- Autosaved Word recovery currently restores plain text; rich-text styling is not preserved in the local emergency draft.
- Recent-files history stores labels and formats, not persistent access to original file URIs; tap-to-reopen is not implemented yet.
- CSV export currently exports the first worksheet only.
- Google Drive access uses Android's system document picker; direct Drive API sync is not implemented.
- APK build and device testing must be run by GitHub Actions or Android Studio; this environment does not have Gradle/Android SDK installed.

## Конвертер файлов (v1.6.0)

В приложении добавлен раздел «Конвертер» для преобразования распространённых офисных форматов:

- TXT ↔ HTML и преобразование текста в DOCX;
- DOCX → TXT, HTML, CSV/XLSX (текстовое содержимое; форматирование при конвертации в другой формат не сохраняется);
- CSV ↔ XLSX (первый лист; до 200 строк и 30 столбцов в текущем редакторе);
- TXT, HTML, DOCX, CSV и XLSX → PDF.

Исходный файл выбирается через системный файловый интерфейс Android, результат сохраняется через системный диалог «Сохранить». Ограничение размера входного файла — 25 МБ. Это не универсальный конвертер всех существующих форматов: импорт PDF, редактирование PDF, PPTX, ODT, RTF, изображений, макросов и сложных офисных элементов пока не поддерживаются. Формулы XLSX при конвертации в CSV становятся текстовыми значениями формул/ячейки согласно возможностям текущего моста.

Сборка: GitHub Actions workflow `.github/workflows/android-build.yml` собирает debug APK и публикует его как артефакт `OfficeClone-debug-apk`. Сборку локально следует выполнять через установленный Android SDK и Gradle 8.7.


## OfficeClone 1.8.0 — OCR

- Добавлен OCR для изображений: выбор изображения через системный диалог, распознавание текста с помощью ML Kit на устройстве, редактирование результата и сохранение в TXT. Модуль не загружает изображение в облако.
- Конвертер сохраняет базовую поддержку RTF как текстового формата; это не полная поддержка оформления RTF.
- Импорт PDF, PDF → DOCX, ODT, создание/импорт PPTX и прямой Drive API sync всё ещё не реализованы. Доступ к файлам Google Drive возможен только через системный Android-пикер, если провайдер Drive доступен на устройстве.
- APK в этой среде не собран: Gradle/Android SDK не обнаружены. Сборку необходимо выполнить Android Studio или GitHub Actions.

## OfficeClone 1.9.0 — импорт PDF, ODT/PPTX и прямой Google Drive API

### Что добавлено в исходный код
- **PDF → DOCX**: извлечение текстового слоя PDF через PDFBox Android и создание DOCX из извлечённого текста. Это не восстановление оригинальной вёрстки. Сканированные страницы без текстового слоя требуют OCR отдельно.
- **ODT**: чтение текста/абзацев из `content.xml` и создание базового `.odt`. Сложные стили, таблицы, изображения, сноски и поля не сохраняются.
- **PPTX**: импорт текстовых блоков по XML слайдов и экспорт редактируемой презентации с текстовыми слайдами. Тема, исходная геометрия, диаграммы, медиа, SmartArt, анимации и произвольные макеты не импортируются. Поэтому это базовая текстовая совместимость, не полная совместимость PowerPoint.
- **Google Drive API**: отдельный экран Drive, OAuth sign-in, список файлов, загрузка файла и скачивание файла через REST API. Это прямой API-клиент, не фоновая двусторонняя синхронизация/конфликт-резолвер.
- **Навигация**: экран Drive доступен с нижней панели.

### Обязательная настройка Google OAuth
1. В Google Cloud Console создайте/выберите проект и включите Google Drive API.
2. Настройте OAuth consent screen и добавьте тестовые аккаунты, если приложение в режиме Testing.
3. Создайте OAuth Client ID типа **Web application** и добавьте его в Android-ресурс `default_web_client_id` (значение — клиентский ID с окончанием `.apps.googleusercontent.com`).
4. Для корректной конфигурации Google Sign-In зарегистрируйте Android OAuth client с package `com.officeclone.app` и SHA-1 сертификата подписи (debug/release — разные отпечатки).
5. Запустите приложение, войдите в Google, затем проверьте список/загрузку/скачивание файлов. Реальный API-вызов требует корректного OAuth-проекта и доступа в сеть.

### Ограничения и проверка
- Лимит файлов в этих мостах — 25 МБ.
- PDF: только извлечение текстового слоя; расположение текста, колонки, формы и сканированные страницы не реконструируются.
- PPTX: импорт/экспорт текста слайдов, не всех элементов PowerPoint.
- Drive: пользовательский OAuth и API-операции; автоматическая фоновая синхронизация и синхронизация правок существующего файла не реализованы.
- APK в среде разработки не собран: Android SDK/Gradle здесь отсутствуют. ZIP-архив можно проверить отдельно; компиляция и реальный OAuth/PDF/PPTX тест требуют Android Studio/GitHub Actions и тестового Google-проекта.

## Следующий этап разработки
Следующий этап — структурный импорт/экспорт DOCX/PPTX (таблицы, базовые стили и расположение элементов), затем автоматические unit-тесты мостов форматов и синхронизация Drive с обновлением существующих файлов и обработкой конфликтов.


## OfficeClone 2.0.0 — структурные офисные форматы и Drive sync

### PPTX: структурный мост
- Добавлен `PptxBridge.importSlides/exportSlides`: переносит слайды, фигуры/текстовые блоки, координаты и размеры в EMU, тип базовой фигуры, заливку, текст и базовые атрибуты текста (жирный, курсив, подчёркивание, размер и цвет).
- API принимает/возвращает структурную модель; при выборе PPTX → PPTX конвертер теперь выполняет структурный разбор и пересборку вместо копирования исходных байтов. TXT ↔ PPTX остаётся текстовым путём.
- Не поддерживаются гарантированно: картинки, диаграммы, SmartArt, группировки, повороты, прозрачность, темы исходного файла, мастер-слайды, анимации, точные переносы и смешанное форматирование в пределах нескольких текстовых фрагментов. Это частичная структурная совместимость, не полноценный клон PowerPoint.

### DOCX и ODT: таблицы и оформление
- DOCX экспортирует строки с табуляцией как настоящие таблицы Word, а обычные абзацы сохраняют базовое bold/italic/underline форматирование. Импорт вытаскивает ячейки таблиц в таб-разделённый текст.
- ODT экспортирует последовательности строк с табуляцией в таблицы OpenDocument; импорт извлекает текст ячеек. Сложные стили, объединённые ячейки, ширины, изображения и многоуровневые таблицы не сохраняются полностью.

### Проверки архивов и автоматические тесты
- Добавлен `OfficeArchiveValidator`: проверяет обязательные элементы DOCX/ODT/PPTX/XLSX, дублирующиеся записи, небезопасные пути и лимит распакованного размера. Валидатор вызывается после конвертации в офисный формат.
- Добавлены unit-тесты ODT/PPTX round-trip и проверки структуры ZIP. GitHub Actions сначала выполняет `testDebugUnitTest`, затем `assembleDebug`.

### Google Drive: обновление и изменения
- Добавлено обновление существующего файла через `PATCH /upload/drive/v3/files/{id}`. Если передан ожидаемый `headRevisionId`, клиент проверяет текущую ревизию и прекращает запись при конфликте, не затирая более новую версию.
- Добавлен cursor-based polling `changes/startPageToken` и `changes`, курсор сохраняется в SharedPreferences. На экране Drive можно выбрать файл для обновления и вручную проверить изменения. Это polling по запросу пользователя, не фоновая синхронизация в реальном времени.

### Статус сборки
В текущем рабочем окружении не обнаружены Android SDK и Gradle, поэтому APK и Android unit-тесты локально не запускались. Исходники, тесты и workflow подготовлены; фактический результат компиляции необходимо получить запуском GitHub Actions или Android Studio. OAuth и реальные Drive-конфликты требуют настроенного Google Cloud проекта и тестового аккаунта.
