# Open Items — everything deliberately not done yet

Single source of truth for anything deferred, blocked, or knowingly limited. Nothing is
allowed to be "skipped and forgotten": if a decision was made to leave something out, it
is written down here with the reason and what would close it.

Status key: **BLOCKED** (needs information we do not have) · **PLANNED** (scheduled) ·
**LIMITATION** (deliberate design boundary, documented) · **DEBT** (was promised, not done)
· **CLOSED** (kept for the record).

Last reviewed: 2026-08-20, after the contract-evolution, error-handling and paging pass.

---

## A. BLOCKED — waiting on information from the team

| # | Item | Why it matters | What closes it |
|---|---|---|---|
| A2 | **Cloudflare / WAF allowlist for the bridge host** | Bot protection is **intermittent**: `sifremiunuttum` was challenged once and succeeded later. Intermittent is worse than a hard block — production would see sporadic 403s that look like random failures. Server-to-server calls are challenged more readily than mobile-app ones. The bridge already detects and reports this (`UPSTREAM_CHALLENGE`), but detecting it is not fixing it. | Ops confirming the bridge's egress IP is allowlisted |
| A4 | **The actual option-set code lists** | The endpoint that holds them is now mapped (`GET /api/v1/reference-data`), and one link is confirmed: `telOperator: "100000002"` is the `par_gsmcompany` entry "Avea". But the response is ~0.92 MB and only its first entries were visible, so the lists for `rol`, `hizmetLokasyonu`, `yakitTuru`, `kargodurumu`, `kargofirmasi`, feedback reasons and the rest are still unknown. With them, `*Code` string fields become real enums. | A capture of the full `combolar` response, or the list names of interest |
| A5 | **GUID reference resolution** | `aracTipi`, `marka`, `model`, `sehir`, `ilce` are GUIDs pointing at lookup records. Callers get an opaque id. `parentvalue` / `regardingvalue` on the dropdown entries look like the dependency links that would resolve them, but both were null in the capture. | Confirmation of how those two fields work |
| A6 | **Legacy base URLs (test / production) and a test account** | Every capture so far hit **production**. Verification runs against WireMock only. | Base URLs plus a non-production account |
| A7 | **Project / package name confirmation** | `com.mobiliz.ysbridge`, artifact `ys-bridge`, chosen by us. Renaming is cheap now, expensive after review. | A yes, or the team's preferred name |
| A8 | **Deployment format** | Docker image, or a jar on an existing host? Affects config externalisation and health checks. | The team's answer |
| A10 | **Correct URL and body for *complete work order*** | The collection points at `PUT /yetkiliservis/demontajtamamlandi/{detayId}` with no body → **411**. That is the *complete demontage* URL, so it looks like a copy-paste error. **The endpoint is deliberately absent from the mapping** rather than guessed. | The real URL and body |
| A11 | **What the `0` in `/servistalepleri/0` selects, and what it returns** | Probably a status filter. Modelled as a path variable so nothing is hardcoded; it should become a named query parameter once known. No response was ever captured. | Its meaning plus one captured response |
| A12 | **`canbusYakitSeviyesi` vs `canbusFuelLevel`** | Vehicle status carries both; they look like one reading under two names. Both were null in the capture. Currently exposed as `canBus.fuelLevel` and `canBus.fuelLevelAlternate`. | Which is which |
| A13 | **Timezone of the naive timestamps** | Values arrive as `2026-08-20T08:12:57` with no offset. The bridge does not invent one. | The zone these are recorded in |
| A14 | **Success shape of `cihaztamamlandi`** | Only the failure case was captured (`{"durumu": false}` alongside `success: false`). | One successful capture |
| A15 | **Polarity of `sehirIciDisi`** | "şehir içi / şehir dışı" — the name does not say which value means which. | Confirmation |
| **A16** | **Request body of `sendstatuscommand`** | The URL is confirmed but the body was never captured (411). It is the **only** write endpoint that relays its body untranslated, which defeats the bridge's purpose for that one call. A test pins this down so it cannot be forgotten. | One captured request |
| **A17** | **Success payload of `servisdetay` (GET)** | Both attempts were made without the token header. No response mapping exists, so the payload is relayed untranslated. | One successful capture |
| **A19** | **Does the line update replace or merge?** | `PUT /servistalepsatiri/{detayId}` is confirmed to accept writes, but no write has actually executed — the one attempt was rejected for an invalid token. So it is still unknown whether a `null` field clears the stored value or means "leave alone". This decides whether callers must send the whole record every time, and whether the bridge should strip nulls before forwarding. **Do not test this against a real record without capturing its current values first.** | One write with a valid token against a record whose contents are known, followed by a read |
| **A18** | **Feedback reason codes** | `geriBildirimNedeni` sent as free text produced `"Input string was not in a correct format."`, so it is a numeric code. | The code list (likely part of A4) |

