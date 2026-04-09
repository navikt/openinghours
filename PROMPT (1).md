# Prompt: Build an Opening Hours Microservice

Build a standalone **opening hours microservice** — a central calendar service that manages opening hours rules for any external resource (services, offices, etc.). The primary consumer is a status monitoring platform, but the API should be generic enough for any application that needs to know "is resource X open on date Y?"

## Tech Stack

- **Java 21** (use modern features: records, sealed interfaces, pattern matching switch)
- **Maven multi-module** project with three modules: `core`, `api`, `server`
- **Fluent JDBC** (0.3.0) for database access — no ORM
- **action-controller** (0.0.32) for REST routing (lightweight, annotation-based)
- **jsonbuddy** (0.18.1) for JSON serialization
- **Jetty 9** embedded web server (required for javax.servlet compatibility with action-controller)
- **PostgreSQL 16** with **Flyway** migrations
- **HikariCP** connection pool
- **SLF4J** + **logevents** for logging
- **JUnit 5** + **AssertJ** for tests

## Architecture

```
┌─────────────────────────────────────────────────────┐
│ server module                                       │
│   ApningstiderServer.java  (Jetty setup, main())    │
│   DataSourceFactory.java   (HikariCP + Flyway)      │
├─────────────────────────────────────────────────────┤
│ api module                                          │
│   ApningstiderApi.java     (ServletContextHandler)   │
│   ApiFilter.java           (DB connection per req)   │
│   controllers/                                       │
│     RuleController          POST/GET/PUT/DELETE      │
│     GroupController         POST/GET/PUT/DELETE      │
│     ResourceController      PUT/DELETE/GET           │
│     QueryController         GET (public, no auth)    │
│     HealthController        /internal/isAlive|Ready  │
├─────────────────────────────────────────────────────┤
│ core module                                         │
│   entities/    (immutable records)                    │
│   repositories/ (Fluent JDBC)                        │
│   openingHours/ (parser + validator)                 │
│   db/migration/ (Flyway SQL)                         │
└─────────────────────────────────────────────────────┘
```

### Request Flow

1. Jetty receives HTTP request
2. `ApiFilter` opens a DB connection from the pool, binds it to the thread via `DbContext.startConnection()`, and starts a transaction
3. `ApiServlet` (from action-controller) routes to the correct controller method
4. Controller calls repository → repository uses the thread-bound connection
5. `ApiFilter` commits the transaction and returns the connection to the pool
6. If anything fails, the transaction rolls back automatically

## Rule DSL

The core of the system is a **domain-specific language (DSL)** for expressing opening hours rules. Each rule is a single string with 4 space-separated parts:

```
DD.MM.YYYY  DAY_IN_MONTH  WEEKDAY  HH:MM-HH:MM
```

### Part 1: Date (DD.MM.YYYY)
- `??.??.????` — matches any date (wildcard)
- `??.04.????` — matches any day in April, any year
- `24.12.????` — matches December 24th, any year
- `01.05.2023` — matches exactly May 1st 2023

### Part 2: Day in Month
- `?` — matches any day of the month
- `L` — matches the last day of the month (28/29/30/31 depending on month)
- `1-5` — matches days 1 through 5
- `1-5,25-30` — matches days 1-5 AND 25-30
- `6` — matches exactly the 6th
- `12-15` — matches days 12 through 15
- `1-5,10-L` — matches days 1-5 and 10 through end of month

### Part 3: Weekday (ISO 8601: 1=Monday, 7=Sunday)
- `?` — matches any weekday
- `1-5` — Monday through Friday
- `6-7` — Saturday and Sunday
- `1-2` — Monday and Tuesday

### Part 4: Opening Hours (HH:MM-HH:MM)
- `07:00-21:00` — open 7am to 9pm
- `00:00-00:00` — closed all day
- `00:00-23:59` — open all day

### Example Rules
```
??.??.???? ? 1-5 07:00-21:00      → Weekdays 7am-9pm (catch-all)
24.12.???? ? 1-5 09:00-14:00      → Christmas Eve weekday 9am-2pm
17.05.???? ? ? 00:00-00:00         → May 17th (national holiday) — closed
??.??.???? L ? 07:00-18:00         → Last day of month 7am-6pm
??.04.???? ? 1-5 10:00-16:00       → April weekdays 10am-4pm
01.05.2023 ? ? 00:00-23:59         → May 1st 2023 — open all day
```

## Hierarchical Rule Groups

Rules are organized in **ordered groups**. A group contains an ordered list of **rules and/or sub-groups**. The parser evaluates entries top-to-bottom and returns the opening hours for the **first matching rule** (first-match-wins).

More specific rules should be placed first (holidays, special dates), with generic catch-all rules at the bottom.

Example structure:
```
Group "NAV Contact Center"
  ├── Rule "May 17 — closed"              (priority 1, most specific)
  ├── Group "Public Holidays"              (priority 2, sub-group with multiple rules)
  ├── Rule "Christmas Eve weekday 9-14"    (priority 3)
  └── Rule "Standard weekdays 7-21"        (priority 4, catch-all)
```

