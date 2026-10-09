# zhusheng

![slop badge](.github/assets/slop_badge.webp)

[简体中文](README.md) | [繁體中文](README_zh_TW.md) | [English](README_en_US.md) | Polski | [Русский](README_ru_RU.md) | [Svenska](README_sv_SE.md)

**zhusheng** przywraca zaufanie TLS/HTTPS w całym systemie na starych
urządzeniach z Androidem (**4.0 - 6.0**, API 14 - 23), pobierając oficjalne
certyfikaty główne publikowane przez najważniejszych dostawców TLS/SSL i
instalując je w magazynach certyfikatów Androida.

Stare urządzenia mają nieaktualny zestaw zaufanych certyfikatów głównych. Gdy
witrynę obsługuje CA, które nie istniało (lub nie było zaufane) w chwili
produkcji urządzenia - najsłynniej **Let's Encrypt** - każde żądanie HTTPS kończy
się błędem certyfikatu i urządzenie „nie ma dostępu do internetu”. zhusheng
naprawia to, instalując brakujące certyfikaty główne.

* Działa na **wszystkich architekturach** (aplikacja to czysta Java, bez kodu
  natywnego).
* Działa na **Androidzie od 4.0 do 6.0**.
* Używa wyłącznie **natywnych komponentów interfejsu Androida** - bez AndroidX i
  bez bibliotek zewnętrznych.

## Jak to działa

1. **Pobieranie.** Wybierz potrzebnych dostawców (albo zostaw zestaw zalecany) i
   naciśnij *Pobierz zaznaczone*. Certyfikaty są pobierane z oficjalnych punktów
   końcowych CA wymienionych poniżej.
2. **Instalacja.** Dostępne są dwa tryby:
   * **Magazyn SYSTEM (root)** - certyfikaty są zapisywane jako pliki PEM w
     `/system/etc/security/cacerts/<subject_hash>.<n>`, dokładnie tak jak
     certyfikaty fabryczne, a `/system` jest ponownie montowany tylko do odczytu.
     Zmiany działają po ponownym uruchomieniu. Dzięki temu ufa im *każda*
     aplikacja.
   * **Magazyn UŻYTKOWNIKA (bez roota)** - certyfikaty są instalowane przez
     systemowy instalator certyfikatów Androida (`KeyChain.createInstallIntent`).
     W wersjach Androida **poniżej 7.0** certyfikaty w magazynie użytkownika są
     zaufane w całym systemie, więc to również naprawia HTTPS we wszystkich
     aplikacjach, bez roota. Przycisk pyta najpierw, jak mają przebiegać
     potwierdzenia instalatora, i podaje trzy sposoby (`UserInstallMode`, wybór
     jest zapamiętywany na następny raz):
     * **jedno pytanie o cały zestaw** - cały zestaw trafia najpierw do instalatora
       jako **jeden** intent: pakiet poświadczeń (credentials bundle) z jednym
       wpisem `CACERT_<n>` na certyfikat
       (`CertInstaller.userStoreBulkInstallIntent`). Instalator, który go rozumie,
       dodaje wszystkie korzenie w jednym przebiegu; część wydań 4.x czyta pakiet
       i po cichu go ignoruje, a aplikacja wraca wtedy do jednego certyfikatu na
       okno;
     * **jedno pytanie na certyfikat** - pakiet nie jest w ogóle próbowany, więc
       instalator jest pytany o jeden certyfikat naraz. Wolniej niż pierwszy
       sposób, ale zachowuje się tak samo w każdym instalatorze;
     * **potwierdzaj komunikaty automatycznie** - ten sam przebieg co pierwszy
       sposób, ale komunikaty potwierdza **usługa automatycznego potwierdzania**.

     Certyfikaty, którym urządzenie **już ufa, są pomijane**, a duplikaty między
     źródłami są odrzucane (`TrustedRoots`), więc kolejne uruchomienie - albo
     dokończenie przerwanej instalacji - nie ma już czego potwierdzać.

     **Usługa automatycznego potwierdzania** jest opcjonalna i wymaga **Androida
     4.1 lub nowszego**. Aplikacja nie może włączyć własnej usługi ułatwień
     dostępu, robi to tylko użytkownik w systemowych ustawieniach ułatwień dostępu
     - dlatego aplikacja wyjaśnia, czym ta usługa jest, i podaje nazwę pozycji,
     której trzeba szukać, zanim otworzy tę stronę. Ostrzega też, że usługa działa,
     szybko przechodząc przez kolejne ekrany, więc w trakcie przebiegu ekran może
     mrugać, i wskazuje osobom wrażliwym na migające światło dwa sposoby
     potwierdzania ręcznego. Robi tak pozycja menu
     *Automatyczne potwierdzanie instalacji* oraz wybór automatycznego sposobu
     powyżej, gdy usługa nie jest jeszcze włączona; w drugim przypadku instalacja
     rusza sama, gdy użytkownik wróci z ustawień. Gdy trwa przebieg rozpoczęty
     przez tę aplikację, usługa klika przycisk *OK* w oknach instalatora, a przy
     usuwaniu - wiersze listy certyfikatów; poza takim przebiegiem nie robi nic, a
     dwa sposoby potwierdzane ręcznie nigdy nie każą jej startować. W Androidzie
     4.0 framework ułatwień dostępu nie potrafi w ogóle kliknąć przycisku
     (`performAction(ACTION_CLICK)` pojawiło się wraz z
     `View.performAccessibilityAction` w 4.1), więc urządzenie z 4.0 potwierdza
     komunikaty ręcznie.

