# zhusheng

![slop badge](.github/assets/slop_badge.webp)

[简体中文](README.md) | [繁體中文](README_zh_TW.md) | [English](README_en_US.md) | [Polski](README_pl_PL.md) | [Русский](README_ru_RU.md) | Svenska

**zhusheng** återställer TLS/HTTPS-förtroende i hela systemet på gamla
Android-enheter (**4.0 - 6.0**, API 14 - 23) genom att hämta de officiella
rotcertifikat som de stora TLS/SSL-leverantörerna publicerar och installera dem i
Androids certifikatarkiv.

Gamla enheter levereras med en föråldrad uppsättning betrodda rötter. När en
webbplats serveras av en CA som inte fanns (eller inte var betrodd) när enheten
byggdes - mest känt **Let's Encrypt** - misslyckas varje HTTPS-begäran med ett
certifikatfel och enheten ”når inte internet som den ska”. zhusheng åtgärdar det
genom att installera de saknade rötterna.

* Fungerar på **alla arkitekturer** (appen är ren Java, ingen nativ kod).
* Fungerar på **Android 4.0 till 6.0**.
* Använder bara **Androids egna gränssnittskomponenter** - ingen AndroidX, inga
  tredjepartsbibliotek.

## Hur det fungerar

1. **Hämta.** Välj de leverantörer du behöver (eller behåll den rekommenderade
   uppsättningen) och tryck på *Hämta markerade*. Certifikaten hämtas från de
   officiella CA-adresser som listas nedan.
2. **Installera.** Det finns två lägen:
   * **Systemarkivet (kräver root)** - certifikaten skrivs som PEM-filer till
     `/system/etc/security/cacerts/<subject_hash>.<n>`, precis som de
     förinstallerade rötterna, och `/system` monteras sedan om som skrivskyddat. En
     omstart tillämpar ändringen. Då litar *alla* appar på dem.
   * **Användararkivet (utan root)** - certifikaten installeras via Androids
     certifikatinstallation (`KeyChain.createInstallIntent`). I Android-versioner
     **före 7.0** är certifikat i användararkivet betrodda i hela systemet, så det
     här åtgärdar också HTTPS för alla appar utan root. Knappen frågar först hur
     installationens bekräftelser ska gå till och erbjuder de tre sätten
     (`UserInstallMode`; valet sparas och föreslås igen nästa gång):
     * **en fråga för hela uppsättningen** - hela uppsättningen erbjuds först till
       installationen i **en** intent: ett paket med autentiseringsuppgifter
       (credentials bundle) med en `CACERT_<n>` post per certifikat
       (`CertInstaller.userStoreBulkInstallIntent`). En installation som förstår
       det lägger till alla rötter i en enda omgång; vissa 4.x-versioner läser
       paketet och ignorerar det tyst, och appen faller då tillbaka på ett
       certifikat per dialog;
     * **en fråga per certifikat** - paketet provas inte alls, så installationen
       får frågan om ett certifikat i taget. Långsammare än det första sättet, men
       det beter sig likadant i alla installationer;
     * **svara på frågorna automatiskt** - samma körning som det första sättet, men
       frågorna besvaras av **tjänsten för automatisk bekräftelse**.

     Certifikat som enheten **redan litar på lämnas i fred**, och dubbletter mellan
     källorna tas bort (`TrustedRoots`), så en andra körning - eller en som
     fortsätter en avbruten installation - har inget kvar att bekräfta.

     **Tjänsten för automatisk bekräftelse** är valfri och kräver **Android 4.1
     eller senare**. En app får inte aktivera sin egen tillgänglighetstjänst, så det
     gör bara användaren i systemets tillgänglighetsinställningar - och därför
     förklarar appen vad tjänsten är och namnger posten att leta efter innan den
     öppnar den sidan. Den varnar också för att tjänsten arbetar genom att snabbt
     trycka sig igenom skärmar, så displayen kan blinka medan körningen pågår, och
     pekar ut de två sätt som bekräftar för hand för den som är känslig för
     blinkande ljus. Det gör menyposten *Bekräfta installationer automatiskt*, och
     det gör också valet av det automatiska sättet ovan när tjänsten inte är
     påslagen; i det fallet startar installationen av sig själv så snart användaren
     är tillbaka från inställningarna. Medan en körning som appen har startat pågår
     trycker tjänsten på *OK* i installationens dialoger och, vid en borttagning, på
     raderna i certifikatlistan; utanför en sådan körning gör den ingenting alls,
     och de två sätt som bekräftas för hand säger aldrig till den att starta. I
     Android 4.0 kan tillgänglighetsramverket inte klicka på en knapp alls
     (`performAction(ACTION_CLICK)` kom med
     `View.performAccessibilityAction` i 4.1), så en 4.0-enhet bekräftar frågorna
     för hand.

