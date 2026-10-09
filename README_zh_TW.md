# zhusheng

![slop badge](.github/assets/slop_badge.webp)

[简体中文](README.md) | 繁體中文 | [English](README_en_US.md) | [Polski](README_pl_PL.md) | [Русский](README_ru_RU.md) | [Svenska](README_sv_SE.md)

**zhusheng** 透過下載各大 TLS/SSL 供應商官方發佈的根憑證並安裝到 Android 憑證儲存區中，為較舊的 Android 裝置（**Android 4.0 - 6.0**，API 14 - 23）恢復系統範圍的 TLS/HTTPS 信任。

舊裝置出廠時內建的受信任根憑證往往已經過時。當某個網站使用裝置出廠時尚不存在（或尚未受信任）的 CA 簽發憑證時——最典型的就是 **Let's Encrypt**——所有 HTTPS 請求都會因憑證錯誤而失敗，裝置便「無法正常上網」。zhusheng 透過安裝缺少的根憑證來解決這個問題。

* 適用於**所有架構**（應用程式為純 Java 實作，不含原生程式碼）。
* 支援 **Android 4.0 至 6.0**。
* 僅使用**原生 Android UI 元件**——不使用 AndroidX、不引入任何第三方函式庫。

## 運作原理

1. **下載。** 選擇你需要的憑證授權單位（或保留建議集合），然後點選*下載所選項目*。憑證會從下方列出的官方 CA 端點取得。
2. **安裝。** 提供兩種方式：
   * **系統憑證儲存區（需 root）**——憑證以 PEM 檔案的形式寫入 `/system/etc/security/cacerts/<subject_hash>.<n>`，與預裝的根憑證完全一致，隨後將 `/system` 重新掛載為唯讀。重新啟動後生效。這樣所有應用程式都會信任這些憑證。
   * **使用者憑證儲存區（免 root）**——憑證透過 Android 憑證安裝程式安裝（`KeyChain.createInstallIntent`）。在 **Android 7.0 以下**的版本中，使用者憑證儲存區中的憑證在系統範圍內受信任，因此免 root 也能為所有應用程式修復 HTTPS。點選按鈕時會先詢問這些確認該如何進行，並列出三種做法（`UserInstallMode`，選擇會被記住，下次開啟時沿用）：
     * **整組只提示一次**——整批憑證先以**一個** intent 交給安裝程式：一個憑證包（credentials bundle），每張憑證對應一個 `CACERT_<n>` 項目（`CertInstaller.userStoreBulkInstallIntent`）。支援的安裝程式會一次加入全部根憑證；部分 4.x 機型會讀取該憑證包卻靜默忽略，此時應用程式會退回每張憑證一個對話方塊；
     * **每張憑證提示一次**——完全不嘗試憑證包，安裝程式一次只會被問到一張憑證。比第一種慢，但在任何安裝程式上表現一致；
     * **自動回答這些提示**——與第一種相同的流程，但由**自動確認服務**來回答這些提示。

     裝置**已經信任的憑證會被略過**，各來源之間的重複憑證也會被去掉（`TrustedRoots`），因此再次安裝——或接續一次被中斷的安裝——已無需再確認任何內容。

     **自動確認服務**是選用的，需要 **Android 4.1 或更新版本**。應用程式無權自行開啟自己的無障礙服務，只能由使用者在系統無障礙設定中開啟——因此應用程式會在開啟該頁面之前先說明這個服務是什麼，並給出要尋找的項目名稱：選單中的*自動確認安裝*會這樣做，上面的自動方式在服務尚未開啟時也會這樣做（此時使用者返回後安裝會自動繼續）。說明的最後還會提示：服務的運作方式是快速點過一個個畫面，因此執行期間螢幕可能會閃爍；對閃爍敏感的使用者可以改用兩種手動確認的方式。在本應用程式發起的一次操作進行期間，該服務會點擊安裝程式對話方塊的*確定*按鈕，移除時還會點選憑證清單的列；除此之外它什麼都不做，而兩種手動確認的方式也從不要求它啟動。在 Android 4.0 上無障礙框架根本無法點擊按鈕（`performAction(ACTION_CLICK)` 隨 4.1 的 `View.performAccessibilityAction` 才出現），因此 4.0 裝置仍需手動確認。