### Closed

| # | Item | Outcome |
|---|---|---|
| ~~A1~~ | How the token is sent | **CLOSED.** Plain request header `token: <GUID>`, lower-case, **no `Bearer` prefix**, inherited at folder level. Tokens do expire or get invalidated — a stale one produces `Token Bilgisi Hatalı`, distinct from the `Eksik` a missing one produces. The bridge relays the caller's token and does not manage its lifetime; the two messages are catalogued separately so a client can tell "log in" from "refresh". |
| ~~A9~~ | Correct URL for *update work order detail* | **CLOSED.** `PUT /yetkiliservis/servistalepsatiri/{detayId}` is the right target. Proven by elimination: the collection's URL answers `405` with `Allow: GET`, while this one answers with the application's own envelope. A 405 is decided at routing, before authentication — so receiving an envelope at all means routing and method were accepted. The mapping already pointed here. |
| ~~A3~~ | Successful response for `sifremiunuttum` | **CLOSED.** `result: null` → the bridge answers **204 No Content**. The earlier 403 was Cloudflare, not the endpoint. |

---

## B. PLANNED — scheduled for later

| # | Item | Deferred because | Scheduled for |
|---|---|---|---|
| B1 | **Term glossary** (`glossary.yaml`) | Worth doing: `plaka`, `serino`, `gsm`, `ysozelnot`, `churn`, `durum` and the option-set codes repeat across domains. Deliberately **not** done before the vocabulary is agreed — building a shared dictionary out of provisional names would mean rewriting it twice. One trap already found: `durum` is a status code on a work order line but a plain boolean on the immobilizer, so the glossary must never override an explicit per-resource mapping. | After the vocabulary arrives |
| B3b | **Query parameter → query parameter translation** | Query → *header* is implemented (that is what the legacy API actually needs). Plain query-parameter renaming is not, because no captured endpoint uses query parameters at all. | When one appears |
| B7 | **OpenAPI generation from the mapping files** | The mapping documents already hold everything a spec needs. Generating one from provisional names would publish a contract that is about to change. | After the vocabulary arrives |
| B8b | **Retry and circuit breaker on the legacy client** | Timeouts are configured and connection failures are classified (`UPSTREAM_UNREACHABLE` / `UPSTREAM_TIMEOUT`). Retry is **not** implemented on purpose: most endpoints are writes, and retrying a non-idempotent write is worse than failing it. Doing it properly means per-endpoint idempotency declarations. | When the traffic pattern is known |
| B12 | **Turning option-set codes into enums** | Depends on A4. | After A4 |
| **B13** | **Caching the reference-data response** | It is ~0.92 MB and holds every dropdown at once. Every caller fetching all of it to use one list is wasteful; a cache plus a `?list=` filter is the obvious improvement. | After A4, when the list names are known |
| **B14** | **Per-endpoint idempotency and rate limiting** | A gateway is the natural place for both, but neither has a requirement yet. | When there is one |

