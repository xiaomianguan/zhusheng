# zhusheng

![slop badge](.github/assets/slop_badge.webp)

简体中文 | [繁體中文](README_zh_TW.md) | [English](README_en_US.md) | [Polski](README_pl_PL.md) | [Русский](README_ru_RU.md) | [Svenska](README_sv_SE.md)

**zhusheng** 通过下载各大 TLS/SSL 提供商官方发布的根证书并安装到 Android 证书库中，为较旧的 Android 设备（**Android 4.0 - 6.0**，API 14 - 23）恢复系统范围的 TLS/HTTPS 信任。

旧设备出厂时内置的受信任根证书往往已经过时。当某个网站使用设备出厂时尚不存在（或尚未受信任）的 CA 签发证书时——最典型的就是 **Let's Encrypt**——所有 HTTPS 请求都会因证书错误而失败，设备便“无法正常上网”。zhusheng 通过安装缺失的根证书来解决这一问题。

* 适用于**所有架构**（应用为纯 Java 实现，不含原生代码）。
* 支持 **Android 4.0 至 6.0**。
* 仅使用**原生 Android UI 组件**——不使用 AndroidX、不引入任何第三方库。

## 工作原理

1. **下载。** 选择你需要的证书颁发机构（或保留推荐集合），然后点击*下载所选项*。证书会从下方列出的官方 CA 端点获取。
2. **安装。** 提供两种方式：
   * **系统证书库（需 root）**——证书以 PEM 文件的形式写入 `/system/etc/security/cacerts/<subject_hash>.<n>`，与预装的根证书完全一致，随后将 `/system` 重新挂载为只读。重启后生效。这样所有应用都会信任这些证书。
   * **用户证书库（无需 root）**——证书通过 Android 证书安装程序安装（`KeyChain.createInstallIntent`）。在 **Android 7.0 以下**的版本中，用户证书库中的证书在系统范围内受信任，因此无需 root 也能为所有应用修复 HTTPS。点击按钮时会先询问这些确认该如何进行，并列出三种做法（`UserInstallMode`，选择会被记住，下次打开时沿用）：
     * **整套只提示一次**——整批证书先以**一个** intent 交给安装程序：一个凭据包（credentials bundle），每张证书对应一个 `CACERT_<n>` 条目（`CertInstaller.userStoreBulkInstallIntent`）。支持的安装程序会一次加入全部根证书；部分 4.x 机型会读取该凭据包却静默忽略，此时应用会退回到每张证书一个对话框；
     * **每张证书提示一次**——完全不尝试凭据包，安装程序一次只会被问到一张证书。比第一种慢，但在任何安装程序上表现一致；
     * **自动回答这些提示**——与第一种相同的流程，但由**自动确认服务**来回答这些提示。

     设备**已经信任的证书会被跳过**，各来源之间的重复证书也会被去掉（`TrustedRoots`），因此再次安装——或继续一次被中断的安装——已无需再确认任何内容。

     **自动确认服务**是可选的，需要 **Android 4.1 或更高版本**。应用无权自行开启自己的辅助功能服务，只能由用户在系统辅助功能设置中开启——因此应用会在打开该页面之前先说明这个服务是什么，并给出要寻找的条目名称：菜单中的*自动确认安装*会这样做，上面的自动方式在服务尚未开启时也会这样做（此时用户返回后安装会自动继续）。说明的最后还会提示：服务的工作方式是快速点过一个个界面，因此运行期间屏幕可能会闪烁；对闪烁敏感的用户可以改用两种手动确认的方式。在本应用发起的一次操作进行期间，该服务会点击安装程序对话框的*确定*按钮，移除时还会点选证书列表的行；除此之外它什么都不做，而两种手动确认的方式也从不要求它启动。在 Android 4.0 上辅助功能框架根本无法点击按钮（`performAction(ACTION_CLICK)` 随 4.1 的 `View.performAccessibilityAction` 才出现），因此 4.0 设备仍需手动确认。