3. **移除。** 兩個憑證儲存區都可以再次清空，因此一次安裝不必借助第二台裝置或回復原廠設定就能撤銷：
   * **系統憑證儲存區（需 root）**——只刪除本應用程式加入的檔案。應用程式會記錄自己複製的每一個檔名（私有 files 目錄中的 `system-installed.txt`），並在刪除前逐一核對檔案內容是否就是它應該保存的那張憑證：裝置預裝的根憑證與某個憑證包中的同一張根憑證可能是完全相同的位元組，單憑憑證儲存區無法區分，而預裝的根憑證絕不能被刪除。
   * **使用者憑證儲存區**——使用者憑證儲存區沒有移除憑證的 API，因此 **3b. 從使用者憑證儲存區移除** 會像 2b 詢問安裝提示那樣，先詢問該走三種方式中的哪一種（`UserRemoveMode`，選擇同樣會被記住，下次沿用）：
     * **用 root 直接刪除檔案**——直接讀取 `/data/misc/user/0/cacerts-added`（較舊版本為 `/data/misc/keychain/cacerts-added`）下的檔案；只有當檔案內容確實是目前載入的某張憑證時才會刪除。這是唯一需要 root 的方式，在沒有 root 的裝置上它會顯示為灰色並註明原因，因此無法選取。
     * **在設定中手動移除**——開啟系統設定中的憑證清單，由使用者逐一移除：點選該列、按下*移除*、確認。應用程式不會刪除任何東西，也免 root。對話方塊會列出具體步驟並提供開啟該畫面的入口；該說明的截圖之後補上。
     * **自動移除（無障礙服務）**——開啟的是同一份清單，改由自動確認服務代勞（見下）。

   會真正刪除內容的方式——3a 與 3b 中的 root 方式——都會先請求確認，且都需要 root：使用者憑證儲存區需要 root 是因為沒有 API，而不是因為它的寫入方式。

   第三種方式把移除交給**自動確認服務**：應用程式開啟同一個憑證清單，把目前載入憑證的名稱交給服務，由服務點選每一列、按下詳細資料頁面上的*移除*，並確認隨之而來的詢問。它只會點選名稱與這些憑證相符的列，並且在應用程式回到前景時立即停止——此後這份清單重新屬於使用者。它同樣需要開啟該服務，並且走的是所執行版本自己的設定介面；在 Android 5.0 與 6.0 上，依次是一次點列、詳細資料下方的*移除*按鈕和一次*是*。未開啟該服務時，應用程式會先說明它並提供開啟該頁面的入口，返回後再一次詢問是否移除。

   每個 root 指令碼的執行——兩個憑證儲存區的安裝與移除（2a、3a，以及 3b 的 root 方式）——最後都會顯示一個對話方塊，把指令碼的輸出**原樣**引用出來（這正是問題報告應該包含的內容），並在下方用應用程式目前的語言解釋它認得的標記（`ScriptNotes`）：`ZHUSHENG_COPIED=`、`ZHUSHENG_REMOVED=`、`ZHUSHENG_DEDUPED=`、`ZHUSHENG_DONE`，以及 `no system-installed.txt`、`could not write`、`command timed out`、`exec failed` 這幾種行。解釋依標記在輸出中出現的順序排列，同一個標記出現兩次只解釋一次，而應用程式不認得的輸出同樣原樣引用。輸出完全為空時則改為給出此時唯一重要的解釋：指令碼什麼都沒有輸出——這正是 root 請求被拒絕時的樣子。（沒有 root 的裝置根本走不到這個對話方塊：2a 與 3a 會提示缺少 root，3b 的 root 方式則是置灰的。）

