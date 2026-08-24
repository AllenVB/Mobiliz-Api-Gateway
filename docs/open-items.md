# Open Items — everything deliberately not done yet

Single source of truth for anything deferred, blocked, or knowingly limited. Nothing is
allowed to be "skipped and forgotten": if a decision was made to leave something out, it
is written down here with the reason and what would close it.

Status key: **BLOCKED** (needs information we do not have) · **PLANNED** (scheduled) ·
**LIMITATION** (deliberate design boundary, documented) · **DEBT** (was promised, not done)
· **CLOSED** (kept for the record).

Last reviewed: 2026-08-24, after the collection was renamed to match the vocabulary and every field name was
re-verified against it — 19 endpoints, no mismatches.

---

## A. BLOCKED — waiting on information from the team

| # | Item | Why it matters | What closes it |
|---|---|---|---|
| A2 | **Cloudflare / WAF allowlist for the bridge host** | Bot protection is **intermittent**: `sifremiunuttum` was challenged once and succeeded later. Intermittent is worse than a hard block — production would see sporadic 403s that look like random failures. Server-to-server calls are challenged more readily than mobile-app ones. The bridge already detects and reports this (`UPSTREAM_CHALLENGE`), but detecting it is not fixing it. | Ops confirming the bridge's egress IP is allowlisted |
| A4 | **The actual option-set code lists** | The endpoint that holds them is now mapped (`GET /authorized-service/combos`), and one link is confirmed: `telOperator: "100000002"` is the `par_gsmcompany` entry "Avea". But the response is ~0.92 MB and only its first entries were visible, so the lists for `rol`, `hizmetLokasyonu`, `yakitTuru`, `kargodurumu`, `kargofirmasi`, feedback reasons and the rest are still unknown. With them, `*Code` string fields become real enums. | A capture of the full `combolar` response, or the list names of interest |
| A5 | **GUID reference resolution** | `aracTipi`, `marka`, `model`, `sehir`, `ilce` are GUIDs pointing at lookup records. Callers get an opaque id. `parentvalue` / `regardingvalue` on the dropdown entries look like the dependency links that would resolve them, but both were null in the capture. | Confirmation of how those two fields work |
| A6 | **Legacy base URLs (test / production) and a test account** | Every capture so far hit **production**. Verification runs against WireMock only. | Base URLs plus a non-production account |
| A7 | **Project / package name confirmation** | `com.mobiliz.ysbridge`, artifact `ys-bridge`, chosen by us. Renaming is cheap now, expensive after review. | A yes, or the team's preferred name |
| A8 | **Deployment format** | Docker image, or a jar on an existing host? Affects config externalisation and health checks. | The team's answer |
| **A23** | **`/servistalepleri/0` never answers** | Captured: Cloudflare returns **524 "A timeout occurred"** after **125 seconds**. That code means Cloudflare did reach the origin and the origin never replied within its window. So the endpoint is not slow — it hangs. This is a backend fault, not a client one, and it is in production. The bridge already handles it (`504 UPSTREAM_TIMEOUT` after the configured read timeout rather than a 125-second wait), but handling is not fixing. | The backend team investigating why that query does not complete |
| A11 | **What the `0` in `/servistalepleri/0` selects, and what it returns** | Probably a status filter. Modelled as a path variable so nothing is hardcoded; it should become a named query parameter once known. No response was ever captured. | Its meaning plus one captured response |
| A12 | **`canbusYakitSeviyesi` vs `canbusFuelLevel`** | Vehicle status carries both; they look like one reading under two names. Both were null in the capture. Now exposed as `canbusFuelLevel_TR` and `canbusFuelLevel` — the vocabulary named the pair without resolving it, see A30. | Which is which |
| A13 | **Timezone of the naive timestamps** | Values arrive as `2026-08-20T08:12:57` with no offset. The bridge does not invent one. | The zone these are recorded in |
| A15 | **Polarity of `sehirIciDisi`** | "şehir içi / şehir dışı" — the name does not say which value means which. | Confirmation |
| **A16** | **Request body of `sendstatuscommand`** | Narrowed on 2026-08-24 and now known to be **undiscoverable from outside**. With no body the endpoint answers 411 — that is IIS, before the application. With the `sendcarcommand` body it answers `success: true`. With `{}` it **also** answers `success: true`. The third result settles it: the endpoint accepts any JSON and reports success, so no probe can distinguish the right fields from ignored ones. **No request mapping is declared**, because writing one from the accepted body would publish a contract this project invented and the API does not enforce. It stays the only endpoint relaying its body untranslated. | The team or the backend source — not another experiment |
| **A17** | **Success payload of the endpoints that had never succeeded** | **MOSTLY CLOSED, 2026-08-24.** The cause was the collection, not the service: the `token` header was defined per folder and `Service`, `File` and the loose `update privacy policy` entry had been missed. Adding it made all five succeed. `servisdetay` (GET) returned a real payload for the first time and is now mapped as far as the vocabulary allows (A34); the four writes returned `result: null`, which is their settled shape. What remains is not this item. | Closed — the remaining unmapped endpoints are A28 and A16 |
| **A28** | **The work order list has agreed English names but no legacy ones** | The vocabulary names all 27 fields of `GET /service-requests/{n}` — `srvNumber`, `priority`, `customer`, `subject`, `orgStartDateTime`, `remainingSubTaskCount`, `serialNoList`, `completionDuration` and the rest. A mapping needs **both** sides, and this endpoint has never returned a successful response (524 after 125 s), so the Turkish names those 27 correspond to are unknown. Pairing them by guesswork would produce a mapping that looks complete while renaming the wrong fields, so **the resource is left with no response mapping**. Note also that the vocabulary documents the path as `/service-requests/**1**` where every capture used `/servistalepleri/**0**` — worth trying `1`, since the document's author evidently got a response from it. | One captured response, with any trailing segment that works |
| **A33** | **The work order line update vanished from the vocabulary** | An earlier draft documented it as `PUT /authorized-service/service-requests/0` — the same read-only route the collection uses, which answers 405. The completed vocabulary has **no entry for it at all**, making it the only write in the collection that is undocumented. Either it was removed once the route was found not to work, or it was dropped by accident. The resource is kept in the mapping, flagged, rather than deleted: the API does accept writes at `servistalepsatiri/{detayId}`, so the work is not wasted, but nothing about it is verified. | The team saying whether the operation exists and where |
| **A37** | **Which source names an endpoint: the vocabulary or the collection?** | The collection was renamed after the vocabulary was published, and two names differ in substance rather than spelling: `file-attachment` became `Upload File`, and `user` became `KVKK`. Both are applied, because the collection is the newer of the two. Two further differences are **not** applied, because they are transpositions the vocabulary spells correctly — `Sevice Requests` and `Technical Servis Unit`. The rule used is written down here so it is not re-invented: **a different word is a rename; a misspelt word is a slip.** Confirmation would let the rule be retired. | The team saying which document is authoritative when they disagree |
| **A36** | **The document and accessory bodies swapped places** | The collection sends `{aksesuarId, aksesuarAdi, detayid, durumu}` to `PUT /dokuman` and gets a document link — a real, captured, successful exchange. The revised vocabulary says `/dokuman` takes `{brandId, modelId, documentType}` and gives the accessory fields to a legacy endpoint this project had never seen, `/yetkiliservis/aksesuarlar/`. Both can be true if `/dokuman` ignores fields it does not recognise, as `sendstatuscommand` was shown to do — which would mean the captured call 'worked' while returning some default document rather than the one asked for. **No request mapping is declared for `/dokuman`**: the vocabulary's shape has no legacy names to map from, and the capture's may never have been read. The accessory read is also unmapped, because the vocabulary's example answers with an empty array. | The legacy names for `brandId`/`modelId`/`documentType`, and one captured call to `/aksesuarlar/` |
| **A29** | **`serviceLocation` is a number on one endpoint and a string on another** | `hizmetLokasyonu` arrives as `1` from `servistalepsatirlari` and as `"1"` from `servistalepsatiri`, for the same option set. Both are now exposed as `serviceLocation`, so the exposed contract has one name with two JSON types. The same is true of `engineVolume`, `fuelType`, `year`, `tireBrand` and `tireSize` between the work order line and the vehicle search. Normalising would need the engine to know the legacy type as well as the exposed one — a real feature, not a mapping tweak. | A decision on whether the bridge should normalise, or the team fixing it upstream |
| **A30** | **`canbusFuelLevel_TR` is almost certainly a placeholder** | It is the only name in the whole vocabulary carrying a language suffix, and it exists because the legacy payload has two fuel-level fields — `canbusYakitSeviyesi` and `canbusFuelLevel` — that nobody has yet told apart. Naming the pair `canbusFuelLevel_TR` and `canbusFuelLevel` records the ambiguity in the contract instead of resolving it. **Applied as written** rather than corrected, per the rule for this vocabulary. A12 stays open underneath: which reading is which is still unknown. | A real name, which needs A12 answered first |
| **A31** | **The immobilizer write's response: `null` or `{status: true}`?** | The capture shows `result: null`, which the bridge turns into 204 No Content. The vocabulary documents `result: {"status": true}`. The mapping now covers both — it is consulted only when a payload arrives — so neither source has been overruled, but the exposed contract cannot promise a body until this is settled. | One capture with a working token |
| **A25** | **`username` or `userName`?** | The earlier PDF spells it `userName`; the Notion page spells it `username`, twice — in login and in forgot-password. Applied as `username`, because Notion is the newer source and the user designated it as authoritative. One of the two documents is wrong and both are in circulation. | The team saying which spelling is the agreed one |
| **A26** | **`fullname` — deliberate or a typo?** | Every other name in the login response is camelCase (`roleDescription`, `androidVersion`, `salesAndroidVersion`); `fullname` alone is not. Applied exactly as written rather than silently corrected to `fullName`, because inventing a name the team did not write is worse than carrying their inconsistency. | A yes or a correction |
| **A24** | **`immobilizercheck` (PUT) rejects with a vendor-record fault** | The 2026-08-24 run returned `Vendor Bilgisinde Hata. Lütfen Teknik Destek Ekibi ile Görüşünüz.` — the legacy API telling the caller to contact support, i.e. broken data on the device record rather than anything wrong with the request. Catalogued as **422 `DEVICE_VENDOR_DATA_INVALID`** so it does not read as a retryable conflict. An earlier run of the same endpoint succeeded with `result: null`, so the endpoint itself works; this is one device's data. | The team confirming whether this is one device or a class of them |
| **A19** | **Does the line update replace or merge?** | `PUT /servistalepsatiri/{detayId}` is confirmed to accept writes, but **no write has ever executed**. The first attempt was rejected for an invalid token; the 2026-08-24 re-run, with a working token, was rejected for a different reason — the saved request has a literal `0` in the URL and the legacy API answered `Guid should contain 32 digits with 4 dashes`. So it is still unknown whether a `null` field clears the stored value or means "leave alone". This decides whether callers must send the whole record every time, and whether the bridge should strip nulls before forwarding. **Do not test this against a real record without capturing its current values first.** | One write against a real `detayId` whose contents are known, followed by a read |
| **A20** | **English names for the remaining response fields** | **In progress.** The team's vocabulary is being published in Notion and delivered section by section. **Auth is applied and closed** (login and forgot-password, requests and responses). The remaining domains — vehicle status, work order line detail, vehicle search, reference data, service, document, accessory, file — are still this project's proposals. Two rules while applying the rest: names are taken **exactly as written**, inconsistencies included (see A25, A26); and where a documented response shows `…` or `null` instead of a body, **nothing is invented** — that field set stays unmapped and visibly incomplete until a real one arrives. | The remaining sections, plus real bodies for the placeholders |
| **A21** | **Two request bodies the EN document omits** | The work order line update (28 fields) and `send-status-command` have no English names either; the document lists their endpoints without bodies. | The same follow-up document |