Groups can be **nested** — a group can contain other groups. The parser recursively evaluates sub-groups. Circular dependencies must be detected and rejected.

## Database Schema

4 tables with proper FK constraints and cascade deletes:

```sql
CREATE TABLE rule (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200) NOT NULL UNIQUE,
    rule        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);

CREATE TABLE rule_group (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(200) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);

-- Join table: ordered list of rules and sub-groups within a group
CREATE TABLE rule_group_entry (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id      UUID NOT NULL REFERENCES rule_group(id) ON DELETE CASCADE,
    entry_type    VARCHAR(10) NOT NULL CHECK (entry_type IN ('RULE', 'GROUP')),
    rule_id       UUID REFERENCES rule(id) ON DELETE CASCADE,
    sub_group_id  UUID REFERENCES rule_group(id) ON DELETE CASCADE,
    sort_order    INT NOT NULL,
    CONSTRAINT exactly_one_ref CHECK (
        (entry_type = 'RULE' AND rule_id IS NOT NULL AND sub_group_id IS NULL) OR
        (entry_type = 'GROUP' AND rule_id IS NULL AND sub_group_id IS NOT NULL)
    ),
    CONSTRAINT unique_entry UNIQUE (group_id, rule_id, sub_group_id)
);

CREATE INDEX idx_rge_group ON rule_group_entry(group_id, sort_order);

-- Generic resource-to-group assignment (resource_id is an opaque UUID from the consumer)
CREATE TABLE resource_assignment (
    resource_id UUID PRIMARY KEY,
    group_id    UUID NOT NULL REFERENCES rule_group(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ
);
```

## REST API

Base path: `/api/v1`

### Rules (CRUD)
```
POST   /rules          — Create rule (validates DSL automatically)
GET    /rules          — List all rules
GET    /rules/:id      — Get one rule
PUT    /rules/:id      — Update rule (validates DSL)
DELETE /rules/:id      — Delete rule
```

### Groups (CRUD)
```
POST   /groups         — Create group (optionally with entries in order)
GET    /groups         — List all groups
GET    /groups/:id     — Get group with nested rules/sub-groups
PUT    /groups/:id     — Update group (replaces entire entry list — not partial)
DELETE /groups/:id     — Delete group (cascade deletes entries)
```

Group create/update body:
```json
{
  "name": "NAV Contact Center",
  "entries": [
    { "type": "RULE", "id": "uuid-of-rule" },
    { "type": "GROUP", "id": "uuid-of-subgroup" }
  ]
}
```

### Resource Assignment
```
PUT    /resources/:resourceId/group/:groupId  — Assign group to resource
DELETE /resources/:resourceId/group            — Remove assignment
GET    /resources/:resourceId/group            — Get assigned group
```

### Query (public — no auth)
```
GET /query/resource/:id?date=2024-03-15              — Opening hours for one date
GET /query/resource/:id/range?from=...&to=...        — Opening hours for date range
```

Query response format:
```json
{
  "resourceId": "uuid",
  "date": "2024-03-15",
  "isOpen": true,
  "openingTime": "07:00",
  "closingTime": "21:00",
  "matchedRule": {
    "name": "Standard weekdays",
    "rule": "??.??.???? ? 1-5 07:00-21:00"
  }
}
```

### Health
```
GET /internal/isAlive   — Returns {"status": "UP"}
GET /internal/isReady   — Returns {"status": "UP"}
```

## Code Quality Requirements

### Modern Java 21
- **Records** for all immutable data (entities, DTOs, internal types)
- **Sealed interface** for the `OpeningHoursRule` type hierarchy (`sealed interface OpeningHoursRule permits RuleEntity, RuleGroup`)
- **Pattern matching switch** for exhaustive type dispatch: `switch (rule) { case RuleEntity r -> ...; case RuleGroup g -> ...; }`
- **`List.copyOf()`** in record compact constructors for defensive copies
- **`var`** for local variables where the type is obvious
- **`.toList()`** instead of `Collectors.toList()`

### Immutability
- All entities must be records (not mutable classes with setters)
- Lists in records must use `List.copyOf()` to prevent mutation
- No fluent setter chains — use constructors

### Error Handling
- `IllegalStateException` with descriptive messages instead of bare `RuntimeException`
- Always include the cause exception: `throw new IllegalStateException("message", e)`
- Validate rule DSL syntax before saving (reject invalid rules at the API boundary)
- Detect and reject circular group dependencies before saving

### Code Organization
- Parser uses **iteration** (for-loop), not recursion via `subList()`
- Extract constants for magic strings (`CLOSED = "00:00-00:00"`, `RULE_NOT_APPLIES`)
- No code duplication — shared JSON conversion methods used across controllers
- Comments should explain **why**, not **what** (except for DSL parsing logic which benefits from what-comments)

## Validator