在 **Android 7.0（API 24）及以上**，系統不再信任使用者憑證儲存區中由使用者安裝的憑證（除非應用程式自行宣告信任），因此*使用者憑證儲存區*模式對大多數應用程式不再有效；*系統憑證儲存區*模式也許仍然可用，但需要 root，而且高於 6.0 的版本不在 zhusheng 的官方支援範圍內（更高版本的 Android 又進一步改動了系統憑證儲存區）。應用程式會在**首次啟動**時就此給出一次提示：`LegacyNotice` 在 `onCreate()` 末尾呼叫，旗標 `legacy_notice_shown` 記錄在 `SharedPreferences` 中，因此每台裝置只提示一次。

系統憑證儲存區的檔案名稱使用 OpenSSL 的 `x509 -subject_hash_old` 值（對 DER 編碼的 subject 求 MD5）產生，應用程式在 Java 中重現了這個演算法（`HashUtils.subjectHashOld`），並已驗證與 OpenSSL 逐位元組一致。

## 官方憑證來源

| 供應商 | URL |
| --- | --- |
| Mozilla CA 根憑證包（涵蓋所有主要 CA） | `https://curl.se/ca/cacert.pem` |
| Google Trust Services（根憑證包） | `https://pki.goog/roots.pem` |
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

**Mozilla CA 根憑證包**是萬用的兜底方案：它包含所有主要 CA 的根憑證（包括 Certum、SSL.com、IdenTrust、Buypass、D-TRUST、QuoVadis、Actalis、HARICA、T-TeleSec、Verisign/Symantec 等眾多 CA），因此只需下載這一個檔案即可修復絕大多數無法存取的網站。
### 下載的安全性

由於裝置上損壞的正是它的信任儲存區，對這些端點使用完全驗證的 HTTPS 連線可能會失敗（例如 `letsencrypt.org` 就是由 Let's Encrypt 憑證提供服務的）。而 Android 4.0 裝置根本無法完成這些主機要求的 TLS 握手——這正是本應用程式要解決的情境——因此下載會逐級回退：

1. 先嘗試**正常驗證**的 HTTPS 下載，僅在失敗時才
2. 重試並接受任意伺服器憑證，
3. 再用**一般 HTTP** 重試同一網址，
4. 最後回退到該來源附帶的**純 HTTP 副本**（官方主機只提供 https 時使用；即官方檔案的固定 Internet Archive 快照）。

真正保證安全的並不是傳輸方式，而是下載完成後的逐張驗證：

* 每張憑證都必須是**自簽章的根憑證**（subject == issuer，且簽章能用其自身公鑰驗證通過），
* 單張根憑證來源會與**固定的 SHA-256 指紋**進行比對，
* 憑證包則會與**預期包含的每一張根憑證指紋**（`KnownRoots`）逐一比對，因此透過純 HTTP 下載的內容也無法夾帶自己的根憑證。

因此，即使下載內容被竄改，也無法在使用者不知情的情況下裝入偽造的根憑證。

## 佈景主題

應用程式提供三種可選佈景主題和兩種配色配置，透過頂欄的**佈景主題**按鈕選擇；選擇會記錄在 `SharedPreferences` 中。**Android 5.0 及以上預設使用 Material**（更貼合系統外觀），Android 4.x 預設使用 Holo；錘子（Smartisan）裝置則預設使用**裝置預設**佈景主題（`LaoLuo`），讓應用程式在它為之打造的 ROM 上呈現廠商自己的外觀。使用者只要明確選過一次佈景主題，該選擇就始終優先。

| 選項 | API 14 - 20（4.0 - 4.4W） | API 21+（5.0 - 6.0） |
| --- | --- | --- |
| **Holo** | `Theme.Holo.Light.DarkActionBar` | `Theme.Holo.Light.DarkActionBar` |
| **Material** | 無法使用（選項反灰） | `Theme.Material.Light.DarkActionBar` |
| **裝置預設** | `Theme.DeviceDefault.Light.DarkActionBar` | `Theme.DeviceDefault.Light.DarkActionBar` |

