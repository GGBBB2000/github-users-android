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
| `:core:testing` | Android ライブラリ | テスト用の偽の Repository、テスト用のデータ、`MainDispatcherRule` など |
| `:feature:userlist` | 機能モジュール | ユーザー一覧（リストペイン）の画面と ViewModel、遷移先の型 `UserListNavKey` と画面の登録処理 |
| `:feature:userdetail` | 機能モジュール | ユーザー詳細（詳細ペイン）の画面と ViewModel、遷移先の型 `UserDetailNavKey` と画面の登録処理 |

### 依存の向き

```
app ─▶ feature:* ─▶ core:data ─▶ core:network ─▶ core:model
            └──────▶ core:ui ─▶ core:designsystem
```

- 機能モジュールどうしは依存しない。一覧と詳細をつなぐのは `:app` の役割
- `:core:model` は Android に依存しない Kotlin だけのモジュールにする
- 機能モジュールは API のレスポンス型を直接使わない。`:core:data` が返すデータ型だけを使う

### NiA から取り入れないもの

| NiA のモジュール | 理由 |
|---|---|
| `core:database`、`core:datastore` | オフライン保存はしない（下の「キャッシュ」を参照）。OAuth のトークンや設定の保存が必要になったら追加する |
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

### パッケージ名と namespace：`com.example.githubusers`

- 各モジュールの namespace は `com.example.githubusers.core.network`、`com.example.githubusers.feature.userlist` のようにする

### 最低 Android バージョン：26（Android 8.0）

- `minSdk = 26`。ダイナミックカラーは Android 12 以降だけで使い、それより前は M3 の標準テーマにする

### 認証：最初は認証なしで作る

- まずは認証なしで、すべての機能が動くものを作る
- 後から OAuth でアクセストークンを取ってくる処理を追加する。追加するときは、トークンの保存先（`:core:datastore` など）と、OkHttp の `Interceptor` で `Authorization` ヘッダーを付ける仕組みを設計する
- 後から追加しやすいように、API の呼び出しは `:core:network` に閉じ込め、ヘッダーを付ける処理を差し込める形にしておく
- クライアントシークレットなどの秘密情報はリポジトリに入れない

### エラーの扱い

- `:core:data` は API の例外（`HttpException`、`IOException` など）を `GitHubException` に変換して投げる
  - 403 / 429 で `x-ratelimit-remaining: 0`（または `retry-after` あり）なら `RateLimited`。`x-ratelimit-reset` から解除時刻を読み取る
  - 404 は `NotFound`、通信できないときは `Network`
- `GitHubException` は画面でも使うので `:core:model` に置く。表示する文言は `:core:ui` の `errorMessage()` で作る

### 人気なリポジトリとスター順：Search API を使う

- `/users/{login}/repos` はスター順に並べられないので、`GET /search/repositories?q=user:{login} fork:true&sort=stars` を使う
- 全件を取ってから手元で並べるより呼び出し回数が少なく、Search API の制限（認証なしで 1 分に 10 回）は通常の API（1 時間に 60 回）と別枠なので、通常の API の回数も節約できる
- 人気なリポジトリが取得できなかったときは、セクションごと表示しない

### 一覧の絞り込み中は自動で追加読み込みしない

- 絞り込み中に末尾が見えるたびに読み込むと、一致するユーザーが少ない場合に上限まで読み込み続けてしまう
- 絞り込み中は「読み込み済みの N 人から絞り込んでいます」と「さらに読み込む」ボタンを出し、ユーザーが選んだときだけ読み込む
