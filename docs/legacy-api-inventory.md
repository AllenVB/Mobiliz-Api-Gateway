# Legacy API Inventory — Yetkili Servis

Source: Hoppscotch collection `Mobiliz Mobile Team → Collections → Yetkili Servis`
(environment `Yetkili Servis Production`).

> **Credential policy:** no real credentials, tokens, phone numbers, plates, addresses or
> personal data are recorded here. Every such value is masked. Samples are shape
> references, not data.

Legend — **[C]** confirmed from a captured request/response · **[C-partial]** partially
captured · **[?]** name known from the collection tree only.

---

## Cross-cutting behaviour (confirmed)

### 1. Response envelope
Every response arrives wrapped:

```json
{ "success": true, "type": "SUCCESS", "message": null, "result": <payload> }
```

`result` is **not always an object or an array**. Observed shapes:

| Shape | Endpoint |
|---|---|
| object | login, work order detail, vehicle status |
| array | work order details, vehicle search |
| `null` | forgot password, the `complete *` writes |
| **plain string** | `DVRSonDurum` returns a URL as the whole result |

### 2. A failure can still carry a payload
`GET /yetkiliservis/cihaztamamlandi/{detayId}`:

```json
{ "success": false, "type": "ERROR",
  "message": "Cihaz GPS Time Dolu Değil. Lütfen Kontrol Ediniz.",
  "result": { "durumu": false } }
```

So `success: false` does **not** imply `result: null`. The bridge must keep the payload.

### 3. HTTP status carries no information
`GET`, `PUT` and application errors alike return **`201 Created`**. Success is decided
only by the body's `success` field.

Exceptions, where the response is not the envelope at all:

| Status | Body | Cause |
|---|---|---|
| `403` | Cloudflare HTML challenge | bot protection, intermittent |
| `405` | `{"Message": "The requested resource does not support http method 'PUT'."}` | ASP.NET default, capital `Message`, no envelope |
| `411` | IIS HTML "Length Required" | PUT sent without a body |

### 4. Authentication — **RESOLVED**
The token is sent as a plain request header, inherited at folder level in the collection:

```
token: <the GUID returned by login>
```

Lower-case header name, raw GUID, **no `Bearer` prefix**. A new token is issued on every
login. Expiry and revocation behaviour are still unknown.

### 5. Cloudflare sits in front of the API
`POST /yetkiliservis/sifremiunuttum` once answered `403` with the Cloudflare interstitial,
then succeeded on a later attempt. The protection is therefore **intermittent**, which is
worse than a hard block: it will fail sporadically in production. Server-to-server calls
from the bridge are more likely to be challenged than calls from the mobile app.

### 6. Naming is inconsistent — five styles observed
| Style | Example | Where |
|---|---|---|
| camelCase Turkish | `adSoyad`, `sonServisFotolari` | most endpoints |
| lower-case run-on | `androidversion`, `sahadestekok`, `ysozelnot` | login, work order lines |
| UPPER abbreviation | `YSKVKK`, `DVR` | login, vehicle status |
| **PascalCase** | `SaseNo`, `MotorNo`, `AracTipi` | `SaseSorgulama` only |
| already English | `vendorCode`, `churn`, `immobilizer`, `lastGPSTime` | scattered |

A JSON key even carries a Turkish letter: `"diğer"` in the churn request body.

### 7. The same concept changes type between endpoints
`hizmetLokasyonu` is `1` (number) in `servistalepsatirlari` and `"1"` (string) in
`servistalepsatiri`. Similarly `yil: "804370052"` (string) vs `Sene: 804370050` (number)
for what looks like the same CRM option-set value.

### 8. Dates are ISO-8601 without a zone
`"2026-08-20T08:12:57"`. No `dd.MM.yyyy` conversion is needed. The missing offset is an
open question: the bridge should not invent one.

### 9. Some values are credentials
`DVRSonDurum` returns a streaming URL containing `Mobiliz-Token=...` in its query string.
This value must never reach the logs.

---

## Auth

### [C] POST `/yetkiliservis/giris` — login
Exposed as: `POST /api/v1/auth/login`

Request:
```json
{ "kullaniciAdi": "<username>", "sifre": "<password>",
  "osversiyon": "IOS", "deviceToken": null }
```
> `osversiyon` holds `"IOS"` — it is a **platform**, not a version.

Response `result`:
```json
{ "token": "<GUID>", "adSoyad": "<full name>", "rol": "1", "rolAciklama": "test",
  "qr": false,
  "androidversion": "2.0.3", "iosversion": "1.0(47)",
  "satisandroidversion": "1.0.3", "satisiosversion": "1.0 (29)",
  "YSKVKK": true, "YSETK": true, "SatisKVKK": true, "SatisETK": true }
```

### [C] POST `/yetkiliservis/sifremiunuttum` — forgot password
Exposed as: `POST /api/v1/auth/password-reset-requests`

Request: `{ "kullaniciAdi": "<username>" }`
Response: `result` is `null` on success. Maps to **204 No Content**.