### Closed

| # | Item | Outcome |
|---|---|---|
| ~~B2~~ | Date format conversion | **CLOSED.** Timestamps already arrive ISO-8601; no conversion needed. The missing offset became A13. |
| ~~B4~~ | Error catalog | **DONE.** `error-catalog.yaml`, four rules, exact matching only. An unmatched message becomes `400 UNRECOGNISED_LEGACY_ERROR` with the original text preserved and a warning logged, so the catalog grows from evidence. |
| ~~B5~~ | Coverage endpoint | **DONE.** `GET /internal/mapping/coverage`. |
| ~~B6~~ | Correlation id and secret handling | **DONE.** `X-Correlation-Id` is honoured, sanitised, bounded and echoed. Request and response **bodies are never logged**, and the coverage recorder deliberately records the *path* of an unmapped value, never the value — payloads carry tokens and personal data. |
| ~~B8~~ | Timeouts | **DONE.** Connect and read timeouts configurable; redirects never followed. |
| ~~B9~~ | README | **DONE.** Includes the "how to add an endpoint" workflow. |
| ~~B10~~ | Per-endpoint legacy headers | **DONE.** Exposed as query parameters and moved into headers on the way out. |
| ~~B14a~~ | Authentication at the gateway | **DONE.** `authentication: TOKEN \| NONE` per resource, default `TOKEN`. Unauthenticated calls are rejected with 401 and `WWW-Authenticate` before the legacy API is troubled. The exposed contract uses `Authorization: Bearer`; the legacy `token` header is an implementation detail of the upstream. |
| ~~B15~~ | Collection paging | **DONE.** Every collection is an object with `items` + `page`, never a bare array, and always paged. See C12 for what this pagination is and is not. |
| ~~B16~~ | Inbound guards | **DONE.** 415 for a non-JSON body, 413 beyond a configured bound (read with the bound enforced, not after), 400 for unusable paging parameters — all validated before the upstream call. |
| ~~B17~~ | Catch-all error handling | **DONE.** Every failure is a problem document, including unforeseen ones (500, stack trace logged and never returned) and paths outside `/api/v1`. Upstream redirects are diagnosed separately from malformed bodies, because their fix is different. |

---

## C. LIMITATION — deliberate boundaries, documented in code