3. **Usuwanie.** Oba magazyny można znów opróżnić, więc instalację da się cofnąć bez
   drugiego urządzenia i bez przywracania ustawień fabrycznych:
   * **Magazyn SYSTEM (root)** – usuwa tylko te pliki, które dodała ta aplikacja.
     Aplikacja zapisuje nazwę każdego skopiowanego pliku (`system-installed.txt`
     w swoim prywatnym katalogu `files`) i przed usunięciem sprawdza, czy zawartość
     pliku to rzeczywiście ten certyfikat: korzeń dostarczony z urządzeniem i ten sam
     korzeń z pakietu mogą być dokładnie tymi samymi bajtami, więc sam magazyn ich nie
     rozróżni, a korzenia z urządzenia nie wolno skasować.
   * **Magazyn UŻYTKOWNIKA** – magazyn użytkownika nie ma API do usuwania
     certyfikatu, więc **3b. Usuń z magazynu UŻYTKOWNIKA** pyta, którym z trzech
     sposobów to zrobić – tak jak 2b pyta o sposób potwierdzania instalacji
     (`UserRemoveMode`, wybór również jest zapamiętywany na następny raz):
     * **Usuń pliki z użyciem roota** – pliki w `/data/misc/user/0/cacerts-added`
       (w starszych wersjach `/data/misc/keychain/cacerts-added`) są czytane
       bezpośrednio. Plik znika tylko wtedy, gdy jego zawartość to jeden
       z wczytanych certyfikatów. To jedyny sposób wymagający roota; na urządzeniu
       bez roota jest wyszarzony i tak opisany, więc nie da się go wybrać.
     * **Usuń ręcznie w ustawieniach** – otwiera się lista certyfikatów
       w ustawieniach systemowych i każdy certyfikat usuwasz tam sam: dotknij jego
       wiersza, naciśnij *Usuń*, potwierdź. Aplikacja niczego nie usuwa, a ten sposób
       nie wymaga roota. Okno wypisuje kolejne kroki i daje przycisk otwierający ten
       ekran; zrzuty ekranu do tego przewodnika jeszcze dojdą.
     * **Usuń automatycznie (usługa ułatwień dostępu)** – ta sama lista, ale robi to
       usługa automatycznego potwierdzania (patrz niżej).

   Sposoby, które faktycznie coś usuwają – 3a i sposób z rootem w 3b – najpierw
   proszą o potwierdzenie, i oba wymagają roota: magazyn użytkownika dlatego, że nie
   ma API, a nie z powodu sposobu zapisu.

   Trzeci sposób zostawia usuwanie **usłudze automatycznego potwierdzania**:
   aplikacja otwiera tę samą listę certyfikatów i przekazuje
   usłudze nazwy wczytanych certyfikatów, a ona dotyka wiersza każdego z nich,
   naciska *Usuń* na ekranie szczegółów, który się otwiera, i odpowiada na
   pojawiające się pytanie. Dotyka wyłącznie wierszy, których nazwa odpowiada
   jednemu z tych certyfikatów, i zatrzymuje się, gdy tylko aplikacja wróci na
   wierzch - lista znów należy do użytkownika. Wymaga tej samej włączonej usługi i
   chodzi po ekranie ustawień tej wersji, na której działa; w Androidzie 5.0 i 6.0
   to dotknięcie wiersza, przycisk *Usuń* pod szczegółami certyfikatu i *Tak*. Gdy
   usługa nie jest włączona, aplikacja najpierw ją wyjaśnia i daje przycisk
   otwierający tę stronę, a po powrocie pyta o usunięcie jeszcze raz.

   Każde uruchomienie skryptu z uprawnieniami roota - instalacje i usunięcia w obu
   magazynach (2a, 3a oraz sposób roota z 3b) - kończy się oknem, które cytuje
   wynik skryptu dokładnie tak, jak wrócił, bo właśnie ten wynik powinien znaleźć
   się w zgłoszeniu błędu, a pod nim wyjaśnia rozpoznane znaczniki w języku, na
   który przełączona jest aplikacja (`ScriptNotes`): `ZHUSHENG_COPIED=`,
   `ZHUSHENG_REMOVED=`, `ZHUSHENG_DEDUPED=`, `ZHUSHENG_DONE` oraz wiersze
   `no system-installed.txt`, `could not write`, `command timed out` i
   `exec failed`. Wyjaśnienia idą w kolejności znaczników w wyniku, znacznik
   wypisany dwa razy jest wyjaśniany raz, a wszystko, czego aplikacja nie rozpoznaje,
   jest i tak cytowane bez zmian. Pusty wynik dostaje zamiast tego jedyne wyjaśnienie,
   które wtedy ma znaczenie: skrypt nic nie wypisał, a tak właśnie wygląda odrzucona
   prośba o roota. (Na urządzeniu bez roota do tego okna w ogóle się nie dochodzi -
   2a i 3a mówią, że brakuje roota, a sposób roota z 3b jest wyszarzony.)

