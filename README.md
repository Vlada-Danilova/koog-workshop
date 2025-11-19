# Koog Workshop — SQL Drama Tutor

Public workshop materials for building AI Agents with [Koog](https://koog.ai). This project contains a small but complete SQL Tutor that can:
- analyze and optimize SQL queries using the actual database schema,
- show table schemas from an introspected YAML (Nemory),
- generate SQL practice challenges and validate your answers — without leaking solutions.


## Architecture overview

Main modules and responsibilities:
- UI (Swing) — file: `src/main/kotlin/ai/koog/workshop/sqltool/QueryTutorUi.kt`
  - Renders the chat window, buttons, and user input area.
  - Delegates all agent-related work to the controller.

- Controller/Service — file: `src/main/kotlin/ai/koog/workshop/sqltool/QueryTutorController.kt`
  - Encapsulates agent orchestration and state: conversation history and active challenge id.
  - Builds Koog `AIAgent` instances with the right prompts and registered tools.
  - Parses `CHALLENGE_ID: <uuid>` from agent responses and routes raw SQL to validation when a challenge is active.

- Agent (Koog) — minimal sample in `src/main/kotlin/ai/koog/workshop/sqltool/QueryTutorAgent.kt` and created on the fly by UI/Controller
  - Uses `simpleGraziePromptExecutor` with your `GRAZIE_TOKEN`.
  - Calls project tools via Koog `ToolRegistry`.

- Tools — file: `src/main/kotlin/ai/koog/workshop/sqltool/tools/QueryTutorTools.kt`
  - `analyze_query` — parses SQL with JSqlParser and reports complexity, concepts, and potential issues.
  - `get_table_schema` — returns detailed schema info from Nemory YAML.
  - `suggest_optimizations` — provides concrete perf suggestions.
  - `generate_challenge_structured` — returns only question metadata (no solution) and stores the internal solution in-memory.
  - `validate_challenge_answer` — checks your SQL against the stored internal solution and replies with pass/fail, never revealing the answer.

- Integration — file: `src/main/kotlin/ai/koog/workshop/sqltool/integration/NemoryConnector.kt`
  - Loads an introspected database schema from YAML (Nemory) and exposes helper queries for tables, columns, indexes, and samples.

### Data flow (high level)
1. UI collects user input and passes it to the Controller.
2. Controller creates/runs an Agent with appropriate system prompts and ToolRegistry.
3. Agent chooses tools to call based on the instruction format:
   - `analyze: <sql>` → analyze_query
   - `optimize: <sql>` → suggest_optimizations
   - `schema <table>` → get_table_schema
   - `challenge <level>` → generate_challenge_structured (agent must include `CHALLENGE_ID: ...`)
   - Raw SQL while a challenge is active → validate_challenge_answer
4. Controller captures agent output, updates state (e.g., challenge id), and notifies the UI via callbacks.

### Challenge lifecycle
- Start: user presses “Challenge” and picks a level (beginner/intermediate/advanced).
- Agent calls `generate_challenge_structured` and replies with text + `CHALLENGE_ID: <uuid>`.
- UI shows the challenge; the controller stores the id.
- Answer: user types just SQL (no analyze/optimize/schema/challenge prefix).
- Controller wraps this as a `challenge_answer` message and the agent calls `validate_challenge_answer`.
- The agent replies with `✅` (success) or `❌` (try again). Controller clears the active id on success.

## Getting started

Prerequisites:
- Java 17+
- Kotlin/Gradle (wrapper included)
- A valid JetBrains Grazie token in env var `GRAZIE_TOKEN`
- A Nemory YAML describing your DB schema in env var `NEMORY_SCHEMA_PATH`

Install deps and build:
- macOS/Linux: `./gradlew build`
- Windows: `gradlew.bat build`

Environment variables:
- `GRAZIE_TOKEN` — required to call the LLM via Koog’s Grazie executor
- `NEMORY_SCHEMA_PATH` — full path to your introspected schema YAML

## Running
Swing UI:
- Run: `./gradlew run -PmainClass=ai.koog.workshop.sqltool.QueryTutorUiKt`
- On first launch, the UI shows DB info at the top bar and chat controls at the bottom.

## Demo examples

Use these examples in the UI input field.

1) Analyze a query
- Input: `analyze: SELECT id, name FROM customers WHERE status = 'Active'`
- Expected: The agent calls `analyze_query` and reports complexity, used concepts, and any issues.

2) Optimize a query
- Input: `optimize: SELECT * FROM orders WHERE customer_id IN (SELECT id FROM customers)`
- Expected: Suggestions may include replacing IN-subquery with JOIN and adding indexes.

3) Show a table schema
- Input: `schema orders`
- Expected: Detailed columns, PK/FK, indexes, and a couple of sample rows.

4) Practice challenge — Beginner
- Input: `challenge beginner`
- Agent behavior: Must show a problem statement and include `CHALLENGE_ID: <uuid>` on the last line.
- Then answer by typing only your SQL, for example:
  - `SELECT * FROM orders WHERE status = 'Pending';`
- Expected: Validation result starting with `✅` or `❌` without revealing any internal solution.

5) Practice challenge — Intermediate/Advanced
- Input: `challenge intermediate` or `challenge advanced`
- Then answer with your SQL.
- Expected: Pass/fail reply based on JOIN or GROUP BY requirements respectively.

Tips:
- If you switch schema (UI top-right button), chat history and active challenge are cleared.
- Raw SQL is treated as a challenge answer only while a challenge id is active.

## Troubleshooting
- “Missing GRAZIE_TOKEN”: export your token, e.g. `export GRAZIE_TOKEN=***` and restart.
- “Nemory YAML not found”: set `NEMORY_SCHEMA_PATH` to a valid file path.
- If the agent forgets to include `CHALLENGE_ID`, ask for another challenge; the UI expects that line to activate validation.

## Useful materials
- [Koog documentation](https://koog.ai/docs)
- [Koog GitHub repository](https://github.com/jetbrains/koog)
- [Kotlin documentation](https://kotlinlang.org/docs)
- [Introduction to agents](https://docs.google.com/presentation/d/1679t_0B2x6uYsdOk_VOArpSIXeaZL00EiFluugUYOso/edit?usp=sharing)
- [Introduction to Koog agents](https://docs.google.com/presentation/d/1YPeF38PXCw1-QtCAbBr6la3pwuqZtgz2paJsxwb_yAU/edit?usp=sharing)