| # | Limitation | Reasoning |
|---|---|---|
| C1 | **`type` declares the *exposed* type; translating back renders the canonical string form.** | Every observed case is a legacy API that quotes its numbers. **Declare `type` only for fields the legacy API sends as strings.** |
| C2 | **`PASSTHROUGH_AND_REPORT` cannot pass through a field nested more than one level deep in the exposed payload**; it is reported and dropped. | Such a field lives inside a grouping object this bridge invented; there is no legacy name to write it under. |
| C3 | **Turkish decimal parsing (`1.234,56`) is not implemented.** | `"1.234"` is genuinely ambiguous, and the one decimal seen (`anaBeslemeVoltaji: "13.24"`) uses a dot. It stays a string. |
| C4 | **`FieldType.DECIMAL` produces a `DecimalNode`.** | Preserves precision but will not compare equal to a default-parsed payload. Noted so a round-trip test is not trusted blindly. |
| C5 | **A value outside its table is passed through and reported**, not rejected. | A new legacy enum value must not take the bridge down. |
| C6 | **A value that fails type coercion is passed through and reported.** | Same reasoning as C5. |
| C7 | **A scalar where a mapping expects an object is passed through and reported.** | `DVRSonDurum` returns a bare URL string as its whole result. Failing on an unexpected shape would turn an upstream change into an outage. |
| C8 | **`serino`, `km`, `mesafeSayaci` and similar numeric strings stay strings.** | Serial numbers can carry leading zeros; coercing without confirmation risks silent data loss. |
| **C9** | **`hizmetLokasyonu` is not normalised across endpoints.** | It arrives as `1` from one endpoint and `"1"` from another. Normalising needs the engine to know the legacy type as well as the exposed one; half-normalising would be worse than leaving it visible. Tracked as a follow-up if the team wants it. |
| **C10** | **Two exposed endpoints share one legacy endpoint** (`service-detail-update`, `service-maintenance-update`). | The legacy API distinguishes them only by which fields the body carries. The bridge gives them separate exposed paths and `unmapped: FAIL` on both, so a body mixing the two sets is rejected here rather than producing undefined behaviour upstream. |
| **C11** | **The bridge does not validate business rules.** | It translates and routes. Value-level validation belongs to the legacy API, which owns the rules. The only rejections here are contract-level: unknown fields, missing required parameters, malformed JSON. |
| **C12** | **Paging is done by the gateway, not the upstream.** | The legacy API answers with whole collections, so the gateway fetches everything and slices it. That bounds what the caller receives and parses; it does **not** reduce what the legacy API produces or transfers. Said plainly rather than implied, because a caller could otherwise assume `?size=10` is cheap upstream. When the legacy API gains real pagination, the exposed contract is already the right shape. |
| **C13** | **`page` and `size` are validated on every endpoint**, including ones that do not return a collection. | A parameter this API understands but cannot use is still a caller mistake worth naming. The alternative — ignoring it silently — hides a bug in the caller. |
| **C14** | **A body far beyond the size limit may reach the caller as a dropped connection rather than a 413.** | The gateway stops reading at the bound and answers; whether the caller sees that answer depends on how much of the request the container will swallow first (Tomcat's `maxSwallowSize`, 2 MB by default). This is how servers behave generally, not something this gateway does differently — recorded so the behaviour is not mistaken for a bug. |
| **C15** | **`hasNext`, `first` and `last` are absent from the page metadata.** | Each is one comparison away from `number` and `totalPages`. `totalPages` is kept because its ceiling division has a zero-element edge case worth getting right once. |

---

## D. DEBT

| # | Item | Status |
|---|---|---|
| ~~D1~~ | Spotless | **DONE.** `googleJavaFormat(...).aosp()`, wired into `check`. |
| ~~D3~~ | Coverage measurement | **DONE.** JaCoCo. Currently line **87.1%**, branch **73.1%** — both above the 80% / 70% targets. |
| D2 | **`-Werror` is not enabled** | `-Xlint:all` is on but warnings do not fail the build. Turn on once the code base settles; enabling it now would make an unrelated warning block work in progress. |

---

## E. DECIDED — recorded so they are not re-litigated

| # | Decision | Reasoning |
|---|---|---|
| E1 | No git repository for now | The user's choice; the home directory is already a git repository |
| E2 | Spring Boot 3.5.16, not 4.x | Boot 4 moves to Jackson 3; this service is Jackson-heavy and the ecosystem is still on Jackson 2 |
| E3 | Gradle 8.11.1, not 9.2.1 | Boot 3.5 supports Gradle 7.6.4+ and 8.4+; Gradle 9 is not on that list |
| E4 | The YAML key is always the legacy name | One reading order removes a whole class of mistakes |
| E5 | The legacy envelope is stripped | The legacy status carries no information; `success` decides |
| E6 | The exposed names are provisional | Only the right-hand side of each mapping line changes when the vocabulary arrives; the tests are name-independent by construction |
| E7 | An endpoint with an unconfirmed URL is left out, not guessed | Applies to *complete work order* (A10). A guessed URL looks like coverage while silently doing nothing |
| **E8** | **One catch-all controller, not one per endpoint** | There is no per-endpoint logic to hold. Everything that differs between endpoints is in the mapping documents, which is what makes adding an endpoint a configuration change |
| **E9** | **Query parameters, not headers, for values the legacy API reads from headers** | A search term in a header is the legacy API's awkwardness. Carrying it forward would defeat the purpose of an anti-corruption layer |
| **E10** | **Exact matching only in the error catalog** | Guessing which error a half-matching message means is how a bridge starts lying about what happened upstream |
| **E11** | **The exposed API uses `Authorization: Bearer`, not the legacy `token` header** | The same move as turning the legacy `search` header into a query parameter: an anti-corruption layer that forwards the upstream's awkwardness has not corrected anything |
| **E12** | **Opaque lookup values are objects, not scalars** | `brand.id` rather than `brandId`. When the reference-data endpoint resolves them, the label is added as `brand.name` — a new field, not a changed one. A flat string would have had to become an object, breaking every caller |
| **E13** | **Nothing is said twice in a response** | The legacy envelope carried `success: true` and `type: "SUCCESS"` — one fact, two names. The same discipline is applied to what this service emits: no `detail` that repeats `title`, no `legacyType` that repeats the status, no derivable page flags |

---

## F. Defects found in the legacy API — to report to the team

1. **Production account password is `123456`** and was visible in a screenshot. Rotate it.
2. **`GET` requests answer `201 Created`**, and so do application errors. HTTP status carries no information at all.
2b. **The collection's *update work order detail* request targets a read-only route.** `/yetkiliservis/servistalepleri/{x}` answers `405` with `Allow: GET`, so that saved request has never worked. Anyone using the collection to test that operation is testing nothing.
3. **`geribildirim` leaks a raw .NET exception** to the caller: `"Input string was not in a correct format."`
4. **`405` and `411` responses bypass the envelope**, returning ASP.NET / IIS defaults. Any client parsing the envelope breaks on them.
5. **The DVR response embeds a `Mobiliz-Token` credential** in a URL query string.
6. **A JSON key contains a Turkish letter** (`"diğer"`), which is fragile across clients.
7. **`modularSensorTemprature` is misspelled.**
8. **The same concept changes type between endpoints** (`hizmetLokasyonu`, `yil`/`Sene`).
9. **One endpoint uses PascalCase** (`SaseSorgulama`) while the rest do not.
10. **Search terms travel in a request header** (`search`) rather than a query parameter.
11. **`get accessories` and `get dropdown list` are the same endpoint** (`GET /combolar`), listed twice in the collection and returning the same ~0.92 MB payload.
12. **`get document` and `update accessory` are the same endpoint** (`PUT /dokuman`). The `get document` entry is simply missing its body, which is why it answers 411.
13. **`update service detail` and `update maintenance` share one URL *and* method** (`PUT /servisdetay/{id}`), distinguished only by which fields the body carries. A body containing both sets has undefined behaviour.
14. **A fourth error-message style**: `notsupported.commands.iotplatform.error` — a dotted code, alongside Turkish prose, English .NET exceptions and ASP.NET bodies.
15. **File upload sends the file as base64 inside JSON** rather than multipart, inflating the payload by roughly a third and holding the whole file in memory on both sides.
16. **`detayid` is lower-case** in the `dokuman` request while every other endpoint uses `detayId`.
17. **`durum` means different things in different endpoints** — a status code on a work order line, a plain boolean on the immobilizer.
18. **The reference-data endpoint returns every list at once** (~0.92 MB) with no way to request one.

---

## Error catalog so far

| Legacy message | Exposed status | Code |
|---|---|---|
| `Token Bilgisi Eksik` | 401 | `MISSING_TOKEN` |
| `Token Bilgisi Hatalı` | 401 | `INVALID_TOKEN` |
| `Cihaz GPS Time Dolu Değil. Lütfen Kontrol Ediniz.` | 409 | `DEVICE_GPS_TIME_MISSING` |
| `notsupported.commands.iotplatform.error` | 422 | `COMMAND_NOT_SUPPORTED` |
| `Input string was not in a correct format.` | 400 | `INVALID_FIELD_FORMAT` |
| *anything else* | 400 | `UNRECOGNISED_LEGACY_ERROR` (logged as a warning) |
