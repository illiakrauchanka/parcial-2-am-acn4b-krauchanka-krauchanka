# MedTraveler

App de referencia para viajeros que quieren chequear si los medicamentos que
llevan consigo están prohibidos o restringidos en el país al que viajan. El
usuario acepta un descargo de responsabilidad (EULA), elige hasta 3 países
destino y ve por cada país una lista de sustancias problemáticas con su
contexto legal (descripción, extracto de la ley local y la pena prevista).
Opcionalmente se registra / inicia sesión para guardar sus propios medicamentos
y verlos resaltados en las listas.

> La información es de carácter referencial y se basa en datos públicos; no
> constituye asesoramiento legal ni médico. Para estar seguro, consultá a los
> organismos oficiales de cada país.

## Flujo

```
Welcome (EULA + elegir país) → Checklist por país → Detalle de medicamento
                                                         │
                                                         └→ guardarlo en "Mis medicamentos"
Drawer: Inicio · Buscar · Contacto · Cuenta          ▲
                          └→ Login / Registro (email o Google) / Profile
   Contacto abre el cliente de correo (mailto:); si el dispositivo
   no tiene uno, muestra un Toast con la dirección de soporte.
```

## Pantallas

1. **WelcomeActivity** (launcher) — EULA + checkbox, selector de país tipo
   Spinner con bandera y nombre, hasta 3 países. Botón `CHECK my meds`
   habilitado sólo si el EULA está aceptado y hay ≥1 país elegido.
2. **MedicineListActivity** — secciones apiladas por país (bandera + nombre +
   `vN · fecha`). Cada tarjeta muestra nombre, badge de estado coloreado y
   descripción. Tap → detalle. Drawer + toolbar con `Update DB` y barra de progreso superior.
   Resalta
   "★ mío" los medicamentos guardados por el usuario logueado.
3. **MedicineDetailActivity** — cabecera, badge de estado (tap = Toast
   explicativo), descripción, datos clave, extracto de ley expandible, pena,
   botón "Abrir fuente oficial" (abre navegador; Toast si no hay navegador),
   botón "Tomo este medicamento" (guarda en Firestore `users/{uid}/myMeds`).
   Imagen del medicamento por URL vía **Glide**.
4. **SearchActivity** — busca en todo el caché por nombre o sustancia activa,
   usa `SearchFilter`. Estado vacío ("Sin resultados") cuando no hay matches.
5. **AuthActivity** — registro / login con email + contraseña o **Google
   Sign-In**. Validación de inputs (guard contra crash con campos vacíos).
6. **ProfileActivity** ("Mis medicamentos") — lista lo guardado por el
   usuario, con botón "Quitar" y "Cerrar sesión".

## Datos

Dos backends:

- **Catálogo de medicamentos** (público, read-only) → **Firebase Realtime
  Database** descargado **por país** vía REST (`GET .../meds/{code}.json` con
  OkHttp), cacheado localmente en **Room**. Seed inicial en
  `assets/meds_seed.json` para que la app funcione offline en el primer arranque.
  Solo estados `RESTRICTED` y `PENAL`. Países y banderas están embebidos en la
  app (`CountryCatalog`), no en la nube.
- **Lista personal del usuario** → **Firebase Auth** (email/contraseña o
  Google) + **Firestore** (`users/{uid}/myMeds/{medId}` = `{ name,
  countryCode, addedAt }`). Solo el uid dueño lee / escribe su subárbol (reglas
  de seguridad `firestore.rules`).

`version` y `updatedAt` por país vienen del nodo RTDB y se persisten
localmente (Room + `CatalogMeta` en SharedPreferences).

## Stack

- Java 11, min SDK 24, target SDK 36.
- AndroidX AppCompat + Material Components; ConstraintLayout / LinearLayout.
- Firebase Auth + Firestore + Realtime Database (REST con OkHttp, no el SDK).
- Room (caché del catálogo). Glide (imágenes por URL).
- Sin R8/minify en la primera publicación (más abajo).