### Closed

| # | Item | Outcome |
|---|---|---|
| ~~A1~~ | How the token is sent | **CLOSED.** Plain request header `token: <GUID>`, lower-case, **no `Bearer` prefix**, inherited at folder level. Tokens do expire or get invalidated — a stale one produces `Token Bilgisi Hatalı`, distinct from the `Eksik` a missing one produces. The bridge relays the caller's token and does not manage its lifetime; the two messages are catalogued separately so a client can tell "log in" from "refresh". |
| ~~A9~~ | Correct URL for *update work order detail* | **CLOSED.** `PUT /yetkiliservis/servistalepsatiri/{detayId}` is the right target. Proven by elimination: the collection's URL answers `405` with `Allow: GET`, while this one answers with the application's own envelope. A 405 is decided at routing, before authentication — so receiving an envelope at all means routing and method were accepted. The mapping already pointed here. |
| ~~A14~~ | Success shape of `cihaztamamlandi` | **CLOSED.** The vocabulary documents it as `{"status": true}`. Only failures were ever captured — `Cihaz GPS Time Dolu Değil` and `Kontak Aktif Durumda`, both carrying `{"durumu": false}` — so the field was known and only its successful value was not. Mapped as `durumu: status`. |
| ~~A18~~ | Feedback reason codes | **CLOSED as to type.** The vocabulary documents `"feedbackReason": 6`, a number, which is why the free-text attempt was rejected with `Input string was not in a correct format.` The list of valid codes is still unknown and stays under A4. |
| ~~A10~~ | Correct URL and body for *complete work order* | **CLOSED.** It is the write side of the completion-status endpoint: `PUT /yetkiliservis/cihaztamamlandi/{detayId}`, no body. The collection had it pointed at `/demontajtamamlandi/`, which is complete-demontage's URL and wants a body — hence 411 on every attempt. Declared, and the bridge sends `{}` upstream so a caller can invoke it with nothing. |
| ~~A32~~ | Does feedback belong to a work order or a line? | **CLOSED — to the work order.** The revised vocabulary pairs `/feedback/{requsetId}` with `/geribildirim/{talepId}`, and `talepId` is the same word the line list uses for its parent. The collection's `detayId` was wrong. The exposed path is now `/authorized-service/feedback/{requestId}`; the vocabulary's transposed spelling is reported, not copied. |
| ~~A34~~ | Five unnamed fields of `servisdetay` | **CLOSED.** The revision names all sixteen, and renames four the earlier draft had called something else — `cableWiringUnderMaintenance` became `cableInstallationUnderMaintenance`, and `maintenanceAccessory1/2/3` became `accessory1/2/3UnderMaintenance`. The request body is now documented as the same sixteen fields, so the field it was missing is there too. |
| ~~A35~~ | Is logout a `GET` or a `PUT`? | **CLOSED — a PUT.** Captured 2026-08-24: `PUT /yetkiliservis/cikis` with `{}` answers `success: true, result: null`. The collection has been corrected to match, and the mapping already had it. |
| ~~A27~~ | Does the exposed contract keep the envelope? | **CLOSED — it stays.** The consuming system expects `{success, message, result}`, so the bridge wraps rather than strips. Everything inside it is still corrected: the HTTP status is truthful instead of a blanket `201`, failures carry a stable `code`, and `type` is dropped because it only ever repeated `success`. `204` is gone with it — a 204 may carry no body, so an empty success is `200` with `"result": null`. |
| ~~A22~~ | Which path style to adopt | **CLOSED — the vocabulary's.** Paths are the legacy ones translated into English (`/authorized-service/login`), with no version segment, on the team's instruction. The controller serves `/authorized-service/**`; a test asserts every declared resource sits under that prefix, so a path declared outside it fails the build rather than 404ing in production. |
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
| **B21** | **Declaring a path variable's format in the mapping YAML** | `Guid should contain 32 digits with 4 dashes` proved every `{detailId}` the legacy API takes is a GUID. The bridge already rejects a missing token and an unusable `page`/`size` before making the upstream call, on the principle that a caller's mistake should never become upstream load; a malformed identifier belongs in the same group. Deliberately **not** built yet: it needs a new key on the resource (`pathVariables: { detailId: GUID }`), strict validation of it at load time, and a decision on whether an unknown format is a startup failure. That is a feature, not a catalog entry, and the catalog covers the case correctly in the meantime (**400 `INVALID_IDENTIFIER_FORMAT`**). | A decision that pre-validation is worth the extra mapping surface |

