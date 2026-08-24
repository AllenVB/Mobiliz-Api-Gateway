# YS Bridge

An API gateway in front of the legacy **Yetkili Servis** API.

The legacy API works, but its contract is hard to consume: field names are Turkish and
inconsistent, its paths are Turkish, and its HTTP status carries no information — reads
and errors alike answer `201 Created`. This service sits in front of it and presents the
same operations in English, with statuses that mean what they say.

The response envelope — `{success, message, result}` — **stays**, because the consuming
system expects it. This bridge corrects what is inside it rather than replacing it.

Its defining property: **the translation lives in configuration, not in code.** Adding an
endpoint or renaming a field is an edit to a YAML document. There is no per-endpoint Java
class to write, and there is no Java class that knows a single Turkish field name.

---

## Running it

```bash
YS_LEGACY_BASE_URL=https://<legacy-host> ./gradlew bootRun
```

`YS_LEGACY_BASE_URL` has no default on purpose: a bridge with no upstream is useless, so
the service refuses to start rather than accepting traffic it cannot serve.

| Endpoint | Purpose |
|---|---|
| `/authorized-service/**` | the exposed API |
| `/actuator/health` | liveness |
| `/internal/mapping/coverage` | fields the mappings did not cover — see below |

Build and test:

```bash
./gradlew build
```

That compiles, runs the formatter check, runs every test, and produces a coverage report
at `build/reports/jacoco/test/html/index.html`.

---

## Using the API

Every response, success or failure, carries the envelope:

```json
{ "success": true, "message": null, "result": { ... } }
```

`type` is not part of it. It only ever held `"SUCCESS"` or `"ERROR"` — the same fact
`success` already carries as a boolean — so it is dropped rather than said twice. If it
ever holds something else, that is logged and surfaced as `legacyType` rather than lost.

**The HTTP status is truthful**, which is the one thing the legacy API never does: `200`,
`401`, `409`, `422` and so on, instead of `201` for everything including failures. The
status and the envelope agree; a caller may read either.

There is no `204`. A 204 may carry no body, and the envelope is unconditional, so an
operation with nothing to return answers `200` with `"result": null`.

Authenticate with the token `POST /authorized-service/login` returns:

```
Authorization: Bearer <token>
```

The legacy API reads that token from a bare `token` header instead. Passing its
awkwardness on to callers would defeat the point of this layer, so the exposed contract
uses the standard scheme and the gateway translates. Only the two auth endpoints are
public; everything else answers `401` with `WWW-Authenticate: Bearer` when the header is
absent — rejected here rather than forwarded, so a caller mistake never becomes upstream
load.

### Collections

A collection is an object inside `result`, never a bare array, and always paged:

```
GET /authorized-service/chassis-inquiry?serialNumber=211&page=0&size=50
```

```json
{
  "success": true,
  "message": null,
  "result": {
    "items": [ ... ],
    "page": { "number": 0, "size": 50, "totalElements": 27, "totalPages": 1 }
  }
}
```

> **What this pagination is and is not.** The legacy API does not paginate — it answers
> with whole collections, one of which is roughly a megabyte. The gateway therefore pages
> on its own side: it bounds what the caller has to receive and parse, not what the legacy
> API has to produce. When the legacy API gains real pagination, the exposed contract does
> not have to change.

---

## Design rules for the exposed contract

These are the rules that decide what a response looks like. They exist so the contract can
grow without breaking anyone.

**A collection is never a bare array.** An array has room for nothing but its elements:
page counts, warnings, deprecation notices could never be added later. One level of
nesting, once, buys the ability to add fields forever.

**Nothing is said twice.** The legacy envelope carried both `success: true` and
`type: "SUCCESS"` — the same fact under two names — so `type` is dropped. The same
discipline applies elsewhere: the page metadata leaves out `first`, `last` and `hasNext`,
each of which is one comparison away from `number` and `totalPages`. `totalPages` stays,
because a ceiling division with a zero-element edge case is worth doing once here instead
of wrongly in every client.

**The team's vocabulary wins over this project's preferences.** This bridge had exposed
opaque lookup codes as objects — `brand.id`, `fuelType.code`, `role.code` +
`role.description` — so that resolving one later would add a field rather than change one,
which is the difference between a safe change and a breaking one. The agreed vocabulary is
flat throughout, and the agreed names are what ships. The cost is recorded once in E15 of
`docs/open-items.md` and is not re-argued per field: when a label is later wanted next to
`fuelType: "1"`, that scalar has to become an object and every caller breaks. Worth
raising once; not worth overriding the team on.