---

## Work Order

### [C-partial] GET `/yetkiliservis/servistalepleri/0` — get work orders
Exposed as: `GET /api/v1/work-orders`
The trailing `0` is a positional argument of unknown meaning — probably a status filter.
No response captured; the request was still loading.
**Open: what does the `0` select, and what does the list look like?**

### [C] GET `/yetkiliservis/servistalepsatirlari/{workOrderId}` — get work order details
Exposed as: `GET /api/v1/work-orders/{workOrderId}/lines`

`result` is an **array**:
```json
[ { "detayId": "<GUID>", "srvNumarasi": "SRV-XXXXXX-XXXXX", "plaka": null,
    "serino": "0000002", "vendorCode": "MT151", "gsm": null,
    "sonServisFotolari": "", "durum": null, "montajDokumani": "",
    "hizmetLokasyonu": 1, "sahadestekok": true, "bakimonarimok": false,
    "ysozelnot": null, "churn": false } ]
```
`hizmetLokasyonu` is a **number** here.

### [C] GET `/yetkiliservis/servistalepsatiri/{detayId}` — get work order detail
Exposed as: `GET /api/v1/work-order-lines/{detailId}`

```json
{ "plaka": "<plate>", "serino": "0000001", "gsm": "<msisdn>",
  "canBusProfile": null, "vendorCode": "MT151", "stfFormNo": "ST-XXXXXXX",
  "telOperator": "100000002", "apnYok": false, "km": "87049", "testHatti": null,
  "aracTipi": "<GUID>", "yil": "804370052", "marka": "<GUID>", "model": "<GUID>",
  "motorHacmi": "4", "yakitTuru": "1", "hizmetLokasyonu": "1",
  "sehirIciDisi": null, "sehir": "<GUID>", "ilce": "<GUID>", "semt": null,
  "ysozelnot": null, "plakakontrolsuz": false, "yolbedelsiz": false,
  "churn": false, "lastikmarka": "5", "lastikebat": "134", "saseno": null,
  "otokocmu": false }
```
Notes:
- `aracTipi`, `marka`, `model`, `sehir`, `ilce` are **GUID references** to lookup records.
- `yil`, `motorHacmi`, `yakitTuru`, `hizmetLokasyonu`, `lastikmarka`, `lastikebat` are
  **numeric strings** that look like CRM option-set codes, not free values.
- `km` is a numeric string.

### [C-partial] PUT — update work order detail
The collection points at `PUT /yetkiliservis/servistalepleri/0`, which answers
**`405 Method Not Allowed`** with the ASP.NET body `{"Message": "..."}`.

The request body is **field-for-field identical** to the `servistalepsatiri/{detayId}`
response, so the intended target is almost certainly
`PUT /yetkiliservis/servistalepsatiri/{detayId}`.
**Open: confirm the correct URL with the team.**

Request body (same fields as the detail response, most set to `null`).

### [C] GET `/yetkiliservis/aracsondurum/{detayId}` — get vehicle status
Exposed as: `GET /api/v1/work-order-lines/{detailId}/vehicle-status`

```json
{ "plaka": "<plate>", "serino": "0000001", "gsm": "<msisdn>",
  "sunucukaydi": true, "sonAdresBilgisi": "<free-text address>",
  "ilkVeriTarihi": "2026-07-14T09:25:39", "sonVeriTarihi": "2026-08-20T08:12:57",
  "donanimBilgisi": null, "cihazYazilimVersiyonu": "60910",
  "mesafeSayaci": "5463", "anaBeslemeVoltaji": "13.24",
  "canbusYakitSeviyesi": null, "kontakAktivitesi": false,
  "canbusHiz": null, "canBusKM": null, "canbusok": true,
  "lastGPSTime": "2026-08-20T08:12:57",
  "canbusEngineRPM": null, "canbusFuelLevel": null, "canbusLastFuelUsed": null,
  "immobilizer": false, "canBusProfile": "", "emoveGsmNumber": "<msisdn>",
  "lastBulkGSensorTime": null, "lastMultiGpsTime": null,
  "lastAccelerationandGpsTime": null,
  "lastTachoDriver1Cardno": null, "lastTachoDriver1Name": null,
  "lastCalibrationStatusDescription": null, "modularSensorTemprature": null,
  "tachomodel": null, "tachoversion": null, "serialTemperature": null,
  "DVR": false }
```
Notes:
- `anaBeslemeVoltaji: "13.24"` — a **decimal as a string**, the first one seen.
- `modularSensorTemprature` is misspelled in the legacy API (`Temprature`).
- `canBusProfile: ""` while other absent fields are `null`.

### [C] PUT `/yetkiliservis/geribildirim/{detayId}` — update feedback
Exposed as: `PUT /api/v1/work-order-lines/{detailId}/feedback`

Request: `{ "geriBildirimNedeni": "<reason>", "geriBildirimAciklama": "<description>" }`
Captured response was a failure whose message is a **raw .NET exception**:
`"Input string was not in a correct format."` — suggesting `geriBildirimNedeni` is
expected to be a numeric code, not free text.
**Open: the reason code list.**