### Closed

| # | Item | Outcome |
|---|---|---|
| ~~B2~~ | Date format conversion | **CLOSED.** Timestamps already arrive ISO-8601; no conversion needed. The missing offset became A13. |
| ~~B4~~ | Error catalog | **DONE.** `error-catalog.yaml`, four rules, exact matching only. An unmatched message becomes `400 UNRECOGNISED_LEGACY_ERROR` with the original text preserved and a warning logged, so the catalog grows from evidence. |
| ~~B5~~ | Coverage endpoint | **DONE.** `GET /internal/mapping/coverage`. |
| ~~B6~~ | Correlation id and secret handling | **DONE.** `X-Correlation-Id` is honoured, sanitised, bounded and echoed. Request and response **bodies are never logged**, and the coverage recorder deliberately records the *path* of an unmapped value, never the value — payloads carry tokens and personal data. |
| ~~B8~~ | Timeouts | **DONE.** Connect and read timeouts configurable; redirects never followed. |
| ~~B9~~ | README | **DONE.** Includes the "how to add an endpoint" workflow. |
| ~~B20~~ | Agreed request vocabulary applied | **DONE.** 25 exposed request names replaced from the team's document across six mapping files. Only the two test classes that assert names needed touching; every coverage and round-trip test passed unchanged, which is the rename-safety guarantee doing its job rather than being claimed. |
| ~~B10~~ | Per-endpoint legacy headers | **DONE.** Exposed as query parameters and moved into headers on the way out. |
| ~~B14a~~ | Authentication at the gateway | **DONE.** `authentication: TOKEN \| NONE` per resource, default `TOKEN`. Unauthenticated calls are rejected with 401 and `WWW-Authenticate` before the legacy API is troubled. The exposed contract uses `Authorization: Bearer`; the legacy `token` header is an implementation detail of the upstream. |
| ~~B15~~ | Collection paging | **DONE.** Every collection is an object with `items` + `page`, never a bare array, and always paged. See C12 for what this pagination is and is not. |
| ~~B16~~ | Inbound guards | **DONE.** 415 for a non-JSON body, 413 beyond a configured bound (read with the bound enforced, not after), 400 for unusable paging parameters — all validated before the upstream call. |
| ~~B18~~ | Every status the gateway can return is exercised | **DONE.** An audit compared the statuses reachable in code against those asserted in tests and found three untested paths — 504, 500 and 406 — plus one catalogued status, 422. All four now have tests. An untested error path is one nobody knows works. |
| ~~B19~~ | The config-driven claim made executable | **DONE.** `ConfigDrivenGuaranteesTest` proves both promises rather than asserting them in prose: an endpoint declared only in YAML is routable and translatable with no Java change, and the same legacy payload survives two entirely different sets of exposed names. Either promise breaking now fails a test. |
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
| **C16** | **A URL Tomcat rejects never reaches the envelope** | A path containing an encoded slash or another sequence the container refuses — `..%2F..%2Fadmin`, for instance — is answered by Tomcat with its own HTML error page, before Spring dispatches. The request is correctly blocked (verified: nothing reached the upstream), but the response is HTML rather than the `{success, message, code, result}` shape every other response carries. Wrapping it would need a servlet-level error page rather than a controller advice. Left as it is: it only affects requests that are malformed at the URL level, and the alternative is machinery for a case no legitimate caller reaches. |

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
| **E15** | **The EN document's flat names were adopted as given, including where they undo a nesting choice** | `komut`+`komutid` become `command`+`commandId` rather than `command.name`+`command.code`; `kargodurumu` becomes `cargoStatus` rather than `shipmentStatus.code`. The team owns the vocabulary. The cost is recorded rather than argued: when a label is later wanted alongside one of these codes, `cargoStatus: "1"` has to become an object, which breaks callers — the nested form would only have gained a field. Worth raising once; not worth overriding them on. |
| E6 | The exposed names are provisional | Only the right-hand side of each mapping line changes when the vocabulary arrives; the tests are name-independent by construction |
| E7 | An endpoint with an unconfirmed URL is left out, not guessed | Applies to *complete work order* (A10). A guessed URL looks like coverage while silently doing nothing |
| **E8** | **One catch-all controller, not one per endpoint** | There is no per-endpoint logic to hold. Everything that differs between endpoints is in the mapping documents, which is what makes adding an endpoint a configuration change |
| **E9** | **Query parameters, not headers, for values the legacy API reads from headers** | A search term in a header is the legacy API's awkwardness. Carrying it forward would defeat the purpose of an anti-corruption layer |
| **E10** | **Exact matching only in the error catalog** | Guessing which error a half-matching message means is how a bridge starts lying about what happened upstream |
| **E11** | **The exposed API uses `Authorization: Bearer`, not the legacy `token` header** | The same move as turning the legacy `search` header into a query parameter: an anti-corruption layer that forwards the upstream's awkwardness has not corrected anything |
| **E12** | **Opaque lookup values are objects, not scalars** | `brand.id` rather than `brandId`. When the reference-data endpoint resolves them, the label is added as `brand.name` — a new field, not a changed one. A flat string would have had to become an object, breaking every caller |
| **E14** | **A request the caller got wrong never reaches the legacy API** | Authentication, paging parameters, the `Accept` header, the body's media type and its size are all checked before the upstream call. Two of these were found the wrong way round during an audit — paging and `Accept` were both being validated only after the call had already been made and paid for. |
| **E13** | **Nothing is said twice in a response** | The legacy envelope carried `success: true` and `type: "SUCCESS"` — one fact, two names. The same discipline is applied to what this service emits: no `detail` that repeats `title`, no `legacyType` that repeats the status, no derivable page flags |