3. **移除。** 两个证书库都可以再次清空，因此一次安装无需第二台设备或恢复出厂设置就能撤销：
   * **系统证书库（需 root）**——只删除本应用加入的文件。应用会记录自己复制的每一个文件名（私有 files 目录中的 `system-installed.txt`），并在删除前逐一核对文件内容是否就是它应该保存的那张证书：设备预装的根证书与某个证书包中的同一张根证书可能是完全相同的字节，仅凭证书库无法区分，而预装的根证书绝不能被删除。
   * **用户证书库**——用户证书库没有移除证书的 API，因此 **3b. 从用户证书库移除** 会像 2b 询问安装提示那样，先询问该走三种方式中的哪一种（`UserRemoveMode`，选择同样会被记住，下次沿用）：
     * **用 root 直接删除文件**——直接读取 `/data/misc/user/0/cacerts-added`（较旧版本为 `/data/misc/keychain/cacerts-added`）下的文件；只有当文件内容确实是当前载入的某张证书时才会删除。这是唯一需要 root 的方式，在没有 root 的设备上它会显示为灰色并注明原因，因此无法选中。
     * **在设置中手动移除**——打开系统设置中的证书列表，由用户逐一移除：点选该行、按下*移除*、确认。应用不会删除任何东西，也无需 root。对话框会列出具体步骤并提供打开该界面的入口；该说明的截图之后补上。
     * **自动移除（无障碍服务）**——打开的是同一份列表，改由自动确认服务代劳（见下）。

   会真正删除内容的方式——3a 与 3b 中的 root 方式——都会先请求确认，且都需要 root：用户证书库需要 root 是因为没有 API，而不是因为它的写入方式。

   第三种方式把移除交给**自动确认服务**：应用打开同一个证书列表，把当前载入证书的名称交给服务，由服务点选每一行、按下详情页上的*移除*，并确认随之而来的询问。它只会点选名称与这些证书相符的行，并且在应用回到前台时立即停止——此后这份列表重新属于用户。它同样需要开启该服务，并且走的是所运行版本自己的设置界面；在 Android 5.0 与 6.0 上，依次是一次点行、详情下方的*移除*按钮和一次*是*。未开启该服务时，应用会先说明它并提供打开该页面的入口，返回后再一次询问是否移除。

   每个 root 脚本的运行——两个证书库的安装与移除（2a、3a，以及 3b 的 root 方式）——最后都会显示一个对话框，把脚本的输出**原样**引用出来（这才是问题报告应该包含的内容），并在下方用应用当前的语言解释它认得出来的标记（`ScriptNotes`）：`ZHUSHENG_COPIED=`、`ZHUSHENG_REMOVED=`、`ZHUSHENG_DEDUPED=`、`ZHUSHENG_DONE`，以及 `no system-installed.txt`、`could not write`、`command timed out`、`exec failed` 这几种行。解释按标记在输出中出现的顺序排列，同一个标记出现两次只解释一次，而应用不认识的输出同样原样引用。输出完全为空时则改为给出此时唯一重要的解释：脚本什么都没有打印——这正是 root 请求被拒绝时的样子。（没有 root 的设备根本走不到这个对话框：2a 与 3a 会提示缺少 root，3b 的 root 方式则是置灰的。）

在 **Android 7.0（API 24）及以上**，系统不再信任用户证书库中由用户安装的证书（除非应用自行声明信任），因此*用户证书库*模式对大多数应用不再有效；*系统证书库*模式也许仍然可用，但需要 root，而且高于 6.0 的版本不在 zhusheng 的官方支持范围内（更高版本的 Android 又进一步改动了系统证书库）。应用会在**首次启动**时就此给出一次提示：`LegacyNotice` 在 `onCreate()` 末尾调用，标志位 `legacy_notice_shown` 记录在 `SharedPreferences` 中，因此每台设备只提示一次。

系统证书库的文件名使用 OpenSSL 的 `x509 -subject_hash_old` 值（对 DER 编码的 subject 求 MD5）生成，应用在 Java 中复现了这一算法（`HashUtils.subjectHashOld`），并已验证与 OpenSSL 逐字节一致。

## 官方证书来源

| 提供商 | URL |
| --- | --- |
| Mozilla CA 根证书包（涵盖所有主要 CA） | `https://curl.se/ca/cacert.pem` |
| Google Trust Services（根证书包） | `https://pki.goog/roots.pem` |
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

**Mozilla CA 根证书包**是万能的兜底方案：它包含所有主要 CA 的根证书（包括 Certum、SSL.com、IdenTrust、Buypass、D-TRUST、QuoVadis、Actalis、HARICA、T-TeleSec、Verisign/Symantec 等众多 CA），因此只需下载这一个文件即可修复绝大多数无法访问的网站。