### [C] GET `/yetkiliservis/cihaztamamlandi/{detayId}` — get work order completion status
Exposed as: `GET /api/v1/work-order-lines/{detailId}/completion-status`

Captured response is the failure-with-payload case:
```json
{ "success": false, "type": "ERROR",
  "message": "Cihaz GPS Time Dolu Değil. Lütfen Kontrol Ediniz.",
  "result": { "durumu": false } }
```
**Open: the success shape.**

### [C-partial] PUT — complete work order
The collection points at `PUT /yetkiliservis/demontajtamamlandi/{detayId}` with no body,
which answers **`411 Length Required`** with an IIS HTML page. That is the same URL as
*complete demontage*, so the entry looks like a copy-paste error.
**Open: the correct URL and body.**

### [C] PUT `/yetkiliservis/demontajtamamlandi/{detayId}` — complete demontage
Exposed as: `PUT /api/v1/work-order-lines/{detailId}/demontage-completion`

Request: `{ "cihazdurumu": 1, "contactname": "<name>" }`
Response: `result` is `null`. `cihazdurumu` is a **number** here.

### [C] PUT `/yetkiliservis/churntamamlandi/{detayId}` — complete churn
Exposed as: `PUT /api/v1/work-order-lines/{detailId}/churn-completion`

```json
{ "teslimdurumu": "1", "contactname": "<name>",
  "rakipurun": "<competitor product>", "diğer": "<other>" }
```
Response: `result` is `null`.
Notes: `teslimdurumu` is a numeric **string** here while `cihazdurumu` is a number in
demontage — the same style of code, two different types. The key `"diğer"` contains a
Turkish letter.

### [C] GET `/yetkiliservis/DVRSonDurum/{detayId}` — get dvr status
Exposed as: `GET /api/v1/work-order-lines/{detailId}/dvr-status`

Request header: `channel: 0` (in addition to the inherited `token`).
Response `result` is a **plain string**, a streaming URL:
`https://<telematics-host>/su6/camera/hls/<id>/<channel>.m3u8?Mobiliz-Token=<secret>&companyId=<id>&serverName=<name>`

> The URL embeds a credential. It must be returned to the caller but never logged.

### [C] GET `/yetkiliservis/SaseSorgulama` — search vehicle by serial number
Exposed as: `GET /api/v1/vehicles`

Request header: `search: <serial>` — the search term travels in a **header**, not a query
parameter.

`result` is an **array**, and this is the only endpoint using PascalCase:
```json
[ { "id": "<GUID>", "SaseNo": "<VIN>", "MotorNo": null,
    "AracTipi": "<GUID>", "Marka": "<GUID>", "Model": "<GUID>",
    "MotorHacmi": 0, "YakitTuru": 0, "Sene": 804370050,
    "LastikMarka": 0, "LastikEbat": 0 } ]
```
`Sene`, `MotorHacmi`, `YakitTuru`, `LastikMarka`, `LastikEbat` are **numbers** here, while
their counterparts in the work order detail (`yil`, `motorHacmi`, ...) are strings.

---

## Not yet captured

### Car Command
`GET get immobilizer status` · `PUT update immobilizer status` ·
`PUT send car command` · `PUT send status command`

### Dropdown
`GET get dropdown list` — likely the source of the option-set code lists
(`rol`, `hizmetLokasyonu`, `yakitTuru`, `marka`, `model`, feedback reasons).
Worth capturing early: it would resolve several open questions at once.

### Service
`GET /yetkiliservis/servisdetay/{detayId}` (only an error response captured) ·
`PUT update service detail` · `PUT update maintenance`

### Document · Accessory · File · `PUT update privacy policy`
Not expanded.

---

## Error catalog (grows as samples arrive)

| Legacy `message` | Exposed status | Code |
|---|---|---|
| `Token Bilgisi Eksik` | 401 | `MISSING_TOKEN` |
| `Cihaz GPS Time Dolu Değil. Lütfen Kontrol Ediniz.` | 409 | `DEVICE_GPS_TIME_MISSING` |
| `Input string was not in a correct format.` | 502 | *(raw .NET exception, not a user-facing message — report upstream)* |

---

## Open items

Tracked in `open-items.md`. The ones this inventory adds:

1. What the `0` in `/servistalepleri/0` selects
2. The correct URL for *update work order detail* (405 today) and *complete work order* (411 today)
3. Option-set code lists: `rol`, `hizmetLokasyonu`, `yakitTuru`, `motorHacmi`, `yil`/`Sene`,
   `lastikmarka`, `lastikebat`, `teslimdurumu`, `cihazdurumu`, feedback reasons
4. Whether the GUID references (`marka`, `model`, `sehir`, `ilce`, `aracTipi`) can be
   resolved to names, and by which endpoint
5. The timezone the naive timestamps are expressed in
6. Success shape of `cihaztamamlandi`