* **Holo** 是原始佈景主題，在所有受支援的版本上都可用，但只在 Android 4.x 上作為預設佈景主題。
* **Material** 使用框架的 Material Design 1 元件，因此僅 Android 5.0+ 可用，並且**是 5.0+ 上的預設佈景主題**。在 Android 4.x 上該選項會被**反灰**，並在下方提示 *Not available on Android 4.x*，而不是悄悄回退成 Holo。
* **裝置預設** 透過 `Theme.DeviceDefault` 使用裝置廠商提供的佈景主題（TouchWiz、Sense、MIUI 等）。

### 配色配置

每個佈景主題都有**淺色**和**深色**兩種配置：

* **淺色**（預設）使用佈景主題的 `DarkActionBar` 變體，即淺色內容區 + 深色操作列，也就是應用程式原本的外觀。
* **深色**使用同一佈景主題的完全深色變體（`Theme.Holo`、`Theme.Material`、`Theme.DeviceDefault`）。

應用程式自身介面的顏色（背景、內文、次要文字、清單分隔線）宣告為佈景主題屬性 `zsBackground` / `zsTextPrimary` / `zsTextSecondary` / `zsDivider`（見 `res/values/attrs.xml`），因此深色配置下內容區不會出現「白底黑字」的割裂感。六個基礎佈景主題設定淺色值，深色配置透過 `ThemeOverlay.Zhusheng.Scheme.Dark` 在 `Theme.applyStyle()` 中疊加覆蓋。

### 顏色

佈景主題對話方塊還提供顏色選擇器。預設項目（第一個色塊）表示**保持佈景主題自帶的顏色**，因此完全不做選擇時外觀與原來一模一樣；Holo 的預設色塊顯示的就是佈景主題自身使用的青色。選中的色塊中央畫一個對勾：淺色配置下為白色，深色配置下為黑色（深色配置用的是 200 淺色調，白色對勾在上面幾乎看不見）；唯一的例外是第一個「預設」色塊，它的顏色取決於佈景主題自身，因此對勾按亮度選黑或白。副標題下方那行版本資訊（`device_info`）是應用程式自己唯一的強調色元素：不做選擇時它採用佈景主題自身的強調色，因此不會與旁邊的核取方塊顏色衝突。