### 下载的安全性

由于设备上损坏的正是它的信任库，对这些端点使用完全校验的 HTTPS 连接可能会失败（例如 `letsencrypt.org` 就是由 Let's Encrypt 证书提供服务的）。而 Android 4.0 设备根本无法完成这些主机要求的 TLS 握手——这正是本应用要解决的场景——因此下载会逐级回退：

1. 先尝试**正常校验**的 HTTPS 下载，仅当失败时才
2. 重试并接受任意服务器证书，
3. 再用**普通 HTTP** 重试同一地址，
4. 最后回退到该来源附带的**纯 HTTP 副本**（官方主机只提供 https 时使用；即官方文件的固定 Internet Archive 快照）。

真正保证安全的并不是传输方式，而是下载完成后的逐张校验：

* 每张证书都必须是**自签名的根证书**（subject == issuer，且签名能用其自身公钥验证通过），
* 单张根证书来源会与**固定的 SHA-256 指纹**进行比对，
* 证书包则会与**预期包含的每一张根证书指纹**（`KnownRoots`）逐一比对，因此通过纯 HTTP 下载的内容也无法夹带自己的根证书。

因此，即使下载内容被篡改，也无法在用户不知情的情况下装入伪造的根证书。

## 主题

应用提供三种可选主题和两种配色方案，通过顶栏的**主题**按钮选择；选择会记录在 `SharedPreferences` 中。**Android 5.0 及以上默认使用 Material**（更贴合系统外观），Android 4.x 默认使用 Holo；锤子（Smartisan）设备则默认使用**设备默认**主题（`LaoLuo`），让应用在它为之打造的 ROM 上呈现厂商自己的外观。用户只要明确选过一次主题，该选择就始终优先。

| 选项 | API 14 - 20（4.0 - 4.4W） | API 21+（5.0 - 6.0） |
| --- | --- | --- |
| **Holo** | `Theme.Holo.Light.DarkActionBar` | `Theme.Holo.Light.DarkActionBar` |
| **Material** | 不可用（选项置灰） | `Theme.Material.Light.DarkActionBar` |
| **设备默认** | `Theme.DeviceDefault.Light.DarkActionBar` | `Theme.DeviceDefault.Light.DarkActionBar` |

* **Holo** 是原始主题，在所有受支持的版本上都可用，但只在 Android 4.x 上作为默认主题。
* **Material** 使用框架的 Material Design 1 组件，因此仅 Android 5.0+ 可用，并且**是 5.0+ 上的默认主题**。在 Android 4.x 上该选项会被**置灰**，并在下方提示 *Not available on Android 4.x*，而不是悄悄回退成 Holo。
* **设备默认** 通过 `Theme.DeviceDefault` 使用设备厂商提供的主题（TouchWiz、Sense、MIUI 等）。

### 配色方案

每个主题都有**浅色**和**深色**两种方案：

* **浅色**（默认）使用主题的 `DarkActionBar` 变体，即浅色内容区 + 深色操作栏，也就是应用原本的外观。
* **深色**使用同一主题的完全深色变体（`Theme.Holo`、`Theme.Material`、`Theme.DeviceDefault`）。

应用自身界面的颜色（背景、正文、次要文字、列表分隔线）声明为主题属性 `zsBackground` / `zsTextPrimary` / `zsTextSecondary` / `zsDivider`（见 `res/values/attrs.xml`），因此深色方案下内容区不会出现“白底黑字”的割裂感。六个基础主题设置浅色值，深色方案通过 `ThemeOverlay.Zhusheng.Scheme.Dark` 在 `Theme.applyStyle()` 中叠加覆盖。

### 颜色

主题对话框还提供颜色选择器。默认项（第一个色块）表示**保持主题自带的颜色**，因此完全不做选择时外观与原来一模一样；Holo 的默认色块显示的就是主题自身使用的青色。选中的色块中央画一个对勾：浅色方案下为白色，深色方案下为黑色（深色方案用的是 200 浅色调，白色对勾在上面几乎看不见）；唯一的例外是第一个「默认」色块，它的颜色取决于主题自身，因此对勾按亮度选黑或白。副标题下方那行版本信息（`device_info`）是应用自己唯一的强调色元素：不做选择时它采用主题自身的强调色，因此不会与旁边的复选框颜色冲突。