W **Androidzie 7.0 (API 24) i nowszym** system nie ufa już certyfikatom
zainstalowanym przez użytkownika, jeśli aplikacja jest kierowana na 7.0 lub
nowszy, więc tryb *magazynu użytkownika* nie działa tam w większości aplikacji;
tryb *magazynu systemowego* może jeszcze działać, ale wymaga roota, a wersje
nowsze niż 6.0 wykraczają poza to, co zhusheng oficjalnie obsługuje (kolejne
wydania Androida zmieniły systemowy magazyn certyfikatów jeszcze bardziej).
Aplikacja wyjaśnia to raz, przy **pierwszym uruchomieniu**: `LegacyNotice` jest
wywoływane na końcu `onCreate()`, a flaga `legacy_notice_shown` zapisuje się w
`SharedPreferences`.

Nazwy plików w magazynie systemowym są generowane wartością OpenSSL
`x509 -subject_hash_old` (MD5 z podmiotu zakodowanego w DER), którą aplikacja
odtwarza w Javie (`HashUtils.subjectHashOld`). Zgodność z OpenSSL sprawdzono bajt
po bajcie.

## Oficjalne źródła certyfikatów

| Dostawca | URL |
| --- | --- |
| Mozilla CA Bundle (wszystkie główne CA) | `https://curl.se/ca/cacert.pem` |
| Google Trust Services (pakiet) | `https://pki.goog/roots.pem` |
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

**Mozilla CA Bundle** to rozwiązanie na wszystko: zawiera certyfikaty główne
wszystkich ważniejszych CA (m.in. Certum, SSL.com, IdenTrust, Buypass, D-TRUST,
QuoVadis, Actalis, HARICA, T-TeleSec, Verisign/Symantec), więc jedno pobranie
naprawia zdecydowaną większość niedziałających witryn.
### Bezpieczeństwo pobierania