| 佈景主題 | 可調顏色 | 作用範圍 |
| --- | --- | --- |
| **Holo** | 強調色（4 種 Holo 色） | 佈景主題本來畫成青色的那些元素：清單核取方塊、對話方塊裡的選項按鈕、進度列、連結文字、對話方塊標題與標題下的分隔線、對話方塊按鈕、深色配置下操作列底部的那條青線，以及副標題下方的版本資訊行（後兩者中依賴逐檢視 tint 的部分需要 API 21+） |
| **Material** | 主色（10 種 Material 色）+ 強調色（10 種） | 主色決定**淺色配置**下的操作列與狀態列（深色配置始終使用佈景主題自帶的深色操作列與狀態列），強調色決定核取方塊、進度列、對話方塊按鈕、副標題下方的版本資訊行等 |
| **裝置預設** | 無 | 顏色由裝置廠商的佈景主題決定 |
* **Material** 的顏色透過佈景主題疊加實作（`android:colorPrimary` / `colorPrimaryDark` / `colorAccent` / `colorControlActivated`），定義在 `res/values-v21/color_overlays.xml`，由 `AppColors.applyTheme()` 在 `setTheme()` 之後、`super.onCreate()` 之前疊加到佈景主題上。這正是 Material 自身定義這些顏色的方式，因此所有元件都會跟著變化。
* **Material** 的**強調色**會跟著配色配置變化，和佈景主題自身的強調色完全一樣：淺色配置用 500 原色，深色配置改用 **200 淺色調**（Material 自己就是這麼做的——它的強調色在淺色下是 deep teal 500 `#009688`，在深色下是 deep teal 200 `#80CBC4`）。**主色只在淺色配置下生效**：深色配置下操作列與狀態列始終使用佈景主題自帶的深色（Material 自己的深色佈景主題就是這樣，Material Design 也建議深色配置不要用主色鋪滿操作列），因此即使選了主色，深色下的操作列也不會變色。主色列仍然可以正常選擇，選擇會被記住，切回淺色配置時生效。強調色列比主色列多一個 **黑 / 白** 選項（排在綠色之前）：淺色配置下是黑色，深色配置下是白色，因此在兩種配置下都看得見。淺色配置保持原來的 500 值，因此外觀與以前完全一致。選擇以「當時螢幕上顯示的顏色」儲存，所以同一個選擇在兩種配置下分別顯示為 500 與 200，`AppColors.indexOfColor()` 會在兩份調色盤中查找它。
* **Holo** 沒有強調色屬性，框架元件也不會讀取它，因此強調色只能逐個檢視施加：核取方塊與選項按鈕用 `setButtonTintList()`、進度列用 `setProgressTintList()`、對話方塊（含單選清單）裡的控件用同樣的方式，標題、標題下的分隔線、按鈕文字、連結文字和副標題下方的版本資訊行則直接改顏色（版本資訊行是唯一保持預設時也帶強調色的元素，此時它使用佈景主題自身的強調色，因此始終與核取方塊一致）。**操作列本身始終保持佈景主題的黑色**，只有深色配置下操作列底部那條 Holo 藍的細線會被覆蓋成強調色。Android 4.x 沒有 tint API，因此那裡只有文字會跟著強調色變化，選擇器下方會給出說明。
* Holo 的調色盤裡**沒有**佈景主題自身那種青色（`#0099CC`）：它就是預設項目，再提供一次會與預設項目完全一樣。Material 的強調色調色盤同理不含 teal（`#009688`）——那是 Material 自己用來畫核取方塊、選項按鈕、進度列的強調色，預設項目已經代表它了；強調色列因此比主色列多出一個 **黑 / 白** 選項、少一個 teal，共 10 種。主色列仍是 10 種，teal 作為主色依然是有效選擇。

佈景主題定義位於 `res/values/styles.xml`，Android 5.0+ 的覆蓋定義位於 `res/values-v21/styles.xml`。目前佈景主題在 `MainActivity.onCreate` 中、建立內容檢視之前透過 `setTheme()` 套用。某個佈景主題在目前裝置上是否可用由 `AppThemes.isAvailable()` 判斷，`get()` 在儲存的佈景主題不可用時回退為 Holo（例如把 5.0+ 裝置的備份還原到 4.x 裝置上時）。

切換佈景主題、配色配置或顏色時都會以**全新 Intent 重新啟動 Activity**（而不是呼叫 `recreate()`）：Holo 與 Material 使用不同的操作列實作，`recreate()` 會讓框架把一種操作列的儲存狀態還原到另一種上，從而拋出 `ClassCastException`（`ActionBarView$SavedState cannot be cast to Toolbar$SavedState`）並導致應用程式崩潰。

## 介面

主介面是**一整頁捲動頁面**：一列檢視中可伸縮的那個是憑證來源清單。`PageScrollView` 把頁面上剩下的空間交給清單——螢幕減去上方的標題、下方的進度列與狀態列，以及它們下面的按鈕——當剩餘空間小於清單要求的最小高度時，就按最小高度給它，此時捲動的是清單本身，周圍的一切都留在原位；只有剩餘空間小於該最小值時，頁面自己才會捲動，而且只捲動多出來的那一小段。`PageListView` 是實現這一切的關鍵：它報告的是所有列的高度，而不是框架在捲動頁面裡測量清單時得到的那一列的高度，並依一列的實際高度算出自己的最小值（兩列）。清單的捲軸會保持顯示，因此「某一列恰好結束在清單底邊」不會是「下面還有來源」的唯一提示。