3. **Borttagning.** Båda arkiven kan tömmas igen, så en installation kan ångras utan en
   andra enhet och utan återställning till fabriksinställningar:
   * **Systemarkivet (root)** – tar bort bara de filer appen själv har lagt dit. Appen
     skriver upp namnet på varje kopierad fil (`system-installed.txt` i sin privata
     `files`-katalog) och jämför innehållet med certifikatet innan filen tas bort: ett
     rotcertifikat som följde med enheten och samma rotcertifikat i ett paket kan vara
     exakt samma byte, arkivet ensamt kan inte skilja dem åt, och ett medföljande
     rotcertifikat får aldrig raderas.
   * **Användararkivet** – arkivet har inget API för att ta bort ett certifikat, så
     **3b. Ta bort från användararkivet** frågar vilket av tre sätt som ska användas,
     på samma sätt som 2b frågar hur installationens frågor ska besvaras
     (`UserRemoveMode`, valet sparas också till nästa gång):
     * **Ta bort filerna med root** – filerna i `/data/misc/user/0/cacerts-added`
       (i äldre versioner `/data/misc/keychain/cacerts-added`) läses direkt. En fil
       tas bort bara när dess innehåll är ett av de inlästa certifikaten. Det här är
       det enda sättet som kräver root, och på en enhet utan root är det nedtonat och
       märkt som sådant, så det går inte att välja där.
     * **Ta bort för hand i inställningarna** – certifikatlistan i
       systeminställningarna öppnas och varje certifikat tas bort där för hand:
       tryck på dess rad, tryck *Ta bort*, bekräfta. Appen tar inte bort något, och
       det här sättet kräver ingen root. Rutan räknar upp stegen och erbjuder en
       knapp som öppnar den skärmen; skärmbilderna till den genomgången kommer
       senare.
     * **Ta bort automatiskt (tillgänglighetstjänsten)** – samma lista, men tjänsten
       för automatisk bekräftelse sköter den (se nedan).

   De sätt som verkligen tar bort något – 3a och root-sättet i 3b – frågar först om
   bekräftelse, och båda kräver root: användararkivet därför att det inte finns
   något API, inte på grund av hur det skrivs.

   Det tredje sättet lämnar borttagningen till **tjänsten för automatisk
   bekräftelse**: appen öppnar samma certifikatlista och
   lämnar namnen på de inlästa certifikaten till tjänsten, som trycker på raden för
   var och en, på *Ta bort* på detaljskärmen som öppnas och svarar på frågan som
   följer. Den rör bara rader vars namn matchar ett av dessa certifikat, och den
   stannar så snart appen är i förgrunden igen - listan tillhör användaren på nytt.
   Den kräver samma tjänst påslagen och går igenom inställningsskärmen i den
   version den körs på; i Android 5.0 och 6.0 är det ett tryck på raden, knappen
   *Ta bort* under certifikatdetaljerna och ett *Ja*. Är tjänsten inte påslagen
   förklarar appen den först och ger en knapp som öppnar den sidan, och frågar om
   borttagningen igen när användaren är tillbaka.

   Varje körning av ett skript med root - installationerna och borttagningarna i
   båda arkiven (2a, 3a och root-sättet i 3b) - slutar i en dialog som citerar
   skriptets utdata precis som de kom tillbaka, för det är den utdatan en
   felrapport ska innehålla, och under den förklarar de markörer appen känner igen
   på det språk appen är inställd på (`ScriptNotes`): `ZHUSHENG_COPIED=`,
   `ZHUSHENG_REMOVED=`, `ZHUSHENG_DEDUPED=`, `ZHUSHENG_DONE` samt raderna
   `no system-installed.txt`, `could not write`, `command timed out` och
   `exec failed`. Förklaringarna följer ordningen markörerna har i utdatan, en
   markör som skrivs ut två gånger förklaras en gång, och allt appen inte känner
   igen citeras ändå som det kom tillbaka. Tom utdata får i stället den enda
   förklaring som spelar roll då: skriptet skrev inte ut något, och det är precis
   hur en avvisad begäran om root ser ut. (På en enhet utan root nås dialogen
   aldrig - 2a och 3a säger att root saknas, och root-sättet i 3b är nedtonat.)