| 主题 | 可调颜色 | 作用范围 |
| --- | --- | --- |
| **Holo** | 强调色（4 种 Holo 色） | 主题本来画成青色的那些元素：列表复选框、对话框里的单选按钮、进度条、链接文字、对话框标题与标题下的分隔线、对话框按钮、深色方案下操作栏底部的那条青线，以及副标题下方的版本信息行（后两者中依赖逐视图 tint 的部分需要 API 21+） |
| **Material** | 主色（10 种 Material 色）+ 强调色（10 种） | 主色决定**浅色方案**下的操作栏与状态栏（深色方案始终使用主题自带的深色操作栏与状态栏），强调色决定复选框、进度条、对话框按钮、副标题下方的版本信息行等 |
| **设备默认** | 无 | 颜色由设备厂商的主题决定 |

* **Material** 的颜色通过主题叠加实现（`android:colorPrimary` / `colorPrimaryDark` / `colorAccent` / `colorControlActivated`），定义在 `res/values-v21/color_overlays.xml`，由 `AppColors.applyTheme()` 在 `setTheme()` 之后、`super.onCreate()` 之前叠加到主题上。这正是 Material 自身定义这些颜色的方式，因此所有组件都会跟着变化。
* **Material** 的**强调色**会跟着配色方案变化，和主题自身的强调色完全一样：浅色方案用 500 原色，深色方案改用 **200 浅色调**（Material 自己就是这么做的——它的强调色在浅色下是 deep teal 500 `#009688`，在深色下是 deep teal 200 `#80CBC4`）。**主色只在浅色方案下生效**：深色方案下操作栏与状态栏始终使用主题自带的深色（Material 自己的深色主题就是这样，Material Design 也建议深色方案不要用主色铺满操作栏），因此即使选了主色，深色下的操作栏也不会变色。主色行仍然可以正常选择，选择会被记住，切回浅色方案时生效。强调色行比主色行多一个 **黑 / 白** 选项（排在绿色之前）：浅色方案下是黑色，深色方案下是白色，因此在两种方案下都看得见。浅色方案保持原来的 500 值，因此外观与以前完全一致。选择以「当时屏幕上显示的颜色」保存，所以同一个选择在两种方案下分别显示为 500 与 200，`AppColors.indexOfColor()` 会在两份调色板中查找它。
* **Holo** 没有强调色属性，框架组件也不会读取它，因此强调色只能逐个视图施加：复选框与单选按钮用 `setButtonTintList()`、进度条用 `setProgressTintList()`、对话框（含单选列表）里的控件用同样的方式，标题、标题下的分隔线、按钮文字、链接文字和副标题下方的版本信息行则直接改颜色（版本信息行是唯一保持默认时也带强调色的元素，此时它使用主题自身的强调色，因此始终与复选框一致）。**操作栏本身始终保持主题的黑色**，只有深色方案下操作栏底部那条 Holo 蓝的细线会被覆盖成强调色。Android 4.x 没有 tint API，因此那里只有文字会跟着强调色变化，选择器下方会给出说明。
* Holo 的调色板里**没有**主题自身那种青色（`#0099CC`）：它就是默认项，再提供一次会与默认项完全一样。Material 的强调色调色板同理不含 teal（`#009688`）——那是 Material 自己用来画复选框、单选按钮、进度条的强调色，默认项已经代表它了；强调色行因此比主色行多出一个 **黑 / 白** 选项、少一个 teal，共 10 种。主色行仍是 10 种，teal 作为主色依然是有效选择。

主题定义位于 `res/values/styles.xml`，Android 5.0+ 的覆盖定义位于 `res/values-v21/styles.xml`。当前主题在 `MainActivity.onCreate` 中、创建内容视图之前通过 `setTheme()` 应用。某个主题在当前设备上是否可用由 `AppThemes.isAvailable()` 判断，`get()` 在保存的主题不可用时回退为 Holo（例如把 5.0+ 设备的备份恢复到 4.x 设备上时）。