頁面只在左右留有內距，垂直方向的留白由裡面的那一欄承擔。`ScrollView` 會把捲動範圍限制為「內容高度減去自身的內距」，因此頁面上的垂直內距是內容永遠捲不到的空間：捲動之後它會靜止在可捲動區域的最上方與最下方，內容從它後面滑過，看起來就像頁面在兩端都短了一截。

頂欄是**佈景主題**按鈕，其後的選單依次是語言、專案網站、GitHub、關於，以及*自動確認安裝*（Android 4.1 起提供，因為 4.0 的無障礙框架無法點擊按鈕）。

## 在地化

介面提供六種語言：**简体中文**、**繁體中文**、**English**、**Polski**、**Русский**、**Svenska**。

* 應用程式**預設跟隨系統語言**：Android 會自動選擇相符的 `res/values-*` 目錄；如果裝置語言不在這六種之內，則回退到 `res/values`（英文）。對應關係：`zh-CN` → `res/values-zh-rCN`（簡體），`zh-TW` → `res/values-zh-rTW`（繁體），其他中文語言環境 → `res/values-zh`（簡體），英語 → `res/values`，`pl` → `res/values-pl`，`ru` → `res/values-ru`，`sv` → `res/values-sv`。
* 選單中的**語言**項允許使用者覆蓋這一行為，可選擇*跟隨系統*以及上述六種語言（順序固定為：简体中文、繁體中文、English、Polski、Русский、Svenska）。選擇會記錄在 `SharedPreferences` 中，並在 `MainActivity.onCreate` 中透過 `AppLanguage.apply()` 套用（在 API 14 - 23 上均可用的 `updateConfiguration` 方式），隨後重新啟動 Activity 使變更生效。
* **所有**介面文字都來自字串資源，包括憑證來源清單裡的供應商名稱與說明：`CertCatalog` 在建立目錄時用 `Context.getString()` 讀取它們。因此目錄必須在 `AppLanguage.apply()` 之後建立——放在欄位初始化器中會先於語言設定執行，從而使用系統語言而非使用者選擇的語言。

之所以使用 `values-zh-rCN` / `values-zh-rTW` / `values-zh` 限定符（而非 `b+zh+Hans` / `b+zh+Hant`），是因為 Android 5.0（API 21）之前不支援 BCP 47 文字（script）限定符。

README 本身也提供同樣六種語言，頂部切換器裡的順序與應用程式內一致：`README.md`（简体中文，預設）、`README_zh_TW.md`、`README_en_US.md`、`README_pl_PL.md`、`README_ru_RU.md`、`README_sv_SE.md`。

## 建置

需要：JDK 17（或 21）以及 Android SDK（build-tools 34、platform android-33）。

```bash
./gradlew assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
```

`gradlew` / `gradlew.bat` 會自己找合適的 JDK。Gradle 8.7 自帶的 Groovy 編譯器讀不了比它新的 JDK 的 class 檔案，只要建置腳本裡出現任何 JDK 型別（`buildFeatures { buildConfig true }` 裡那個 Boolean 就夠了），建置就會以 `Unsupported class file major version 70` 失敗——預設 `java` 是 JDK 26 的機器正是如此。因此腳本會依序尋找 17、21（macOS 的 `/usr/libexec/java_home`、Homebrew、Linux 發行版的 `/usr/lib/jvm`、Android Studio 自帶的 JBR、Windows 上各家的預設安裝目錄等常見位置），找到就把 `JAVA_HOME` 指過去；你自己設的 `JAVA_HOME` 只要已經是 17–21 就不動它。找不到時腳本會給出提示，此時請安裝 JDK 17 或把 `JAVA_HOME` 指向它。