På **Android 7.0 (API 24) och senare** litar systemet inte längre på certifikat som
användaren installerat, för appar som riktar sig mot 7.0 eller senare, så läget
*användararkivet* har ingen verkan för de flesta appar där; läget *systemarkivet*
kan fortfarande fungera, men det kräver root, och versioner över 6.0 ligger utanför
vad zhusheng officiellt stöder (senare versioner av Android har ändrat
systemcertifikatarkivet ytterligare). Appen förklarar det en gång, vid **första
starten**: `LegacyNotice` anropas i slutet av `onCreate()` och flaggan
`legacy_notice_shown` sparas i `SharedPreferences`.

Filnamnen i systemarkivet skapas med OpenSSL-värdet `x509 -subject_hash_old` (MD5
över subjektet i DER-kodning), som appen återskapar i Java
(`HashUtils.subjectHashOld`). Överensstämmelsen med OpenSSL har verifierats byte
för byte.

## Officiella certifikatkällor

| Leverantör | URL |
| --- | --- |
| Mozilla CA Bundle (alla stora CA) | `https://curl.se/ca/cacert.pem` |
| Google Trust Services (paket) | `https://pki.goog/roots.pem` |
| Let's Encrypt ISRG Root X1 | `https://letsencrypt.org/certs/isrgrootx1.pem` |
| Let's Encrypt ISRG Root X2 | `https://letsencrypt.org/certs/isrg-root-x2.pem` |
| Google GTS Root R1-R4 | `https://pki.goog/repo/certs/gtsr1.pem` … `gtsr4.pem` |
| Amazon Root CA 1-4 | `https://www.amazontrust.com/repository/AmazonRootCA1.pem` … |
| DigiCert Global Root CA / G2 / G3 / Trusted G4 / EV | `https://cacerts.digicert.com/….crt.pem` |
| GlobalSign Root CA R1 / R3 / R6 | `https://secure.globalsign.com/cacert/root-r1.crt` … |
| Sectigo USERTrust / COMODO RSA / ECC | `http://crt.sectigo.com/….crt` |
| Entrust Root CA G2 | `https://web.entrust.com/root-certificates/entrust_g2_ca.cer` |
| Go Daddy Root CA G2 | `https://certs.godaddy.com/repository/gdroot-g2.crt` |
| Microsoft RSA / ECC Root CA 2017 | `https://www.microsoft.com/pkiops/certs/….crt` |

**Mozilla CA Bundle** är lösningen för allt: den innehåller rötterna för alla
större CA (bland andra Certum, SSL.com, IdenTrust, Buypass, D-TRUST, QuoVadis,
Actalis, HARICA, T-TeleSec, Verisign/Symantec), så en enda hämtning lagar de allra
flesta trasiga webbplatser.
### Säkerheten i hämtningen

