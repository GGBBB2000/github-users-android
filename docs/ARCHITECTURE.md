# アーキテクチャ

今後の拡張性を考えて、マルチモジュール構成にする。構成は [Now in Android](https://github.com/android/nowinandroid)（以下 NiA）を参考にし、このアプリに必要なものだけを取り入れる。

## モジュール構成

| モジュール | 種類 | 役割 |
|---|---|---|
| `:app` | Android アプリ | `MainActivity`、テーマの適用、`NavigableListDetailPaneScaffold` による画面の組み立て、Hilt の起点 |
| `:build-logic:convention` | Gradle の共通設定 | Android ライブラリ、Compose、機能モジュール、Hilt などの共通設定 |
| `:core:model` | Kotlin のみ | `User`、`UserDetail`、`Repository` などのデータ型。Android に依存しない |
| `:core:network` | Android ライブラリ | Retrofit、OkHttp、kotlinx.serialization、API のレスポンス型、HTTP キャッシュ、レート制限ヘッダーの読み取り |
| `:core:data` | Android ライブラリ | Repository の定義と実装、API のレスポンス型からデータ型への変換、エラー型への変換 |
| `:core:designsystem` | Android ライブラリ（Compose） | M3 テーマ（ライト／ダーク）、共通部品（検索欄、スケルトン、状態表示など） |
| `:core:ui` | Android ライブラリ（Compose） | データ型を使う共通 UI（頭文字入りの丸に切り替わるアバター、数値の短縮表示など） |
| `:core:testing` | Android ライブラリ | テスト用の偽の Repository、テスト用のデータ、`MainDispatcherRule` など |
| `:feature:userlist` | 機能モジュール | ユーザー一覧（リストペイン）の画面と ViewModel |
| `:feature:userdetail` | 機能モジュール | ユーザー詳細（詳細ペイン）の画面と ViewModel |

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
| `core:navigation`、機能の `api` / `impl` 分割 | Navigation 3 を使わないため（下の「決定事項」を参照） |
| `benchmarks`、`app-nia-catalog`、`core:screenshot-testing` | 後から追加できる |

## 決定事項

### 画面の組み立て：`NavigableListDetailPaneScaffold` を使う

- [DESIGN.md](design/DESIGN.md) の指定どおり、material3-adaptive の `NavigableListDetailPaneScaffold` で一覧と詳細を組み立てる
- 一覧と詳細を両方知っている `:app` で組み立てる。機能モジュールは画面の Composable と ViewModel を公開し、選択されたユーザーなどはコールバックで受け渡す
- NiA は Navigation 3 と `ListDetailSceneStrategy` を使い、機能を `api` と `impl` に分けている。しかし `adaptive-navigation3` はまだ alpha 版なので、製品として出すアプリには使わない。画面が増えて Navigation 3 が安定版になったら移行を検討する

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