The validator must check all 4 parts of a rule string before accepting it:

1. **Date**: Valid DD.MM.YYYY with wildcards, valid calendar date (no Feb 30th)
2. **Day in month**: Valid ranges 1-31, L only at end, ascending order
3. **Weekday**: Valid range 1-7, ascending order
4. **Time**: Valid HH:MM format, closing not before opening (except 00:00-00:00 which means closed)

## Parser Logic

The parser evaluates a rule against a date by checking each part:

1. **Date match**: Does the date part match? (wildcards, specific date, month-only)
2. **Day-in-month match**: Is the day of month within the specified range? (resolve L to actual last day)
3. **Weekday match**: Is the ISO weekday within range?
4. If all 3 match → return the time part (e.g., "07:00-21:00")
5. If any part doesn't match → return sentinel value "rule_not_applies"

For groups: iterate entries in order, recursively evaluate sub-groups, return first non-sentinel result. If no rule matches, return "00:00-00:00" (closed).

## Tests

Write comprehensive tests for:

1. **Parser** — single rules, nested groups (3+ levels deep), edge cases (last day of month, Christmas on Sunday vs weekday, month-only wildcards)
2. **Validator** — valid rules, invalid rules (bad day, bad month, bad weekday, missing parts, closing before opening)
3. **Display data** — verify that matched rule name is correctly returned

Use the following test scenarios for nested groups:

```
Rules:
  rule1:  "17.05.???? ? ? 00:00-00:00"       (May 17 — closed)
  rule2:  "??.??.???? L ? 07:00-18:00"        (Last day of month)
  rule3:  "??.??.???? 1-5,25-30 ? 07:00-21:00" (Days 1-5, 25-30)
  rule4:  "??.04.???? ? 1-5 10:00-16:00"      (April weekdays)
  rule5:  "24.12.2023 ? 1-5 09:00-14:00"      (Christmas Eve 2023)
  rule6:  "24.12.???? ? 1-5 09:00-15:00"      (Christmas Eve weekday)
  rule7:  "??.??.???? ? 1-5 07:30-17:00"      (Standard weekdays)
  rule8:  "??.??.???? 12-15 ? 08:00-16:30"    (Days 12-15)
  rule9:  "??.??.???? 6 1-2 12:00-18:30"      (6th of month, Mon-Tue)
  rule10: "01.05.2023 ? ? 00:00-23:59"         (May 1st 2023)

Groups:
  group4 = [rule4, rule5, rule8]
  group3 = [rule10, rule2, rule3]
  group2 = [group3, rule9, group4]
  group1 = [rule1, group2, rule6, rule7]

Expected results for group1:
  2023-12-24 (Sunday)     → "00:00-00:00"  (no weekday rule matches)
  2024-12-24 (Tuesday)    → "09:00-15:00"  (rule6)
  2023-05-17 (Wednesday)  → "00:00-00:00"  (rule1)
  2023-11-09 (Thursday)   → "07:30-17:00"  (rule7)
  2023-04-24 (Monday)     → "10:00-16:00"  (rule4 via group4 via group2)
  2023-10-12 (Thursday)   → "08:00-16:30"  (rule8)
  2023-06-06 (Tuesday)    → "12:00-18:30"  (rule9)
  2023-05-25 (Thursday)   → "07:00-21:00"  (rule3)
  2023-07-22 (Saturday)   → "00:00-00:00"  (no match)
  2023-09-30 (Saturday)   → "07:00-18:00"  (rule2, last day of month)
  2023-05-01 (Monday)     → "00:00-23:59"  (rule10)
```

## Infrastructure

### Dockerfile (multi-stage)
- Build: `maven:3-amazoncorretto-21`
- Runtime: `eclipse-temurin:21-jdk`
- Fat JAR via maven-shade-plugin

### DataSource
- HikariCP with retry logic (up to 30 attempts — the database may start slower than the app)
- Flyway migration runs automatically on startup
- Reads config from environment variables: `DB_JDBC_URL`, `DB_HOST`, `DB_PORT`, `DB_DATABASE`, `DB_USERNAME`, `DB_PASSWORD`

### DB Connection Per Request Pattern
Use a servlet `Filter` that:
1. Calls `dbContext.startConnection(dataSource)` — binds a connection to the current thread
2. Calls `dbContext.ensureTransaction()` — starts a transaction
3. Runs the rest of the filter chain (controller logic)
4. Calls `transaction.setComplete()` — marks for commit
5. Both auto-close: transaction commits (or rolls back if setComplete wasn't called), connection returns to pool

## What NOT to Build

- No tags/labels on rules (< 50 rules total, search isn't needed)
- No template system (create standard groups manually)
- No holiday calendar integration (existing system works fine without it)
- No iCal/webhook/Kafka (no consumers, polling is sufficient)
- No historical versioning (opening hours change rarely)
- No bulk operations (< 50 rules)
- No separate entry endpoints for groups (PUT the entire group instead)
- No frontend yet (that's a separate task)