---

## F. Defects found in the legacy API — to report to the team

1. **Production account password is `123456`** and was visible in a screenshot. Rotate it.
2. **`GET` requests answer `201 Created`**, and so do application errors. HTTP status carries no information at all.
1b. **`GET /yetkiliservis/servistalepleri/0` does not respond at all.** Cloudflare times it out at 524 after 125 seconds. An endpoint in production that never completes.
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
13. **`update service detail` and `update maintenance` share one URL *and* method** (`PUT /servisdetay/{id}`), distinguished only by which fields the body carries. A body containing both sets has undefined behaviour. **RESOLVED — they never shared one.** The revised vocabulary gives the maintenance write its own legacy endpoint, `/yetkiliservis/teknikservisbirim/{detayId}`. The collection had it pointed at `/servisdetay/`, which accepted it without complaint — presumably the same 'accepts anything' behaviour seen elsewhere. So the defect was in the collection, and what it hid was that a write had been going to the wrong endpoint and reporting success.
14. **A fourth error-message style**: `notsupported.commands.iotplatform.error` — a dotted code, alongside Turkish prose, English .NET exceptions and ASP.NET bodies.
15. **File upload sends the file as base64 inside JSON** rather than multipart, inflating the payload by roughly a third and holding the whole file in memory on both sides.
16. **`detayid` is lower-case** in the `dokuman` request while every other endpoint uses `detayId`.
17. **`durum` means different things in different endpoints** — a status code on a work order line, a plain boolean on the immobilizer.
18. **The reference-data endpoint returns every list at once** (~0.92 MB) with no way to request one.
19. **A second raw .NET exception leaks to the caller**: `"Guid should contain 32 digits with 4 dashes (xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)."` — the framework's own parse error, complete with its placeholder text, returned as an API error message. Same class of defect as #3, and now demonstrably not a one-off.
20. **`cihaztamamlandi` refuses for at least two different reasons** — `Cihaz GPS Time Dolu Değil` and `Kontak Aktif Durumda` — and both are free Turkish prose with no code to branch on. A client that wants to tell "wait for a GPS fix" from "ask the driver to switch the ignition off" has to match on the sentence.
22. **`sendstatuscommand` accepts any JSON body and reports success.** `{}` is answered with `success: true`, exactly as a fully populated body is. Either the endpoint ignores its body entirely, or it silently discards fields it does not recognise. Both mean a caller cannot tell a correct request from a meaningless one, and a field sent under the wrong name would be dropped without complaint.
21. **`Vendor Bilgisinde Hata. Lütfen Teknik Destek Ekibi ile Görüşünüz.`** is an instruction to a human ("contact the technical support team") returned where a machine-readable error belongs. It also means device records exist in production whose vendor data is broken.