Eftersom det är just enhetens förtroendearkiv som är trasigt kan en fullt
verifierad HTTPS-anslutning misslyckas mot de här adresserna (till exempel
`letsencrypt.org` serveras med ett Let's Encrypt-certifikat). En Android 4.0-enhet
kan inte ens genomföra den TLS-handskakning som dessa värdar kräver - och det är
just för sådana enheter den här appen finns - så hämtningen faller tillbaka till
vanlig HTTP:

1. först försöker den hämta över HTTPS med **normal verifiering**, och bara om det
   misslyckas
2. försöker den igen och accepterar vilket servercertifikat som helst,
3. försöker den igen på samma adress över **vanlig HTTP**, och till sist
4. tar den den **kopia över vanlig HTTP** som källan bär med sig - för de värdar
   som bara svarar över https (en fast ögonblicksbild av den officiella filen hos
   Internet Archive).

Det är inte något av detta som skapar förtroendet, utan kontrollen av innehållet
efteråt, rot för rot:

* varje certifikat måste vara en **självsignerad rot** (subject == issuer och
  signaturen verifieras med dess egen nyckel),
* en källa med ett enda certifikat jämförs med sitt **fastställda
  SHA-256-avtryck**, och
* ett paket jämförs med **avtrycken för varje rot det förväntas innehålla**
  (`KnownRoots`), så innehåll hämtat över vanlig HTTP kan inte smuggla in en egen
  rot.

En manipulerad hämtning kan därför inte i tysthet installera en förfalskad rot.

## Teman

Appen erbjuder tre valbara teman och två färgscheman, som väljs med knappen
**Tema** i den övre listen; valet sparas i `SharedPreferences`. **Material är
standard på Android 5.0 och senare** (det passar systemet omkring); Android 4.x
startar med Holo, och Smartisan-maskinvara startar med **enhetens eget tema**
(`LaoLuo`), så att appen öppnas i tillverkarens egen skepnad på den ROM den är gjord
för. När ett tema väl har valts medvetet vinner det valet alltid.

| Alternativ | API 14 - 20 (4.0 - 4.4W) | API 21+ (5.0 - 6.0) |
| --- | --- | --- |
| **Holo** | `Theme.Holo.Light.DarkActionBar` | `Theme.Holo.Light.DarkActionBar` |
| **Material** | inte tillgängligt (gråat) | `Theme.Material.Light.DarkActionBar` |
| **Enhetens standard** | `Theme.DeviceDefault.Light.DarkActionBar` | `Theme.DeviceDefault.Light.DarkActionBar` |

* **Holo** är det ursprungliga temat och finns i alla stödda versioner, men är
  standard bara på Android 4.x.
* **Material** använder ramverkets Material Design 1-komponenter och finns därför
  bara på Android 5.0+, där det också är **standard**. På Android 4.x är
  alternativet **gråat** i listan, med en förklaring under, i stället för att tyst
  falla tillbaka på Holo.
* **Enhetens standard** använder det tema tillverkaren levererar (TouchWiz, Sense,
  MIUI, ...) via `Theme.DeviceDefault`.

### Färgschema

Varje tema finns i ett **ljust** och ett **mörkt** schema:

* **Ljust** (standard) använder temats `DarkActionBar`-variant - ljust innehåll med
  en mörk åtgärdslist - alltså appens ursprungliga utseende.
* **Mörkt** använder den helt mörka varianten av samma tema (`Theme.Holo`,
  `Theme.Material`, `Theme.DeviceDefault`).

Färgerna på appens egna ytor (bakgrund, brödtext, sekundär text, listavdelare)
deklareras som temaattributen `zsBackground`, `zsTextPrimary`, `zsTextSecondary`
och `zsDivider` (se `res/values/attrs.xml`), så innehållet inte förblir vitt i det
mörka schemat. De sex bastemana sätter de ljusa värdena; det mörka schemat skriver
över dem med `ThemeOverlay.Zhusheng.Scheme.Dark` i `Theme.applyStyle()`.

### Färger

Temadialogen erbjuder också färgval. Den första rutan (standard) betyder **behåll
temats egen färg**, så utan val ser det ut precis som förut; Holos standardruta
visar den turkos som temat självt ritar med. Den valda rutan har en bock i mitten:
vit i det ljusa schemat, svart i det mörka, vars 200-nyanser är för bleka för en
vit bock. Det enda undantaget är den första, ”standard”, rutan: den har temats egen
färg, så bocken väljs efter ljushet. Versionsraden under underrubriken
(`device_info`) är appens enda element som alltid bär en accentfärg, så utan val
bär den temats egen färg och kan inte krocka med kryssrutorna bredvid.

| Tema | Färger | Var de gäller |
| --- | --- | --- |
| **Holo** | accent (4 Holo-färger) | allt temat ritar i sin egen turkos: kryssrutorna i listan, radioknapparna och bockarna i en dialog, förloppsindikatorn, länkar, dialogtiteln och avdelaren under den, dialogknapparna, den blå linjen längs den mörka åtgärdslistens nederkant och versionsraden under underrubriken (de delar som kräver färgning per vy kräver API 21+) |
| **Material** | primär (10 Material-färger) + accent (10) | primärfärgen styr åtgärdslisten och statusraden i det **ljusa** schemat (det mörka behåller alltid temats egen mörka list och statusrad), accenten styr kryssrutor, förloppsindikatorn, dialogknapparna, versionsraden under underrubriken med mera |
| **Enhetens standard** | inga | färgerna kommer från tillverkarens tema |
* **Material**s färger läggs på via temaöverlagringar
  (`android:colorPrimary` / `colorPrimaryDark` / `colorAccent` /
  `colorControlActivated`) som definieras i `res/values-v21/color_overlays.xml` och
  läggs på temat av `AppColors.applyTheme()` efter `setTheme()` och före
  `super.onCreate()`. Det är så Material självt definierar färgerna, så alla
  komponenter följer med.
* **Material**s **accent** följer färgschemat, precis som temats egen accent: det
  ljusa schemat använder 500-nyanserna och det mörka **200-nyanserna** (Material
  gör självt så - accenten är deep teal 500 `#009688` i ett ljust tema och deep
  teal 200 `#80CBC4` i ett mörkt). **Primärfärgen gäller bara i det ljusa
  schemat**: ett mörkt tema behåller sin mörka åtgärdslist och svarta statusrad,
  som Materials eget mörka tema gör och som Material Design rekommenderar (en list
  i primärfärg skulle blända mot ett mörkt innehåll). En vald primärfärg målar
  därför aldrig om den mörka listen - raden går fortfarande att använda och valet
  gäller igen i det ljusa schemat. Accentraden har ett alternativ som primärraden
  inte har, **svart / vit**, före den gröna: svart i det ljusa schemat, vit i det
  mörka, så den syns i båda. Det ljusa schemat behåller 500-nyanserna och ser
  därför ut precis som förut. Ett val sparas som den färg som syntes på skärmen när
  det gjordes, så samma val läses som 500 i det ena schemat och som 200 i det
  andra; `AppColors.indexOfColor()` letar upp det i båda paletterna.
* **Holo** har inget accentattribut, och ramverkets komponenter skulle inte läsa
  ett sådant ändå, så accenten läggs på vy för vy: `setButtonTintList()` för
  kryssrutorna och radioknapparna, `setProgressTintList()` för
  förloppsindikatorn, likadant för kontrollerna i en dialog (inklusive raderna i en
  envalslista), och en vanlig färg för dialogtiteln, avdelaren under den,
  dialogknapparna, länkar och versionsraden under underrubriken. Versionsraden är
  det enda element som behåller en accent även när standardvalet lämnas orört -
  då bär den temats egen färg och stämmer därför alltid med kryssrutorna. **Själva
  åtgärdslisten behåller temats svarta färg**, och bara den blå linje det mörka
  schemat ritar längs dess nederkant målas om med accentfärgen. Android 4.x har
  inget API för färgning, så där följer bara texten accenten; väljaren säger det
  under.
* Holos palett erbjuder **inte** temats egen turkos (`#0099CC`): det är just vad
  standardvalet står för, så de två skulle vara omöjliga att skilja. Materials
  accentpalett hoppar av samma skäl över teal (`#009688`) - det är accenten
  Material självt ritar sina komponenter med - och lägger i stället till
  alternativet **svart / vit**, som primärraden inte har, så båda raderna erbjuder
  tio färger (accentraden byter teal mot svart / vit; teal är en giltig
  primärfärg).

Definitionerna finns i `res/values/styles.xml`, med överlagringen för Android 5.0+
i `res/values-v21/styles.xml`. Det aktiva temat sätts i `MainActivity.onCreate`
med `setTheme()` innan innehållsvyn skapas. Om ett tema går att använda på enheten
avgörs av `AppThemes.isAvailable()`; `get()` faller tillbaka på Holo när det
sparade temat inte gör det (till exempel efter att en säkerhetskopia från en 5.0+
återställts på en 4.x-enhet).

Att byta tema, färgschema eller färg startar om aktiviteten med en **ny intent**
(i stället för att anropa `recreate()`): Holo och Material har olika
implementeringar av åtgärdslisten, och `recreate()` får ramverket att återställa
den ena listens sparade tillstånd i den andra, vilket kastar `ClassCastException`
(`ActionBarView$SavedState cannot be cast to Toolbar$SavedState`) och kraschar
appen.

## Gränssnitt

Huvudskärmen är en enda rullande sida: en kolumn av vyer där den flexibla är listan
över källor. `PageScrollView` ger listan det utrymme som blir över på sidan -
skärmen minus rubriken ovanför, förloppsindikatorn och statusraden nedanför och
knapparna under dem - eller det minimum listan ber om när det blir mindre än så, och
då är det listan som rullar medan allt omkring står stilla. Själva sidan rullar
först när utrymmet är mindre än minimumet, och då bara den lilla resten. Det är
`PageListView` som gör det möjligt: den rapporterar höjden på alla sina rader i
stället för den enda rad som ramverket mäter en lista till inuti en rullande sida,
och räknar själv fram sitt minimum - två rader - ur hur hög en rad visar sig vara.
Listans rullningslist hålls kvar på skärmen, så en rad som slutar precis vid dess
nederkant är inte det enda tecknet på att fler källor följer.

Sidan har utfyllnad bara till vänster och höger, och det vertikala avståndet hör
till kolumnen inuti. En `ScrollView` begränsar sin rullning till höjden på sitt
innehåll minus sin egen utfyllnad, så en vertikal utfyllnad på sidan är utrymme som
innehållet aldrig rullar in i: den står stilla högst upp och längst ner i det
rullbara området medan innehållet glider förbi bakom den, vilket ser ut som att
sidan tar slut för tidigt i båda ändar.

I den övre listen sitter knappen **Tema**, och menyn bakom den har språkvalet,
projektets webbplats, GitHub, dialogen *Om* och *Bekräfta installationer
automatiskt* (från Android 4.1, där tillgänglighetsramverket kan klicka på en
knapp).

## Lokalisering

Gränssnittet finns på sex språk: **简体中文**, **繁體中文**, **English**,
**Polski**, **Русский** och **Svenska**.

* Som standard **följer appen systemets språk**: Android väljer rätt
  `res/values-*`-katalog, och ett språk utanför de sex täcks av `res/values`
  (engelska). `zh-CN` använder `res/values-zh-rCN` (förenklad), `zh-TW` använder
  `res/values-zh-rTW` (traditionell), alla andra kinesiska språk använder
  `res/values-zh` (förenklad), och `pl`, `ru` och `sv` använder `res/values-pl`,
  `res/values-ru` och `res/values-sv`.
* Posten **Språk** i menyn låter användaren välja i stället: *Enhetens standard*
  eller något av de sex språken, alltid i samma ordning: 简体中文, 繁體中文,
  English, Polski, Русский, Svenska. Valet sparas i `SharedPreferences` och
  tillämpas i `MainActivity.onCreate` via `AppLanguage.apply()` (en åsidosättning
  av `updateConfiguration` som fungerar på API 14 - 23), följt av att aktiviteten
  startas om.
* **Varje** synlig text kommer från en resurs, inklusive leverantörsnamnen och
  noterna i certifikatkällornas lista: `CertCatalog` läser dem med
  `Context.getString()` när katalogen byggs. Därför byggs katalogen efter
  `AppLanguage.apply()` - en fältinitiering skulle köras innan det sparade språket
  tillämpats och använda systemets språk i stället.

Kvalificerarna `values-zh-rCN` / `values-zh-rTW` / `values-zh` (i stället för
`b+zh+Hans` / `b+zh+Hant`) används eftersom BCP 47-skriptkvalificerare inte stöds
före Android 5.0 (API 21).

Själva README finns på samma sex språk, i samma ordning i väljaren högst upp:
`README.md` (förenklad kinesiska, standard), `README_zh_TW.md`,
`README_en_US.md`, `README_pl_PL.md`, `README_ru_RU.md` och `README_sv_SE.md`.

## Bygga

Krav: JDK 17 (eller 21) och Android SDK (build-tools 34, platform android-33).

```bash
./gradlew assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
```

`gradlew` / `gradlew.bat` hittar själva en lämplig JDK. Groovy-kompilatorn som
följer med Gradle 8.7 kan inte läsa klassfiler från en nyare JDK, så ett byggskript
som nämner någon JDK-typ - Boolean i `buildFeatures { buildConfig true }` räcker -
misslyckas med "Unsupported class file major version 70"; en maskin vars `java` är
JDK 26 är precis en sådan. Omslaget letar därför efter 17 och sedan 21 på de
vanliga ställena (`/usr/libexec/java_home` på macOS, Homebrew, Linuxdistributionernas
`/usr/lib/jvm`, JDK:n som följer med Android Studio, de vanliga
installationsmapparna i Windows) och pekar `JAVA_HOME` på det som hittas. Ett
`JAVA_HOME` som redan pekar på en JDK av någon av de versionerna lämnas orört, så
ett medvetet val står alltid kvar. När inget hittas säger skriptet det, och
lösningen är att installera JDK 17 eller peka `JAVA_HOME` på en.

De två blocken - det i `gradlew` och det högst upp i `gradlew.bat` - känner Gradles
`wrapper`-uppgift inte till, så ett nygenererat omslag kastar bort dem. Kör
`./build-prep.sh` (eller `build-prep.bat` i Windows) efteråt för att lägga tillbaka
dem; `./build-prep.sh --check` rapporterar bara och ändrar inget, vilket är vad CI
vill. Blocken själva finns i `tools/gradlew-jdk17.sh` och
`tools/gradlew-jdk17.bat`, så de två skripten delar en kopia.

Dialogrutan Om (menyn i övre listen -> Om) listar versionsnamn och versionskod,
paketnamn samt byggtid och byggtyp (debug / release) under sin text, så att en
felrapport kan namnge vilket bygge den kommer från. Byggtiden skrivs in i
`BuildConfig.BUILD_DATE` av `build.gradle` när bygget konfigureras, så den anger
byggögonblicket och inte installationen; priset är att samma commit inte längre ger
en byte-identisk APK.

## Projektstruktur

```
app/src/main/java/cc/wuersan008/zhusheng/
  MainActivity.java     gränssnitt + flödet för hämtning/installation
  CertCatalog.java      listan över officiella certifikatkällor
  KnownRoots.java       de rötter båda paketen väntas innehålla
  CertSource.java       en post i katalogen
  Downloader.java       hämtning (kedjan från https till vanlig http)
  CertParser.java       PEM- / DER-tolkning (enskilda certifikat och paket)
  HashUtils.java        PEM-kodning, SHA-256, subject_hash_old
  CertInstaller.java    installation i och borttagning ur system- och användararkivet
  ScriptNotes.java      förklarar markörerna i utdata från ett root-skript
  TrustedRoots.java     det enheten redan litar på (AndroidCAStore)
  UserInstallMode.java  de tre sätten att installera i användararkivet + valet
  UserRemoveMode.java   de tre sätten att ta bort ur användararkivet + valet
  InstallerAutoConfirm.java  flaggor: installation pågår / tjänsten på
  RemovalAutoConfirm.java    flagga: borttagning pågår + namnen att matcha
  AutoConfirmService.java    tillgänglighetstjänst: installerardialoger, certifikatlistan
  RootUtils.java        hjälpare för su-skalet
  SourceAdapter.java    ListView-adapter
  PageListView.java     källistan, mätt för en sida som rullar
  PageScrollView.java   sidan: listans utrymme, rullningen, dragningarna
  LegacyNotice.java     engångsnotisen om Android 7.0+
  AppThemes.java        val och lagring av tema
  AppColors.java        temats färger, lagda per vy eller per temaöverlägg
  AppLanguage.java      val och lagring av språk
  LaoLuo.java           igenkänning av Smartisan-maskinvara (tema)
  ZhushengApp.java      Application (fångar systemets riktiga språk)
```

## Begränsningar

* Att skriva till `/system/etc/security/cacerts` kräver root. Utan root använd
  användararkivet.
* Borttagning ur systemarkivet kräver också root. I användararkivet kräver bara ett
  av de tre sätten det: det läser filerna direkt, eftersom arkivet inte har något
  API för att ta bort certifikat. De andra två kräver ingen root alls – genomgången
  av listan i inställningarna för hand, och tjänsten för automatisk bekräftelse som
  gör samma sak själv. Den senare går igenom inställningsskärmen i den version den
  körs på, så en enhet med en annan skärm får nöja sig med genomgången för hand.
* Tjänsten för automatisk bekräftelse kräver Android 4.1 eller senare; i Android
  4.0 måste varje fråga vid installation i användararkivet bekräftas för hand.
* Appar som pinnar certifikat eller har ett eget förtroendearkiv påverkas inte.

## Licens

Projektet släpps under [Unlicense](UNLICENSE), som överlämnar det till den
allmänna egendomen (public domain). Du får fritt kopiera, ändra, publicera,
använda, kompilera, sälja och distribuera det — i källkods- eller binär form, för
vilket syfte som helst, kommersiellt eller icke-kommersiellt, med vilka medel som
helst, utan att ange upphovsperson.

Programvaran tillhandahålls i befintligt skick, utan någon som helst garanti.
Den fullständiga texten finns i filen [UNLICENSE](UNLICENSE).