這兩段程式碼（`gradlew` 裡的一段、`gradlew.bat` 開頭的一段）Gradle 的 `wrapper` 任務並不知道，每次重新產生 wrapper 都會把它們丟掉。丟了之後執行一次 `./build-prep.sh`（Windows 上用 `build-prep.bat`）即可補回來；`./build-prep.sh --check` 只檢查、不修改，適合放進 CI。兩段程式碼本身放在 `tools/gradlew-jdk17.sh` 與 `tools/gradlew-jdk17.bat`，兩個腳本共用同一份。

關於對話方塊（頂欄選單 → 關於）在正文下方列出**版本名稱與版本號、套件名稱，以及建置時間和建置型別**（debug / release），方便在問題回報裡指明是哪一個建置。建置時間由 `build.gradle` 在設定階段寫進 `BuildConfig.BUILD_DATE`，因此記錄的是建置時刻而不是安裝時刻；代價是同一個提交每次建置出來的 APK 都不完全相同。

## 專案結構

```
app/src/main/java/cc/wuersan008/zhusheng/
  MainActivity.java     介面 + 下載/安裝流程編排
  CertCatalog.java      官方憑證來源清單
  KnownRoots.java       兩個憑證包預期包含的根憑證指紋
  CertSource.java       單一來源項目
  Downloader.java       下載（https 到純 http 的回退鏈）
  CertParser.java       PEM / DER 解析（單張憑證與憑證包）
  HashUtils.java        PEM 編碼、SHA-256、subject_hash_old
  CertInstaller.java    系統憑證儲存區（root）+ 使用者憑證儲存區安裝與移除
  ScriptNotes.java      解釋 root 指令碼輸出中的標記
  TrustedRoots.java     裝置已信任的憑證（AndroidCAStore）
  UserInstallMode.java  使用者憑證儲存區安裝的三種方式 + 使用者的選擇
  UserRemoveMode.java   使用者憑證儲存區移除的三種方式 + 使用者的選擇
  InstallerAutoConfirm.java  安裝進行中 / 服務已啟用的旗標
  RemovalAutoConfirm.java    移除進行中的旗標 + 要相符的憑證名稱
  AutoConfirmService.java    無障礙服務：安裝對話方塊與設定中的憑證清單
  RootUtils.java        su shell 輔助方法
  SourceAdapter.java    ListView 配接器
  PageListView.java     憑證來源清單：為捲動頁面測量自身高度
  PageScrollView.java   頁面：給清單的空間、頁面捲動與拖曳分流
  LegacyNotice.java     每台裝置只提示一次的 Android 7.0+ 說明
  AppThemes.java        佈景主題選擇與持久化
  AppColors.java        佈景主題顏色：按檢視或按佈景主題疊加套用
  AppLanguage.java      語言選擇與持久化
  LaoLuo.java           錘子（Smartisan）裝置識別（預設佈景主題）
  ZhushengApp.java      Application（擷取真實的系統語言）
```

## 限制

* 寫入 `/system/etc/security/cacerts` 需要 root。沒有 root 時請使用使用者憑證儲存區方式。
* 從系統憑證儲存區移除同樣需要 root。在使用者憑證儲存區中，三種方式裡只有一種需要：它因為該儲存區沒有移除憑證的 API 而直接讀取其檔案。另外兩種完全不需要 root——手動走一遍設定中的憑證清單，以及讓自動確認服務代勞。後者驅動的是所執行版本自己的設定介面，因此介面配置不同的裝置請改用手動方式。
* 自動確認服務需要 Android 4.1 或更新版本；在 Android 4.0 上，使用者憑證儲存區安裝的每次提示都需手動確認。
* 對憑證進行固定（pinning）或自帶信任儲存區的應用程式不受影響。

## 授權條款

本專案以 [Unlicense](UNLICENSE) 發佈，即放棄著作權、進入公有領域（public domain）。你可以自由複製、修改、發佈、使用、編譯、販售或散佈本專案，無論是原始碼形式或編譯後的二進位形式，用於任何目的（商業或非商業），採用任何方式，均無需署名。

軟體按「原樣」提供，不附帶任何形式的擔保。完整條款見 [UNLICENSE](UNLICENSE) 檔案。