---

## G. Defects in the API documents themselves

The team's own `Yetkili Servis -TR` document was compared against what the API actually
does. It describes 27 endpoints; every one of them is mapped here. Six problems in the
document are worth reporting, because anyone following it hits them.

1. **`PUT /yetkiliservis/servistalepleri/0` is documented but cannot work.** Its 405 carries
   `Allow: GET`. The Hoppscotch entry was not a copy-paste slip — it came from here.
2. **`Content-Length = 3500` is documented as a header** for `sendstatuscommand`. It is not a
   header anyone sets: it is computed from the body. Following this line makes the request
   hang until it times out, because the server waits for 3500 bytes that never arrive. This
   looks like a workaround for the 411 that was written down instead of solved.
3. **`PUT /yetkiliservis/demontajtamamlandi/{detayId}` is listed twice**, once without a body.
   The bodyless entry is what the collection calls *complete work order*, and it answers 411.
4. **`geriBildirimNedeni` is shown as free text** (`"Geri bildirim nedeni örnek"`), but the API
   rejects that with `Input string was not in a correct format.` — it wants a numeric code.
5. **The document contains live credentials** (`sahadestek1` / `123456`).
6. **No response body is documented anywhere** — requests only. This is why roughly 110
   response fields still have no agreed English name (A20).