---

## Errors

A failure carries the same envelope as a success, plus a stable `code` to branch on —
never a message to parse:

```json
{ "success": false,
  "message": "The vehicle's ignition is on and must be switched off first",
  "code": "IGNITION_ACTIVE",
  "legacyMessage": "Kontak Aktif Durumda. Lütfen Kapatınız.",
  "result": null }
```

`message` is the English translation; the untranslated original travels alongside as
`legacyMessage` so support can still trace it against the legacy service's own logs.

| Situation | Status | `code` |
|---|---|---|
| No route for this path | 404 | `NO_SUCH_ENDPOINT` |
| Path exists under another method | 405 | `METHOD_NOT_ALLOWED` (+ `Allow` header) |
| No bearer token on a protected endpoint | 401 | `MISSING_TOKEN` (+ `WWW-Authenticate`) |
| Field not in the endpoint's contract | 400 | `UNRECOGNISED_FIELD` |
| Required query parameter absent | 400 | `MISSING_QUERY_PARAMETER` |
| `page` / `size` unusable | 400 | `INVALID_PAGE_REQUEST` |
| Body is not valid JSON | 400 | `MALFORMED_REQUEST_BODY` |
| Body declared as something other than JSON | 415 | `UNSUPPORTED_MEDIA_TYPE` |
| Body beyond the configured bound | 413 | `PAYLOAD_TOO_LARGE` |
| Legacy error in the catalog | as catalogued | e.g. `DEVICE_GPS_TIME_MISSING` |
| Legacy error not in the catalog | 400 | `UNRECOGNISED_LEGACY_ERROR` (logged) |
| Upstream bot-protection challenge | 502 | `UPSTREAM_CHALLENGE` |
| Upstream redirect (never followed) | 502 | `UPSTREAM_REDIRECT` |
| Upstream body is not the envelope | 502 | `UPSTREAM_NOT_AN_ENVELOPE`, `UPSTREAM_NOT_JSON`, … |
| Upstream unreachable | 502 | `UPSTREAM_UNREACHABLE` |
| Upstream timed out | 504 | `UPSTREAM_TIMEOUT` |
| Anything unforeseen | 500 | `INTERNAL_ERROR` (stack trace logged, never returned) |

A legacy failure that carries data keeps it: `cihaztamamlandi` answers `success: false`
alongside `{"durumu": false}`, which arrives translated in `result` — the envelope's own
slot for it — rather than being dropped.

Legacy error messages are matched **exactly**, never fuzzily. Guessing which error a
half-matching message means is how a gateway starts lying about what happened upstream.
An unmatched message is passed through in `legacyMessage` and logged so the catalog can
grow from evidence.

---

## Adding or changing an endpoint

Everything below happens in `src/main/resources/mapping/*.yaml`. No Java changes.

### 1. Declare the resource

```yaml
resources:
  work-order-line:
    method: GET
    exposedPath: /authorized-service/service-request-line/{detailId}
    legacyPath: /yetkiliservis/servistalepsatiri/{detailId}
    authentication: TOKEN     # the default; use NONE only for the auth endpoints
```

Both templates must declare the same path variables; the service refuses to start
otherwise.

### 2. Map the fields

**The key is always the legacy (Turkish) name. The value is the name this bridge
exposes.** One reading order, in both `request` and `response` blocks — this convention
removes a whole class of mistakes.

```yaml
    response:
      unmapped: PASSTHROUGH_AND_REPORT
      fields:
        adSoyad: fullname                       # short form: a plain rename
        androidversion: appVersions.android     # a dot groups it into a nested object
        marka: brand.id                         # nesting is supported, though the agreed
                                                # vocabulary does not currently use it
        durum:                                  # long form: extra rules
          exposed: status
          values:
            AKTIF: ACTIVE
            PASIF: INACTIVE
        serino:
          exposed: serialNumber
          type: INTEGER                         # legacy sends "1234567", expose a number
        montajDokumani:
          exposed: installationDocument
          emptyStringAsNull: true               # legacy sends "", expose null
        adresBilgisi:
          exposed: address
          nested:                               # for an object, or each array element
            fields:
              ilAdi: city
```

### 3. If the legacy endpoint wants a header

The legacy API reads some values from headers that belong in the URL. Expose them as
query parameters and let the bridge move them:

```yaml
    legacyHeaders:
      search:
        fromQuery: serialNumber
        required: true
```

### Field reference

| Key | Meaning |
|---|---|
| `exposed` | the exposed name; a dot creates nesting |
| `values` | value table, applied in both directions; must be reversible |
| `type` | `STRING` · `INTEGER` · `DECIMAL` · `BOOLEAN` — the **exposed** type |
| `emptyStringAsNull` | `""` becomes `null` outbound, and back to `""` inbound |
| `nested` | mapping for an object, or for each element of an array |

`unmapped` decides what happens to a field with no entry:

| Value | Behaviour | Use for |
|---|---|---|
| `PASSTHROUGH_AND_REPORT` | relayed unchanged, recorded in the coverage report | responses — a field the legacy API adds later must surface, not vanish |
| `FAIL` | request rejected with `400 UNRECOGNISED_FIELD` | requests — an unknown field means the caller has the wrong contract |
| `DROP` | silently omitted | rarely; prefer one of the above |

> **`type` caveat.** It declares the *exposed* type. Translating back renders the
> canonical string form (`1234567` → `"1234567"`), which matches the case it exists for:
> a legacy API that quotes its numbers. Declare it only for fields the legacy API sends
> as strings.

### 4. Prove it against a real payload

Add the captured payload to `CapturedPayloadsTest` and assert two things:

- **coverage** — translating it reports no gaps, so the mapping is complete
- **round trip** — translating out and back reproduces the original exactly, so the
  mapping is reversible

Both assertions are deliberately independent of the English names chosen, so they keep
working when the vocabulary changes. That is not a detail: it is what makes the rename
safe.

---

## How a request flows

```
caller → BridgeController
           ├─ RouteResolver          match method + path, or 404 / 405
           ├─ authentication         reject unauthenticated calls before they cost anything
           ├─ paging parameters      validated here, so a bad request never reaches upstream
           ├─ body                   read within a bound, rejected unless JSON
           ├─ mapped query params    moved into the headers the legacy API expects
           ├─ JsonTranslator         request body: English → Turkish
           ├─ LegacyApiClient        call the legacy API; never trusts its status
           ├─ EnvelopeParser         unwrap; detect HTML, challenges, redirects, junk
           ├─ ErrorCatalog           legacy message → status + stable code
           └─ JsonTranslator         response payload: Turkish → English, paged if a collection
```

Everything the translator could not fully handle — an unmapped field, a value outside its
table, a shape that changed — is recorded by `MappingCoverageRecorder` and served at
`/internal/mapping/coverage`. That endpoint is how the mapping documents get completed
from what production actually sees, rather than from guesswork.

---

## Layout

```
src/main/java/com/mobiliz/ysbridge/
├── config/         properties, startup loading, gateway wiring
├── mapping/
│   ├── model/      the compiled mapping: resources, fields, value tables
│   ├── loader/     YAML → model, with strict validation
│   └── engine/     JsonTranslator — the translation itself
├── legacy/         calling the legacy API and interpreting its envelope
├── gateway/        routing, paging, and the single catch-all controller
├── error/          error catalog and the failure envelope
└── observability/  correlation id, mapping coverage

src/main/resources/
├── mapping/*.yaml      one document per domain
└── error-catalog.yaml  legacy error messages → exposed errors
```

---

## Documentation

| File | Contents |
|---|---|
| [`docs/legacy-api-inventory.md`](docs/legacy-api-inventory.md) | every captured endpoint, its real payloads, and the legacy behaviour that shaped the design |
| [`docs/open-items.md`](docs/open-items.md) | everything deliberately not done, why, and what would close it — plus the defects found in the legacy API |

---

## Status

**Names and paths are settled.** Every field name and every path comes from the team's
English vocabulary, applied exactly as published — inconsistencies included, because
inventing a name they did not write would be worse than carrying one of theirs. The
discrepancies are raised in `docs/open-items.md` (A25, A26, A29, A30) rather than fixed in
place.

**Two endpoints are deliberately incomplete.** The work order list has agreed English names
but no captured legacy response, so the pairing between the two is unknown and the mapping
is left empty rather than guessed (A28). Five more endpoints have never returned a
successful response to anyone, including the vocabulary's own author (A17).

Applying the whole vocabulary changed **no Java source at all** — only YAML documents and
test fixtures. That is the rename-safety guarantee doing its job.