切换主题、配色方案或颜色时都会以**全新 Intent 重启 Activity**（而不是调用 `recreate()`）：Holo 与 Material 使用不同的操作栏实现，`recreate()` 会让框架把一种操作栏的保存状态恢复到另一种上，从而抛出 `ClassCastException`（`ActionBarView$SavedState cannot be cast to Toolbar$SavedState`）并导致应用崩溃。

## 界面

主界面是**一整页滚动页面**：一列视图中可伸缩的那个是证书来源列表。`PageScrollView` 把页面上剩下的空间交给列表——屏幕减去上方的标题、下方的进度条与状态行，以及它们下面的按钮——当剩余空间小于列表要求的最小高度时，就按最小高度给它，此时滚动的是列表本身，周围的一切都留在原位；只有剩余空间小于该最小值时，页面自己才滚动，而且只滚动多出来的那一小段。`PageListView` 是实现这一切的关键：它报告的是所有行的高度，而不是框架在滚动页面里测量列表时得到的那一行的高度，并根据一行的实际高度算出自己的最小值（两行）。列表的滚动条会保持显示，因此「某一行恰好结束在列表底边」不会是「下面还有来源」的唯一提示。

页面只在左右留有内边距，垂直方向的留白由里面的那列承担。`ScrollView` 会把滚动范围限制为「内容高度减去自身的内边距」，因此页面上的垂直内边距是内容永远滚不到的空间：滚动之后它会静止在可滚动区域的最上方与最下方，内容从它后面滑过，看起来就像页面在两端都短了一截。

顶栏是**主题**按钮，其后的菜单依次是语言、项目网站、GitHub、关于，以及*自动确认安装*（Android 4.1 起提供，因为 4.0 的辅助功能框架无法点击按钮）。

## 本地化

界面提供六种语言：**简体中文**、**繁體中文**、**English**、**Polski**、**Русский**、**Svenska**。

* 应用**默认跟随系统语言**：Android 会自动选择匹配的 `res/values-*` 目录；如果设备语言不在这六种之内，则回退到 `res/values`（英文）。对应关系：`zh-CN` → `res/values-zh-rCN`（简体），`zh-TW` → `res/values-zh-rTW`（繁体），其他中文语言环境 → `res/values-zh`（简体），英语 → `res/values`，`pl` → `res/values-pl`，`ru` → `res/values-ru`，`sv` → `res/values-sv`。
* 菜单中的**语言**项允许用户覆盖这一行为，可选择*跟随系统*以及上述六种语言（顺序固定为：简体中文、繁體中文、English、Polski、Русский、Svenska）。选择会记录在 `SharedPreferences` 中，并在 `MainActivity.onCreate` 中通过 `AppLanguage.apply()` 应用（在 API 14 - 23 上均可用的 `updateConfiguration` 方式），随后重启 Activity 使更改生效。
* **所有**界面文字都来自字符串资源，包括证书来源列表里的提供商名称与说明：`CertCatalog` 在构建目录时用 `Context.getString()` 读取它们。因此目录必须在 `AppLanguage.apply()` 之后构建——放在字段初始化器中会先于语言设置执行，从而使用系统语言而非用户选择的语言。

之所以使用 `values-zh-rCN` / `values-zh-rTW` / `values-zh` 限定符（而非 `b+zh+Hans` / `b+zh+Hant`），是因为 Android 5.0（API 21）之前不支持 BCP 47 文字（script）限定符。

README 本身也提供同样六种语言，顶部切换器里的顺序与应用内一致：`README.md`（简体中文，默认）、`README_zh_TW.md`、`README_en_US.md`、`README_pl_PL.md`、`README_ru_RU.md`、`README_sv_SE.md`。

## 构建

需要：JDK 17（或 21）以及 Android SDK（build-tools 34、platform android-33）。

```bash
./gradlew assembleDebug      # -> app/build/outputs/apk/debug/app-debug.apk
```

`gradlew` / `gradlew.bat` 会自己找合适的 JDK。Gradle 8.7 自带的 Groovy 编译器读不了比它新的 JDK 的 class 文件，只要构建脚本里出现任何 JDK 类型（`buildFeatures { buildConfig true }` 里那个 Boolean 就够了），构建就会以 `Unsupported class file major version 70` 失败——默认 `java` 是 JDK 26 的机器正是如此。因此脚本会依次寻找 17、21（macOS 的 `/usr/libexec/java_home`、Homebrew、Linux 发行版的 `/usr/lib/jvm`、Android Studio 自带的 JBR、Windows 上各家的默认安装目录等常见位置），找到就把 `JAVA_HOME` 指过去；你自己设的 `JAVA_HOME` 只要已经是 17–21 就不动它。找不到时脚本会给出提示，此时请安装 JDK 17 或把 `JAVA_HOME` 指向它。