---

## Error catalog so far

Every row is backed by a captured response. Nothing here is anticipated.

| Legacy message | Exposed status | Code |
|---|---|---|
| `Token Bilgisi Eksik` | 401 | `MISSING_TOKEN` |
| `Token Bilgisi Hatalı` | 401 | `INVALID_TOKEN` |
| `Cihaz GPS Time Dolu Değil. Lütfen Kontrol Ediniz.` | 409 | `DEVICE_GPS_TIME_MISSING` |
| `Kontak Aktif Durumda. Lütfen Kapatınız.` | 409 | `IGNITION_ACTIVE` |
| `notsupported.commands.iotplatform.error` | 422 | `COMMAND_NOT_SUPPORTED` |
| `Vendor Bilgisinde Hata. Lütfen Teknik Destek Ekibi ile Görüşünüz.` | 422 | `DEVICE_VENDOR_DATA_INVALID` |
| `Input string was not in a correct format.` | 400 | `INVALID_FIELD_FORMAT` |
| `Guid should contain 32 digits with 4 dashes (…).` | 400 | `INVALID_IDENTIFIER_FORMAT` |
| *anything else* | 400 | `UNRECOGNISED_LEGACY_ERROR` (logged as a warning) |

Why the two device refusals are 409 and the vendor fault is 422: a 409 says the request is
correct and the world is not ready, so fixing the world and retrying will work — switch the
ignition off, wait for a GPS fix. `Vendor Bilgisinde Hata` cannot be resolved by anyone in
the request path; the legacy API says as much by telling the caller to phone support.
Retrying it forever would be the natural reading of a 409, so it is not one.