## Cómo correr

1. Necesitás un proyecto Firebase con Auth (Email/Password + Google) y
   Realtime Database + Firestore (`firestore.rules`, `rtdb.rules`).
2. Bajar el `google-services.json` del proyecto y ponerlo en `app/`.
3. Cargar el catálogo en Realtime Database (ver
   `parcial2-planning/medcatalog/README-import.md`).
4. Abrir el proyecto en Android Studio, esperar el Gradle sync y correrlo en
   un emulador con SDK 24+.

```bash
./gradlew :app:installDebug
adb shell am start -n com.davinci.medtraveler/.ui.WelcomeActivity
```

## Build de release (Google Play)

Play Store para apps nuevas exige **Android App Bundle** (.aab), no .apk.

### 1) Keystore de release (una sola vez, guardalo aparte)

```bash
keytool -genkey -v -keystore medtraveler-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias medtraveler
```

No lo commitees — está en `.gitignore` (`*.jks`, `*.keystore`,
`keystore.properties`).

### 2) `keystore.properties` (en la raíz del repo, también gitignored)

```
storeFile=/ruta/absoluta/medtraveler-release.jks
storePassword=...
keyAlias=medtraveler
keyPassword=...
```

`app/build.gradle` lee este archivo si existe y firma el variant `release`
con ese keystore; si no existe, el `release` cae al signing debug (pracbuild
local sinомерing). Para entregar a Play el archivo **debe** estar presente.

### 3) Registrar SHA-1 / SHA-256 en Firebase

```bash
keytool -list -v -keystore medtraveler-release.jks -alias medtraveler
```

Firebase Console → `medtraveler-f49cc` → Settings → SHA certificate
fingerprints → pegar SHA-1 y SHA-256 de release. Bajar de nuevo el
`google-services.json` y sobreescribir `app/google-services.json`. Sin esto,
el **Google Sign-in no funciona en production**.

### 4) Generar el .aab

```bash
./gradlew :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab
```

Subir ese archivo a Play Console.

### Minify / R8

Por ahora `minifyEnabled false` en `buildTypes.release` para minimizar riesgo
de-crash en la primer pasada de review. `app/proguard-rules.pro` ya tiene
keep-rules para `model/`, `data/local/` (Room), Glide y Firebase por si
se habilita más adelante. Activarlo implica re-probar todo el flujo en la
release-signed build antes de subir.

## Estructura

```
app/src/main/java/com/davinci/medtraveler/
├── data/
│  ├── Catalog.java            # RTDB base URL
│  ├── CatalogJson.java        # parsea seed flat + nodo país RTDB
│  ├── CatalogMeta.java        # version/updatedAt per country + lastUpdatedGlobal
│  ├── CatalogRepo.java        # Room DAO wrapper + seed desde assets
│  ├── CatalogUpdater.java     # OkHttp REST → Room por país
│  ├── AuthManager.java        # email/password + Google + currentUid
│  ├── UserMedsRepo.java       # Firestore users/{uid}/myMeds CRUD
│  └── local/                  # Room: AppDatabase, MedDao @Transaction, MedEntity
├── model/
│  ├── CountryCatalog.java     # países embebidos (code, name, flagRes)
│  ├── Medicine.java
│  └── Status.java             # ALLOWED / RESTRICTED / PENAL
├── ui/
│  ├── BaseActivity.java        # drawer + toolbar + top ProgressBar + DB status
│  ├── WelcomeActivity.java
│  ├── MedicineListActivity.java
│  ├── MedicineDetailActivity.java
│  ├── SearchActivity.java
│  ├── AuthActivity.java
│  ├── ProfileActivity.java
│  ├── CatalogNotifier.java     # NotificationChannel para "Update DB"
│  ├── CountrySpinnerAdapter.java
│  └── (helpers de filas)
└── util/SearchFilter.java
```

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

Cubre `SearchFilter`, `CatalogJson` (seed flat + nodo país), `CatalogMeta`,
`Status.fromString`, selección de países.