这两段代码（`gradlew` 里的一段、`gradlew.bat` 开头的一段）Gradle 的 `wrapper` 任务并不知道，每次重新生成 wrapper 都会把它们丢掉。丢了之后运行一次 `./build-prep.sh`（Windows 上用 `build-prep.bat`）即可补回来；`./build-prep.sh --check` 只检查、不修改，适合放进 CI。两段代码本身放在 `tools/gradlew-jdk17.sh` 与 `tools/gradlew-jdk17.bat`，两个脚本共用同一份。

关于对话框（顶栏菜单 → 关于）在正文下方列出版本名称与版本号、包名，以及构建时间和构建类型（debug / release），方便在问题报告里指明是哪一个构建。构建时间由 `build.gradle` 在配置阶段写进 `BuildConfig.BUILD_DATE`，因此记录的是构建时刻而不是安装时刻；代价是同一个提交每次构建出来的 APK 都不完全相同。

## 项目结构

```
app/src/main/java/cc/wuersan008/zhusheng/
  MainActivity.java     界面 + 下载/安装流程编排
  CertCatalog.java      官方证书来源列表
  KnownRoots.java       两个证书包预期包含的根证书指纹
  CertSource.java       单个来源条目
  Downloader.java       下载（https 到纯 http 的回退链）
  CertParser.java       PEM / DER 解析（单张证书与证书包）
  HashUtils.java        PEM 编码、SHA-256、subject_hash_old
  CertInstaller.java    系统证书库（root）+ 用户证书库安装与移除
  ScriptNotes.java      解释 root 脚本输出中的标记
  TrustedRoots.java     设备已信任的证书（AndroidCAStore）
  UserInstallMode.java  用户证书库安装的三种方式 + 用户的选择
  UserRemoveMode.java   用户证书库移除的三种方式 + 用户的选择
  InstallerAutoConfirm.java  安装进行中 / 服务已启用的标志位
  RemovalAutoConfirm.java    移除进行中的标志位 + 要匹配的证书名称
  AutoConfirmService.java    辅助功能服务：安装对话框与设置中的证书列表
  RootUtils.java        su shell 辅助方法
  SourceAdapter.java    ListView 适配器
  PageListView.java     来源列表：为滚动页面测量自身高度
  PageScrollView.java   页面：给列表的空间、页面滚动与拖动分流
  LegacyNotice.java     每台设备只提示一次的 Android 7.0+ 说明
  AppThemes.java        主题选择与持久化
  AppColors.java        主题颜色：按视图或按主题叠加应用
  AppLanguage.java      语言选择与持久化
  LaoLuo.java           锤子（Smartisan）设备识别（默认主题）
  ZhushengApp.java      Application（捕获真实的系统语言）
```

## 限制

* 写入 `/system/etc/security/cacerts` 需要 root。没有 root 时请使用用户证书库方式。
* 从系统证书库移除同样需要 root。在用户证书库中，三种方式里只有一种需要：它因为该证书库没有移除证书的 API 而直接读取其文件。另外两种完全不需要 root——手动走一遍设置中的证书列表，以及让自动确认服务代劳。后者驱动的是所运行版本自己的设置界面，因此界面布局不同的设备请改用手动方式。
* 自动确认服务需要 Android 4.1 或更高版本；在 Android 4.0 上，用户证书库安装的每次提示都需手动确认。
* 对证书进行固定（pinning）或自带信任库的应用不受影响。

## 许可证

本项目以 [Unlicense](UNLICENSE) 发布，即放弃版权、进入公有领域（public domain）。你可以自由复制、修改、发布、使用、编译、出售或分发本项目，无论是源代码形式还是编译后的二进制形式，用于任何目的（商业或非商业），采用任何方式，均无需署名。

软件按“原样”提供，不附带任何形式的担保。完整条款见 [UNLICENSE](UNLICENSE) 文件。
