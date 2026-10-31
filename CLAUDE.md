# Carnet

## 本地编译

- 云端会话启动时 `.claude/hooks/session-start.sh` 会在后台装好 Android SDK，推送前先在本地跑 `LANG=C.UTF-8 ./gradlew assembleDebug testDebugUnitTest`（测试名有中文，需要 UTF-8 locale）；CI 打的是 release 包，会多跑 `lintVitalRelease`，动了依赖或 Activity 时本地也跑一下。
- 刚开会话时如果报找不到 SDK 或 `local.properties`，说明后台安装还没跑完，等一会再试。

## 记账的约定

- 金额一律用「分」存成 `Long`，换算和分摊都在 `domain/` 里，用 `BigDecimal` 四舍五入；改算法先改 `app/src/test` 里的测试。
- 汇率统一写成「1 新币 = 多少人民币」（`FxRate.cnyPerSgd`），默认汇率由用户自己设，每一笔可以单独改；记下的账存当时用的汇率，以后改默认汇率不影响历史账。
- 账户（钱在哪）和账本（算谁的、要不要 AA）是两回事：`Entry.accountCents` 是账户上动了多少（账户币种），`Entry.ledgerCents` 是记进账本多少（账本货币，混币账本按主货币新币）。
- 谁多垫了钱的说法用「垫 / 补 / 两清」，不说「欠」。
- 改表要升数据库版本并手写 `Migration`，SQL 照抄 `app/schemas/` 里 Room 导出的 `createSql`，再在 `MigrationTest` 里加一条对照；不要用 destructive migration，会把账清空。
- 应用内更新读 `zhaidy/Carnet` 的 GitHub Releases（`build-N` 标签，N 就是 versionCode），只有配了签名密钥的 `main` 构建才会发 Release。

## 和她同步

- 共用账本走 Firebase（和 Mise 共用一个项目）：`books/{邀请码}`、`entries/{syncId}`、`settlements/{syncId}`，见 `sync/BookSync.kt`。账户信息不上传；「谁付的」按记账人存（`paidByAuthor`），各自换成我 / 她。
- `app/google-services.json` 不进仓库：CI 从 Secret `GOOGLE_SERVICES_JSON` 写入，没配置时 `scripts/write-google-services.sh` 写占位文件，能编译但同步不可用。
- Firestore 规则只有一份，`firestore.rules` 里连 Mise 的 kitchens 部分一起写着；改了要整份贴到 Firebase 控制台。

## Pull request 流程

- 推送分支后创建 PR，CI（`build`）通过后自动合并，不需要再问。
- 优先开 GitHub 自动合并（squash）；PR 已经是 clean 状态、开不了自动合并时，直接 squash 合并。
- CI 失败先修，修好再合；不要跳过、禁用测试来让 CI 变绿。
- 合并后如果还有后续改动，从最新的 `main` 重新拉分支，不要在已合并的分支上继续提交。
