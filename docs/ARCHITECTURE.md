# アーキテクチャ

今後の拡張性を考えて、マルチモジュール構成にする。構成は [Now in Android](https://github.com/android/nowinandroid)（以下 NiA）を参考にし、このアプリに必要なものだけを取り入れる。

## モジュール構成

| モジュール | 種類 | 役割 |
|---|---|---|
| `:app` | Android アプリ | `MainActivity`、テーマの適用、Navigation 3 の `NavDisplay` による画面の組み立て（バックスタックの管理）、Hilt の起点 |
| `:build-logic:convention` | Gradle の共通設定 | Android ライブラリ、Compose、機能モジュール、Hilt などの共通設定 |
| `:core:model` | Kotlin のみ | `User`、`UserDetail`、`Repo` などのデータ型と、エラーの型 `GitHubException`。Android に依存しない |
| `:core:network` | Android ライブラリ | Retrofit、OkHttp、kotlinx.serialization、API のレスポンス型、HTTP キャッシュ、レート制限ヘッダーの読み取り |
| `:core:data` | Android ライブラリ | Repository の定義と実装、API のレスポンス型からデータ型への変換、エラー型への変換 |
| `:core:designsystem` | Android ライブラリ（Compose） | M3 テーマ（ライト／ダーク）、共通部品（検索欄、スケルトン、状態表示など） |
| `:core:ui` | Android ライブラリ（Compose） | データ型を使う共通 UI（頭文字入りの丸に切り替わるアバター、数値・日付の表示、エラーメッセージ、Custom Tabs で開く処理など） |
| `:core:datastore` | Android ライブラリ | 暗号化したサインイン情報（アクセストークンなど）の保存。DataStore と Tink を使う |
| `:core:ai` | Android ライブラリ | 端末上の AI（ML Kit の Prompt API、Gemini Nano）による活動の要約。指示の組み立て、出力の確認、デモ用の実装 |
| `:core:testing` | Android ライブラリ | テスト用の偽の Repository、テスト用のデータ、`MainDispatcherRule` など |
| `:feature:userlist` | 機能モジュール | ユーザー一覧（リストペイン）の画面と ViewModel、遷移先の型 `UserListNavKey` と画面の登録処理 |
| `:feature:userdetail` | 機能モジュール | ユーザー詳細（詳細ペイン）の画面と ViewModel、遷移先の型 `UserDetailNavKey` と画面の登録処理 |
| `:feature:signin` | 機能モジュール | Device Flow のサインイン画面と ViewModel、遷移先の型 `SignInNavKey` と画面の登録処理 |

### 依存の向き

```
app ─▶ feature:* ─▶ core:data ─▶ core:network ─▶ core:model
            │              ├──────▶ core:datastore
            │              └──────▶ core:ai
            └──────▶ core:ui ─▶ core:designsystem
```

- 機能モジュールどうしは依存しない。一覧・詳細・サインインをつなぐのは `:app` の役割
- `:core:model` は Android に依存しない Kotlin だけのモジュールにする
- 機能モジュールは API のレスポンス型を直接使わない。`:core:data` が返すデータ型だけを使う

### NiA から取り入れないもの

| NiA のモジュール | 理由 |
|---|---|
| `core:database` | オフライン保存はしない（下の「キャッシュ」を参照） |
| `core:domain` | 今は UseCase に分けるほどの処理がない |
| `core:analytics`、`core:notifications` | このアプリでは使わない |
| `core:navigation`、機能の `api` / `impl` 分割 | 機能どうしが直接遷移しないので、今は不要（下の「決定事項」を参照） |
| `benchmarks`、`app-nia-catalog`、`core:screenshot-testing` | 後から追加できる |

## 決定事項

### 画面の組み立て：Navigation 3 と `ListDetailSceneStrategy` を使う

- 当初は `NavigableListDetailPaneScaffold` を使う予定だった。Navigation 3 の2ペイン用ライブラリ（`adaptive-navigation3`）が alpha 版だったためである。安定版（1.3.0、Compose BOM に含まれる）が出たので、NiA と同じ Navigation 3 に移行した
- `:app` がバックスタック（`rememberNavBackStack`）を持ち、`NavDisplay` に `ListDetailSceneStrategy` を渡す。スマホは1ペイン、横幅が Expanded 以上（タブレットや横向きのスマホ）は2ペインになる
- 各機能モジュールは、遷移先の型（`NavKey`）と、画面を登録する関数（`EntryProviderScope.userListEntry()` など）を公開する。機能どうしは依存せず、一覧で選ばれたユーザーを詳細に渡すのは `:app` の役割
- NiA のように機能を `api`（`NavKey`）と `impl`（画面）に分けるのは、ある機能から別の機能へ直接遷移するようになったときに行う。今は `:app` だけが両方を知っていればよいので分けない
- 画面ごとに ViewModel と `rememberSaveable` の状態を持つ（`rememberViewModelStoreNavEntryDecorator` と `rememberSaveableStateHolderNavEntryDecorator`）。詳細を閉じると、その ViewModel も破棄される
- 詳細は常に1つだけバックスタックに積む。別のユーザーを選んだら、前の詳細と入れ替える
- 戻る操作は `BackNavigationBehavior.PopUntilContentChange` にする。初期値（`PopUntilScaffoldValueChange`）だと、2ペインで詳細を表示中に戻る操作をしたとき、見た目のペイン構成が変わらないためアプリが閉じてしまう
- 詳細ペインのレイアウトは、2ペインかどうかに加えて実際の幅（640dp 以上）で決める。横向きのスマホでは2ペインになるが詳細ペインが狭いので、スマホ用のレイアウト（戻るボタンなし）を使う

### キャッシュ：OkHttp の HTTP キャッシュだけを使う

- 認証なしの GitHub API は 1 時間に 60 回までしか呼べない。詳細画面を 1 回開くだけで 2〜3 回使う
- OkHttp の `Cache` を使う。GitHub は `ETag` を返し、条件付きリクエストで 304 が返った場合は呼び出し回数に数えない
- Room による保存（オフラインでも使える作り）はしない。改善点として扱う

### 一覧の追加読み込み：自分で書く

- Paging 3 は使わない。最後に読み込んだユーザーの `id` を `since` に渡して次を読み込む処理を `:core:data` と ViewModel で書く
- 読み込み済みの一覧を手元で絞り込む仕様と合わせやすく、テストもしやすい

### デモ用のビルド：作らない

- NiA のような demo / prod の分割はしない
- `:core:testing` の偽の Repository を UI テストとプレビューで使う
- 例外として、端末上の AI だけは、debug ビルドでデモの実装に差し替えられるようにする（`githubusers.demoOnDeviceAi=true`）。エミュレーターには AICore がなく、要約の画面を確認できないため。release ビルドでは常に本物を使う（下の「活動の要約」を参照）

### パッケージ名と namespace：`com.example.githubusers`

- 各モジュールの namespace は `com.example.githubusers.core.network`、`com.example.githubusers.feature.userlist` のようにする

### 最低 Android バージョン：26（Android 8.0）

- `minSdk = 26`。ダイナミックカラーは Android 12 以降だけで使い、それより前は M3 の標準テーマにする

### 認証：GitHub の Device Flow

UI は [DESIGN.md の「3. サインイン」](design/DESIGN.md) を参照。サインインは任意で、しなくてもすべての機能を使える。

#### 方式
- 個人アクセストークン（PAT）をアプリに埋め込むと、APK から取り出されて誰でも使えてしまう。ユーザーに PAT を入力してもらうのは手間がかかる。そこで [Device Flow](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/authorizing-oauth-apps#device-flow) を使う
  - Device Flow は `client_secret` を使わないので、アプリに秘密情報を入れずに済む
  - アプリはパスワードを扱わない。ユーザーは GitHub のページ（Custom Tabs、または別の端末）でコードを入力する
- 流れ
  1. `POST https://github.com/login/device/code`（`client_id`）→ `device_code`、`user_code`、`verification_uri`、`expires_in`（既定 900 秒）、`interval`
  2. `POST https://github.com/login/oauth/access_token`（`client_id`、`device_code`、`grant_type=urn:ietf:params:oauth:grant-type:device_code`）を `interval` 秒ごとに呼ぶ
     - 失敗も HTTP 200 で返り、本文の `error` で区別する：`authorization_pending`（待つ）、`slow_down`（間隔を 5 秒延ばす。新しい間隔が本文に入る）、`expired_token`、`access_denied`、`device_flow_disabled`、`incorrect_client_credentials` など
  3. 取得したトークンで `GET /user` を呼び、ログイン名とアバターを保存する

#### アプリの種類：GitHub App
- GitHub は OAuth App より GitHub App を勧めている。GitHub App のユーザートークンは 8 時間で切れ、リフレッシュトークン（6 か月）で更新する。漏れたときの被害が小さい
- **Device Flow で取ったトークンは `client_secret` なしで更新できる**（[ドキュメント](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/refreshing-user-access-tokens)）。このため、アプリに秘密情報を入れずに期限付きトークンを使える
- 権限は何も要求しない（公開情報の読み取りだけ）。利用上限は 1 時間 5,000 回
- 実装は、`refresh_token` が返らない場合（期限なしのトークン）にも対応する。OAuth App に切り替えても設定の変更だけで動く
- GitHub 側の設定で「Enable Device Flow」を有効にする。無効なら `device_flow_disabled` が返る

#### client_id
- `client_id` は APK に含まれるので秘密ではない。`gradle.properties` の `githubusers.oauthClientId` に書いてコミットし、`local.properties` で上書きできるようにする（フォークした人が自分の App で試せるように）
- `:core:network` の `BuildConfig.GITHUB_CLIENT_ID` に入れる。空ならサインインの入口を出さない
- `client_secret` は使わないし、リポジトリにも入れない

#### モジュールごとの役割

| モジュール | 追加するもの |
|---|---|
| `:core:model` | `AuthState`（`SignedOut` / `SignedIn(login, avatarUrl)`）、`DeviceCode`、`DeviceAuthorizationResult`（`Authorized` / `Expired` / `Denied`）。`GitHubException.OAuth`（GitHub App の設定の誤りなど） |
| `:core:network` | `GitHubOAuthApi`（`https://github.com/` 向け。form 形式の POST、`Accept: application/json`）、`AuthInterceptor`、`TokenAuthenticator`、トークンを受け取るためのインターフェース `AuthTokenProvider`、`GitHubOAuthConfig`（Client ID） |
| `:core:datastore` | `AuthSessionStore`（暗号化したサインイン情報の保存。下の「保存」を参照） |
| `:core:data` | `AuthRepository`（サインインの状態、Device Flow、サインアウト）と、`AuthSessionManager`（`AuthTokenProvider` の実装。トークンの保持と更新）。サインインが必要な `ContributionRepository`（草） |
| `:core:ui` | `GitHubErrorState`（レート制限のときにサインインを勧める表示）、`ShowSnackbar` |
| `:feature:signin` | サインイン画面と `SignInViewModel`、`SignInNavKey` |
| `:feature:userlist`、`:feature:userdetail` | メニューの「サインイン／サインアウト」、レート制限のときのサインインへの導線、サインイン後の再読み込み |
| `:app` | `SignInNavKey` の登録、`DialogSceneStrategy` の追加、Snackbar の一本化（下の「Snackbar」を参照）、自動サインアウトの通知 |
| `:core:testing` | `FakeAuthRepository` |

依存の向きは今と同じで、`:core:network` は `:core:datastore` に依存しない。`:core:data` が両方を使い、`AuthTokenProvider` を実装して Hilt で `:core:network` に渡す。

```
core:data ─▶ core:network（AuthTokenProvider を定義）
    └──────▶ core:datastore
```

#### 通信
- OkHttpClient を 2 つに分ける。共通の設定（ログ）を持つ元のクライアントから `newBuilder()` で作り、接続プールは共有する
  - API 用（`api.github.com`）：HTTP キャッシュ、`GitHubHeadersInterceptor`、`AuthInterceptor`、`TokenAuthenticator`
  - OAuth 用（`github.com`）：キャッシュも認証ヘッダーも付けない。トークンの更新は API の通信を止めたまま行うので、`Dispatcher` は API 用と分ける
- `AuthInterceptor`
  - `AuthTokenProvider` がメモリに持っているトークンを `Authorization: Bearer` で付ける。通信のたびに DataStore を読まない
  - ホストが `api.github.com` のときだけ付ける（アバター画像など別のホストにトークンを送らない。Coil は別のクライアントを使う）
  - 有効期限まで 5 分を切っていたら、先に更新する（`AuthSessionManager.accessToken()`）
  - 呼び出し側が自分でヘッダーを付けたリクエスト（`ExplicitAuthorization` のタグ付き）には手を出さない。サインインの直後、保存前のトークンで `GET /user` を呼ぶときに使う
- `TokenAuthenticator`（OkHttp の `Authenticator`。401 のときに呼ばれる）
  - GitHub は、無効なトークンを付けると公開 API でも 401 を返す。対処しないと、ユーザーが GitHub 側で許可を取り消しただけでアプリが使えなくなる
  - リフレッシュトークンがあれば更新して、新しいトークンでやり直す
  - 更新できなければ（期限切れ、取り消し）トークンを消してサインアウトし、**ヘッダーなしで 1 回だけ**やり直す。サインアウトしたことを Snackbar で知らせる
  - やり直しは 1 回まで（`priorResponse` を見て無限に繰り返さない）
  - 更新は `Mutex` で 1 つにまとめる。リフレッシュトークンは使うたびに新しいものに変わるので、同時に 2 回更新すると片方が失敗する。待っていた側は、更新済みのトークンを使う
- ログ：`HttpLoggingInterceptor` は今の `BASIC`（URL だけ）のままにする。`HEADERS` 以上にするとトークンがログに出るので、使う場合は `redactHeader("Authorization")` を付ける
- HTTP キャッシュ
  - 認証なしのレスポンスは `Cache-Control: public` で、`Vary` に `Authorization` を含まない。公開データなので、サインインの前後でキャッシュを共有しても問題ない
  - サインアウト時は `Cache.evictAll()` で消す（`/user` など本人のデータを残さない）

#### 保存：DataStore と Tink
- `androidx.security:security-crypto`（`EncryptedSharedPreferences`）は非推奨になった。代わりに `androidx.datastore:datastore-tink`（1.3.0 で安定版）の `AeadSerializer` を使う
  - Tink は Google が管理している。鍵は `AndroidKeysetManager` で作り、マスター鍵は Android Keystore に置く
  - 保存する中身は kotlinx.serialization の JSON（`accessToken`、`accessTokenExpiresAt`、`refreshToken`、`refreshTokenExpiresAt`、`login`、`avatarUrl`）。ファイル名を AEAD の関連データにして、別のファイルの暗号文と入れ替えられても復号できないようにする
  - 鍵の準備（Keystore へのアクセス）は時間がかかることがあるので、最初に読み書きするとき（DataStore の IO スレッド）まで遅らせる
- 復号できないとき（バックアップから戻したときなど、Keystore の鍵は移らない）は、`ReplaceFileCorruptionHandler` で空にして未ログインとして扱う。アプリは落とさない。鍵そのものを読めないときは、鍵を作り直す
- バックアップの対象外にする。`files/`（DataStore）と、Tink の鍵を置く SharedPreferences（`githubusers_auth_keyset.xml`）を外す
- `AuthSession.toString()` はトークンを含めない（ログに出ても漏れないように）

#### Device Flow の問い合わせ
- 間隔を守って問い合わせ続ける処理は `AuthRepository.awaitAuthorization(deviceCode)` に置く。`slow_down` で間隔を延ばし、期限が来たら `Expired` を返す。一時的な通信エラーでは止めず、次の間隔で再試行する
- `delay` と時計を差し替えられるようにして、`runTest` の仮想時間でテストする
- `SignInViewModel` は `DeviceCode` を `SavedStateHandle` に保存する。回転やプロセスの終了の後も、期限内なら同じコードで問い合わせを再開する
  - `device_code` は最長 15 分で無効になり、`client_id` と組み合わせてもユーザーが許可しない限りトークンは取れないので、`SavedStateHandle` に置いてよいと判断した。結果が出たら（成功・期限切れ・拒否・失敗）すぐに消す
- ブラウザを開いている間も、プロセスが生きていれば問い合わせを続ける（ViewModel のスコープ）。WorkManager などは使わない（最長 15 分なので不要）

#### 画面のつなぎ方
- `SignInNavKey` はバックスタックに積む。大きい画面では `DialogSceneStrategy` でダイアログ、それ以外は全画面にする（判定は `:app` が行い、`isTwoPane` と同じように渡す）
- サインインに成功したら `SignInNavKey` を取り除く。一覧・詳細は `AuthState` の変化を見て、レート制限で失敗していた読み込みを再試行する
- 一覧と詳細には、`:app` から `onSignInClick` を渡す（機能どうしは依存しない）

#### Snackbar
- サインインの結果や自動サインアウトは、どの画面にいても（サインイン画面を閉じた後も）出す必要がある
- NiA と同じように、`:app` が `SnackbarHostState` を 1 つ持ち、各画面には `ShowSnackbar`（`suspend (message, actionLabel) -> Boolean`）を渡す
- サインイン画面の「コードをコピーしました」（Android 12 以前だけ）は、ダイアログの中に出す必要があるので、サインイン画面が自分で持つ

#### サインアウト
- 端末のトークンと HTTP キャッシュを消す
- GitHub 側の許可を取り消す API（`DELETE /applications/{client_id}/token`）は `client_secret` が必要なので、アプリからは呼ばない。GitHub の設定ページを開けるようにする

#### テスト
- `:core:network`：MockWebServer で、HTTP 200 で返るエラーの読み取り、`AuthInterceptor` がヘッダーを付けること（別ホストには付けないこと）、`TokenAuthenticator` の更新とヘッダーなしでのやり直し、やり直しが 1 回までであること
- `:core:data`：`awaitAuthorization` の `authorization_pending`、`slow_down`、期限切れ、拒否、一時的な通信エラー。同時に 401 が返ったときに更新が 1 回だけ行われること
- `:core:datastore`：暗号化して読み戻せること、別の鍵や別のファイルの暗号文は壊れたデータとして扱うこと（Robolectric は使わず、Tink の鍵を直接作る JVM テストにする）
- `:feature:signin`：ViewModel の状態の切り替わり、`SavedStateHandle` からの再開

### エラーの扱い

- `:core:data` は API の例外（`HttpException`、`IOException` など）を `GitHubException` に変換して投げる
  - 403 / 429 で `x-ratelimit-remaining: 0`（または `retry-after` あり）なら `RateLimited`。`x-ratelimit-reset` から解除時刻を読み取る
  - 404 は `NotFound`、通信できないときは `Network`
  - Device Flow で想定していないエラー（`device_flow_disabled` など）は `OAuth`
- `GitHubException` は画面でも使うので `:core:model` に置く。表示する文言は `:core:ui` の `errorMessage()` で作る

### 人気なリポジトリとスター順：Search API を使う

- `/users/{login}/repos` はスター順に並べられないので、`GET /search/repositories?q=user:{login} fork:true&sort=stars` を使う
- 全件を取ってから手元で並べるより呼び出し回数が少なく、Search API の制限（認証なしで 1 分に 10 回）は通常の API（1 時間に 60 回）と別枠なので、通常の API の回数も節約できる
- 人気なリポジトリが取得できなかったときは、セクションごと表示しない

### 草：GraphQL API を使い、サインインしているときだけ表示する

- GitHub の草（`contributionsCollection.contributionCalendar`）は GraphQL API でしか取れず、GraphQL API はサインインが必要
- 認証なしの Events API（直近90日、最大300件）から近似する案もあったが、GitHub の草と数が一致しないので採らない。未ログインのときは、草の場所にサインインを勧めるカードを出す
- 期間は過去1年だけ（`from` / `to` を指定しない）。年の切り替えはしない
- `:core:data` の `ContributionRepository` が `POST /graphql` を呼ぶ。GraphQL のエラーは HTTP 200 で本文の `errors` に入るので、`NOT_FOUND` は `NotFound`、`RATE_LIMITED` は `RateLimited` に変換する
- Organization と Bot には草がない（`NOT_FOUND`）。プロフィールの種類が User 以外とわかった時点で、問い合わせずにセクションを隠す
- `UserDetailViewModel` はサインインの状態を見て、サインインしたら読み込み、サインアウトしたら（401 による自動サインアウトを含む）サインインを勧める表示に戻す
- POST なので HTTP キャッシュは効かない。詳細を開くたびに1回呼ぶ（GraphQL API の上限は REST API と別枠）
- 描画は `Canvas` で行い、マス1つずつを Composable にしない（約 370 個）。タップは領域全体で受けて、指の位置から日を決める

### 活動の要約：端末上の AI（ML Kit の Prompt API）で、数値はアプリで計算する

- ML Kit GenAI の Prompt API（`com.google.mlkit:genai-prompt`、ベータ）で、AICore の Gemini Nano を使う。通信も費用もかからず、サーバーに何も送らない
- 使える端末は一部（Pixel 9 以降、Galaxy S26 など）だけ。`checkStatus()` が `UNAVAILABLE` なら、要約に関する表示を一切出さない。端末名では判定しない
- **数値は AI に作らせない**。連続日数・よく活動する曜日・よく使う言語などは `:core:data` の `activityFacts()` で計算し、画面と同じ表記（`1,269 件`、`24.1k`）に整える。AI には「この値だけを使って2〜3文にする」ことだけを頼む
- 出力は `:core:ai` の `SummaryPrompt.validate()` で確かめる。文章の中の数（全角・3桁区切りをそろえる）が、渡した値とラベルと名前の中にすべてあること、ひらがな・カタカナが十分にあること（日本語であること）。通らなければ表示せず「要約を作れませんでした」にする。自動で作り直さない（回数の上限があるため）
- 漢数字や、別々の値を結びつけた文（「9月に23日連続」など）は機械では見つけにくい。そのため、もとにした数値を画面に必ず出し、注記を付ける
- 生成はユーザーが押したときだけ。AICore にはアプリごとの回数（`BUSY`）と1日の電池（`PER_APP_BATTERY_USE_QUOTA_EXCEEDED`）の上限があり、詳細を開くたびに作るとすぐ届く
- 要約の材料は、詳細画面で読み込み済みのもの（プロフィール、リポジトリ一覧、人気なリポジトリ、草）を使い、要約のために API を呼び直さない。草とリポジトリの読み込みが終わるのを待ってから作る
- `:core:ai` を `:core:data` から分けた理由：ML Kit（ベータ）への依存を1つのモジュールに閉じ込めるため。`:core:data` は `ActivitySummarizer` のインターフェースだけを使い、テストでは偽物に差し替える
- 作った要約とモデルのダウンロードは `ActivitySummaryRepository`（シングルトン）が持つ。ダウンロードはアプリ全体のスコープ（`@ApplicationScope`）で動かし、画面を移っても続ける。要約はプロセスが続く間ログイン名ごとに覚え、開き直したら押さずに出す
- ストリーミングはしない。数値の確認を通ってから出したいのと、TalkBack の読み上げを1回にするため
- アプリが前面でなくなると AI は止まる（`BACKGROUND_USE_BLOCKED`）。これは中止と同じに扱い、エラーを出さない
- 名前は利用者が自由に決められるので、指示に入れる前に改行を除き、長さを抑える。出力は画面に表示するだけで、ほかの処理には使わない
- 対応端末と日本語の品質は、公式の資料に一覧がない。手元に対応端末がないので、実機での確認は別に行う。エミュレーターでは debug ビルドのデモの実装で画面を確かめる

### 一覧の絞り込み中は自動で追加読み込みしない

- 絞り込み中に末尾が見えるたびに読み込むと、一致するユーザーが少ない場合に上限まで読み込み続けてしまう
- 絞り込み中は「読み込み済みの N 人から絞り込んでいます」と「さらに読み込む」ボタンを出し、ユーザーが選んだときだけ読み込む