Ponieważ zepsuty jest właśnie magazyn zaufania urządzenia, w pełni zweryfikowane
połączenie HTTPS z tymi punktami końcowymi może się nie udać (na przykład
`letsencrypt.org` obsługuje certyfikat Let's Encrypt). Urządzenie z Androidem 4.0
nie jest w stanie w ogóle przeprowadzić wymaganego uzgadniania TLS - a właśnie dla
takich urządzeń powstał ten program - więc pobieranie schodzi kolejno do HTTP:

1. najpierw próbuje pobrania HTTPS z **normalną weryfikacją**, a dopiero gdy to
   się nie uda,
2. ponawia próbę, akceptując dowolny certyfikat serwera,
3. ponawia próbę pod tym samym adresem po **zwykłym HTTP**, a na końcu
4. sięga po **kopię po zwykłym HTTP**, którą niesie ze sobą źródło - dla hostów
   odpowiadających wyłącznie po https (stała migawka oficjalnego pliku w Internet
   Archive).

O zaufaniu nie decyduje jednak żaden z tych sposobów, lecz sprawdzenie zawartości
po pobraniu, korzeń po korzeniu:

* każdy certyfikat musi być **samopodpisany** (subject == issuer, a podpis daje
  się zweryfikować jego własnym kluczem),
* źródło pojedynczego certyfikatu jest porównywane z **przypiętym odciskiem
  SHA-256**, a
* pakiet jest porównywany z **odciskami wszystkich korzeni, które ma zawierać**
  (`KnownRoots`), więc zawartość pobrana po zwykłym HTTP nie może przemycić
  własnego korzenia.

Sfałszowane pobranie nie może więc po cichu zainstalować podrobionego korzenia.

## Motywy

Aplikacja oferuje trzy motywy i dwa schematy kolorów, wybierane przyciskiem
**Motyw** na górnym pasku; wybór jest zapamiętywany w `SharedPreferences`.
**Na Androidzie 5.0 i nowszym domyślny jest Material** (pasuje do otaczającego
systemu); Android 4.x startuje z Holo, a sprzęt Smartisan startuje z **domyślnego
motywu urządzenia** (`LaoLuo`), żeby aplikacja otwierała się w firmowym wyglądzie
producenta na ROM-ie, dla którego powstała. Gdy motyw zostanie wybrany świadomie, ten
wybór zawsze wygrywa.

| Opcja | API 14 - 20 (4.0 - 4.4W) | API 21+ (5.0 - 6.0) |
| --- | --- | --- |
| **Holo** | `Theme.Holo.Light.DarkActionBar` | `Theme.Holo.Light.DarkActionBar` |
| **Material** | niedostępny (wyszarzony) | `Theme.Material.Light.DarkActionBar` |
| **Domyślny urządzenia** | `Theme.DeviceDefault.Light.DarkActionBar` | `Theme.DeviceDefault.Light.DarkActionBar` |

* **Holo** to motyw pierwotny i jest dostępny w każdej wspieranej wersji, ale
  domyślny jest tylko na Androidzie 4.x.
* **Material** korzysta z komponentów Material Design 1 z frameworka, więc jest
  dostępny tylko na Androidzie 5.0+, gdzie jest też **domyślny**. Na Androidzie
  4.x opcja jest **wyszarzona**, z wyjaśnieniem pod spodem, a nie po cichu
  zastępowana przez Holo.
* **Domyślny urządzenia** korzysta z motywu dostarczonego przez producenta
  (TouchWiz, Sense, MIUI, ...) przez `Theme.DeviceDefault`.

### Schemat kolorów

Każdy motyw występuje w wersji **jasnej** i **ciemnej**:

* **Jasny** (domyślny) używa wariantu `DarkActionBar` - jasna treść i ciemny
  pasek akcji - czyli pierwotnego wyglądu aplikacji.
* **Ciemny** używa w pełni ciemnego wariantu tego samego motywu (`Theme.Holo`,
  `Theme.Material`, `Theme.DeviceDefault`).

Kolory własnych powierzchni aplikacji (tło, tekst główny, tekst pomocniczy,
separator listy) są zadeklarowane jako atrybuty motywu `zsBackground`,
`zsTextPrimary`, `zsTextSecondary` i `zsDivider` (patrz
`res/values/attrs.xml`), więc treść nie zostaje biała w ciemnym schemacie.
Sześć motywów bazowych ustawia wartości jasne; ciemny schemat nadpisuje je przez
`ThemeOverlay.Zhusheng.Scheme.Dark` w `Theme.applyStyle()`.

### Kolory

Okno motywu oferuje też wybór kolorów. Pierwszy kafelek (domyślny) oznacza
**zostaw kolor samego motywu**, więc brak wyboru zostawia wygląd bez zmian;
domyślny kafelek Holo pokazuje turkus, którym sam motyw rysuje. Wybrany kafelek
ma na środku znaczek: biały w jasnym schemacie, czarny w ciemnym, którego odcienie
200 są zbyt blade dla białego znaczka. Jedyny wyjątek to pierwszy, „domyślny”
kafelek: ma kolor samego motywu, więc jego znaczek jest czarny albo biały
zależnie od jasności. Wiersz z wersją pod podtytułem (`device_info`) to jedyny
element aplikacji noszący kolor akcentu, więc bez wyboru używa koloru motywu i nie
kłóci się z polami wyboru obok.

| Motyw | Kolory | Gdzie działają |
| --- | --- | --- |
| **Holo** | akcent (4 kolory Holo) | wszystko, co motyw rysuje swoim turkusem: pola wyboru na liście, przyciski opcji i znaczniki w oknie, pasek postępu, łącza, tytuł okna i separator pod nim, przyciski okna, niebieska linia na dole ciemnego paska akcji oraz wiersz wersji pod podtytułem (części wymagające barwienia pojedynczych widoków potrzebują API 21+) |
| **Material** | główny (10 kolorów Material) + akcent (10) | kolor główny wyznacza pasek akcji i pasek stanu **jasnego** schematu (ciemny zawsze zachowuje własny ciemny pasek), akcent wyznacza pola wyboru, pasek postępu, przyciski okna, wiersz wersji pod podtytułem itd. |
| **Domyślny urządzenia** | brak | kolory pochodzą z motywu producenta |
* Kolory **Material** są nakładane przez nakładki motywu
  (`android:colorPrimary` / `colorPrimaryDark` / `colorAccent` /
  `colorControlActivated`) zdefiniowane w `res/values-v21/color_overlays.xml` i
  dokładane do motywu przez `AppColors.applyTheme()` po `setTheme()`, a przed
  `super.onCreate()`. Tak właśnie Material sam definiuje te kolory, więc każdy
  widżet idzie za nimi.
* **Akcent** motywu **Material** podąża za schematem kolorów, dokładnie jak akcent
  samego motywu: jasny schemat używa odcieni 500, a ciemny **odcieni 200**
  (Material sam tak robi - jego akcent to deep teal 500 `#009688` w jasnym
  motywie i deep teal 200 `#80CBC4` w ciemnym). **Kolor główny działa tylko w
  jasnym schemacie**: ciemny motyw zachowuje ciemny pasek akcji i czarny pasek
  stanu, tak jak robi to własny ciemny motyw Material i jak zaleca Material
  Design (pasek w kolorze głównym raziłby na ciemnej treści). Wybrany kolor
  główny nigdy więc nie przemalowuje ciemnego paska - wiersz nadal działa, a
  wybór znów zaczyna obowiązywać w jasnym schemacie. Wiersz akcentu ma jedną
  pozycję, której nie ma wiersz koloru głównego: **czarny / biały** przed
  zielonym - czarny w jasnym schemacie, biały w ciemnym, więc widać go w obu.
  Jasny schemat zachowuje odcienie 500, więc wygląda dokładnie jak dotąd. Wybór
  jest zapisywany jako kolor widoczny w chwili wyboru, więc ten sam wybór czyta
  się raz jako 500, a raz jako 200; `AppColors.indexOfColor()` szuka go w obu
  paletach.
* **Holo** nie ma atrybutu akcentu, a widżety frameworka i tak by go nie
  czytały, więc jego akcent jest nakładany widok po widoku: `setButtonTintList()`
  dla pól wyboru i przycisków opcji, `setProgressTintList()` dla paska postępu,
  tak samo dla kontrolek w oknie (łącznie z wierszami listy jednokrotnego
  wyboru), a zwykły kolor dla tytułu okna, separatora pod nim, przycisków okna,
  łączy i wiersza wersji pod podtytułem. Wiersz wersji to jedyny element, który
  zachowuje akcent nawet przy domyślnym wyborze - wtedy nosi kolor samego
  motywu, więc zawsze zgadza się z polami wyboru. **Sam pasek akcji zachowuje
  czerń motywu**, a kolorem akcentu pokrywana jest tylko niebieska linia, którą
  ciemny schemat rysuje na jego dolnej krawędzi. Android 4.x nie ma API
  barwienia, więc tam za akcentem podąża tylko tekst; wybór mówi o tym pod
  spodem.
* Paleta Holo **nie** oferuje turkusu samego motywu (`#0099CC`): to właśnie
  oznacza pozycja domyślna, więc oba wybory byłyby nieodróżnialne. Paleta akcentu
  Materialu z tego samego powodu pomija teal (`#009688`) - to akcent, którym
  Material sam rysuje swoje widżety - a w zamian dodaje pozycję **czarny /
  biały**, której nie ma wiersz koloru głównego, więc oba wiersze oferują po
  dziesięć kolorów (wiersz akcentu wymienia teal na czarny / biały; teal jest
  prawidłowym kolorem głównym).

Definicje motywów są w `res/values/styles.xml`, a nadpisania dla Androida 5.0+ w
`res/values-v21/styles.xml`. Aktywny motyw jest ustawiany w
`MainActivity.onCreate` przez `setTheme()`, zanim powstanie widok treści. O tym,
czy motyw działa na danym urządzeniu, decyduje `AppThemes.isAvailable()`; `get()`
wraca do Holo, gdy zapisany motyw nie jest dostępny (na przykład po przywróceniu
kopii z urządzenia 5.0+ na 4.x).

Zmiana motywu, schematu kolorów albo koloru restartuje aktywność **świeżym
intentem** (a nie przez `recreate()`): Holo i Material mają różne implementacje
paska akcji, a `recreate()` każe frameworkowi przywrócić zapisany stan jednego
paska w drugim, co kończy się `ClassCastException` (`ActionBarView$SavedState
cannot be cast to Toolbar$SavedState`) i wywala aplikację.

## Interfejs

Ekran główny to jedna przewijana strona: kolumna widoków, z których elastyczny jest
lista źródeł. `PageScrollView` daje tej liście miejsce, które zostało na stronie -
ekran mniej nagłówek nad nią, pasek postępu i wiersz stanu pod nią oraz przyciski pod
nimi - albo minimum, o które lista prosi, gdy zostaje mniej niż to, i wtedy to lista
się przewija, a wszystko wokół niej stoi. Sama strona przewija się dopiero wtedy, gdy
tego miejsca jest mniej niż minimum, i tylko o tę niewielką nadwyżkę. `PageListView`
właśnie to umożliwia: podaje wysokość wszystkich swoich wierszy, a nie tego jednego,
do którego framework mierzy listę wewnątrz przewijanej strony, i samo wylicza swoje
minimum - dwa wiersze - z wysokości, jaką okaże się mieć wiersz. Pasek przewijania
listy pozostaje widoczny, więc wiersz kończący się dokładnie na jej dolnej krawędzi
nie jest jedynym znakiem, że poniżej są kolejne źródła.

Strona ma margines wewnętrzny tylko z lewej i z prawej, a odstęp w pionie należy do
kolumny w środku. `ScrollView` ogranicza przewijanie do wysokości swojej treści
mniejszej o własny margines wewnętrzny, więc pionowy margines wewnętrzny strony to
miejsce, w które treść nigdy nie przewinie: stoi ono nieruchomo u góry i u dołu
obszaru przewijania, a treść przesuwa się za nim, co wygląda, jakby strona kończyła
się za wcześnie z obu stron.

Górny pasek to przycisk **Motyw**, a menu za nim to wybór języka, strona projektu,
GitHub, okno *O programie* oraz *Automatyczne potwierdzanie instalacji* (od Androida
4.1, gdzie framework ułatwień dostępu potrafi kliknąć przycisk).

## Lokalizacja

Interfejs jest dostępny w sześciu językach: **简体中文**, **繁體中文**,
**English**, **Polski**, **Русский** i **Svenska**.

* Domyślnie aplikacja **podąża za językiem systemu**: Android wybiera pasujący
  katalog `res/values-*`, a język spoza tej szóstki obsługuje `res/values`
  (angielski). `zh-CN` używa `res/values-zh-rCN` (uproszczony), `zh-TW` używa
  `res/values-zh-rTW` (tradycyjny), każdy inny język chiński używa
  `res/values-zh` (uproszczony), a `pl`, `ru` i `sv` używają `res/values-pl`,
  `res/values-ru` i `res/values-sv`.
* Pozycja **Język** w menu pozwala to nadpisać: do wyboru jest *Domyślny
  systemu* oraz sześć języków, zawsze w tej samej kolejności: 简体中文, 繁體中文,
  English, Polski, Русский, Svenska. Wybór trafia do `SharedPreferences` i jest
  stosowany w `MainActivity.onCreate` przez `AppLanguage.apply()` (nadpisanie
  `updateConfiguration`, które działa na API 14 - 23), po czym aktywność jest
  restartowana.
* **Każdy** widoczny tekst pochodzi z zasobu, także nazwy dostawców i notatki na
  liście źródeł certyfikatów: `CertCatalog` odczytuje je przez
  `Context.getString()` podczas budowania katalogu. Dlatego katalog powstaje po
  `AppLanguage.apply()` - inicjalizator pola uruchomiłby się przed zastosowaniem
  zapisanego języka i użyłby języka systemu.

Kwalifikatory `values-zh-rCN` / `values-zh-rTW` / `values-zh` (a nie
`b+zh+Hans` / `b+zh+Hant`) są używane, bo kwalifikatory skryptu BCP 47 nie są
obsługiwane przed Androidem 5.0 (API 21).

Sam README jest przetłumaczony na te same sześć języków, w tej samej kolejności w
przełączniku na górze: `README.md` (chiński uproszczony, domyślny),
`README_zh_TW.md`, `README_en_US.md`, `README_pl_PL.md`, `README_ru_RU.md` i
`README_sv_SE.md`.

## Budowanie

Wymagania: JDK 17 (albo 21) oraz Android SDK (build-tools 34, platform
android-33).

```bash
./gradlew assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
```

`gradlew` / `gradlew.bat` same znajdują odpowiedni JDK. Dołączony do Gradle 8.7
kompilator Groovy'ego nie czyta plików class nowszego JDK, więc skrypt
budowania, który wymienia jakikolwiek typ JDK - wystarczy Boolean z
`buildFeatures { buildConfig true }` - kończy się błędem "Unsupported class file
major version 70"; maszyna, na której domyślne `java` to JDK 26, jest dokładnie
taka. Wrapper szuka więc najpierw 17, a potem 21 w typowych miejscach
(`/usr/libexec/java_home` na macOS, Homebrew, `/usr/lib/jvm` w dystrybucjach
Linuksa, JDK dołączony do Android Studio, typowe katalogi instalacyjne w
Windows) i wskazuje na nie `JAVA_HOME`. `JAVA_HOME` wskazujący już na JDK w
jednej z tych wersji nie jest ruszany, więc świadomy wybór zawsze obowiązuje.
Gdy nic nie znajdzie, mówi o tym, a rozwiązaniem jest instalacja JDK 17 albo
wskazanie go w `JAVA_HOME`.

Te dwa bloki - jeden w `gradlew`, drugi na początku `gradlew.bat` - są dla
zadania `wrapper` w Gradle niewidoczne, więc ponowne wygenerowanie wrappera je
usuwa. Po tym uruchom `./build-prep.sh` (w Windows `build-prep.bat`), żeby je
przywrócić; `./build-prep.sh --check` tylko raportuje i niczego nie zmienia,
czego chce CI. Same bloki są w `tools/gradlew-jdk17.sh` i
`tools/gradlew-jdk17.bat`, więc oba skrypty dzielą jedną kopię.

Okno „O programie” (menu na górnym pasku -> O programie) pod tekstem podaje nazwę
i numer wersji, nazwę pakietu oraz czas i typ kompilacji (debug / release), żeby
zgłoszenie błędu mogło wskazać konkretną kompilację. Czas kompilacji jest
wpisywany do `BuildConfig.BUILD_DATE` przez `build.gradle` w fazie konfiguracji,
więc oznacza moment kompilacji, a nie instalacji; kosztem jest to, że ten sam
commit nie daje już APK identycznego co do bajtu.

## Układ projektu

```
app/src/main/java/cc/wuersan008/zhusheng/
  MainActivity.java     interfejs + przebieg pobierania/instalacji
  CertCatalog.java      lista oficjalnych źródeł certyfikatów
  KnownRoots.java       korzenie, które mają zawierać oba pakiety
  CertSource.java       jedna pozycja katalogu
  Downloader.java       pobieranie (łańcuch od https do zwykłego http)
  CertParser.java       parsowanie PEM / DER (pojedyncze certyfikaty i pakiety)
  HashUtils.java        kodowanie PEM, SHA-256, subject_hash_old
  CertInstaller.java    instalacja w magazynie SYSTEM (root) i UŻYTKOWNIKA oraz ich usuwanie
  ScriptNotes.java      wyjaśnia znaczniki w wyniku skryptu roota
  TrustedRoots.java     to, czemu urządzenie już ufa (AndroidCAStore)
  UserInstallMode.java  trzy sposoby instalacji w magazynie UŻYTKOWNIKA + wybór
  UserRemoveMode.java   trzy sposoby usuwania z magazynu UŻYTKOWNIKA + wybór
  InstallerAutoConfirm.java  flagi: trwa instalacja / usługa włączona
  RemovalAutoConfirm.java    flaga: trwa usuwanie + nazwy do dopasowania
  AutoConfirmService.java    usługa ułatwień dostępu: okna instalatora, lista certyfikatów
  RootUtils.java        pomocniki powłoki su
  SourceAdapter.java    adapter ListView
  PageListView.java     lista źródeł, mierzona pod przewijaną stronę
  PageScrollView.java   strona: miejsce dla listy, przewijanie, przeciągnięcia
  LegacyNotice.java     jednorazowa informacja o Androidzie 7.0+
  AppThemes.java        wybór i zapamiętywanie motywu
  AppColors.java        kolory motywu, nakładane na widoki lub na motyw
  AppLanguage.java      wybór i zapamiętywanie języka
  LaoLuo.java           rozpoznawanie sprzętu Smartisan (motyw)
  ZhushengApp.java      Application (przechwytuje prawdziwy język systemu)
```

## Ograniczenia

* Zapis do `/system/etc/security/cacerts` wymaga roota. Bez roota użyj trybu
  magazynu UŻYTKOWNIKA.
* Usuwanie z magazynu SYSTEM wymaga roota. W magazynie użytkownika tylko jeden
  z trzech sposobów go wymaga: ten czyta pliki bezpośrednio, bo magazyn nie ma API
  do usuwania certyfikatów. Pozostałe dwa nie potrzebują roota wcale – ręczne
  przejście przez listę w ustawieniach i usługa automatycznego potwierdzania, która
  robi to samo sama. Ta druga chodzi po ekranie ustawień tej wersji, na której
  działa, więc urządzenie z inaczej zbudowanym ekranem zostaje przy ręcznym
  przejściu.
* Usługa automatycznego potwierdzania wymaga Androida 4.1 lub nowszego; w
  Androidzie 4.0 każde okno instalacji w magazynie UŻYTKOWNIKA trzeba potwierdzić
  ręcznie.
* Aplikacje, które przypinają certyfikaty albo mają własny magazyn zaufania, nie
  są tym objęte.

## Licencja

Projekt jest udostępniany na licencji [Unlicense](UNLICENSE), która oddaje go do
domeny publicznej. Możesz go swobodnie kopiować, modyfikować, publikować,
używać, kompilować, sprzedawać i rozpowszechniać — w formie źródła lub
skompilowanej postaci binarnej, w dowolnym celu, komercyjnym lub niekomercyjnym,
dowolnymi środkami, bez konieczności podawania autora.

Oprogramowanie jest dostarczane „tak jak jest”, bez jakiejkolwiek gwarancji.
Pełna treść znajduje się w pliku [UNLICENSE](UNLICENSE).
