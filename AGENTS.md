# Agent Notes

## Project Overview
- Spring Boot 3.1.5, Java 17, Gradle.
- Telegram bot (long polling) with channel forwarding logic in `src/main/java/io/github/yienruuuuu/service/application/telegram/main_bot/MainBotConsumer.java`.

## How To Run
- Build: `gradlew.bat build`
- Run: `gradlew.bat bootRun`
- Tests: `gradlew.bat test`

## Configuration
- Local config: `src/main/resources/application-local.properties`
- Default config: `src/main/resources/application.properties`
- Logback: `src/main/resources/logback.xml`

## Data & Migrations
- SQL scripts: `src/main/resources/common-script/`

## Skills
- No repo-local skills defined.
