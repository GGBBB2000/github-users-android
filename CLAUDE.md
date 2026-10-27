# CLAUDE.md

GitHub の User API を使ってユーザーの一覧と詳細を表示する Android アプリ。

## 方針

- 一覧は `GET /users`、詳細は `GET /users/{login}` を使う
- Android Studio で開ける Gradle（Kotlin DSL）のプロジェクトにする。言語は Kotlin、UI は Jetpack Compose と Material 3
- エラー処理、状態の扱い、アクセシビリティ、テストまで含めて、リリースできる品質にする
- 設計判断はコミットメッセージや PR の説明に残す
- 実装しきれなかったアイデアは README の「今後の改善」か `docs/` 以下にメモしておく

## デザイン

- UI 仕様は [docs/design/DESIGN.md](docs/design/DESIGN.md) に従う。実装前に必ず読むこと
- `docs/design/mockups/*.dc.html` はデザインキャンバスで作ったモックアップのソース。dp・色・余白などの数値を確認するために参照する（HTML として実装するものではない）
- 要点
  - 画面は Navigation 3 の `NavDisplay` と `ListDetailSceneStrategy`（material3-adaptive-navigation3）で構成する。スマホは1ペイン遷移、タブレットは2ペイン
  - 色は `MaterialTheme.colorScheme`、文字は `MaterialTheme.typography` のロール経由で指定し、ハードコードしない
  - ライト／ダーク両対応
  - 状態パターン（スケルトン、初回エラー、追加読み込みエラー、レート制限）をすべて実装する
- DESIGN.md で「検討中」「改善案」「未決定」となっている項目は、実装前にユーザーに確認する

## 技術スタック

- Kotlin / Jetpack Compose / Material 3 / material3-adaptive
- 画面遷移：Navigation 3（`NavDisplay`、`ListDetailSceneStrategy`）
- DI：Hilt（ViewModel の引数は assisted injection）
- 通信：Retrofit + OkHttp（HTTP キャッシュ）+ kotlinx.serialization
- 画像：Coil 3（`rememberAsyncImagePainter` + `Image`）
- ブラウザ：androidx.browser（Custom Tabs）
- 認証：GitHub App の Device Flow。トークンは DataStore と Tink（`datastore-tink`）で暗号化して保存
- テスト：JUnit 4、kotlinx-coroutines-test、Turbine、MockWebServer
- ビルド：AGP 9（Kotlin は AGP に組み込みのものを使う）、Gradle のバージョンカタログ、`build-logic` の共通設定
- `compileSdk` / `targetSdk` 37、`minSdk` 26
- 依存ライブラリは、Google・JetBrains・Square など組織が管理しているものを使い、個人のリポジトリのものは基本的に避ける

## アーキテクチャ

[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) を参照。モジュールを追加・変更するときは、このファイルも更新する。

## よく使うコマンド

```bash
./gradlew :app:assembleDebug        # デバッグ版の APK を作る
./gradlew testDebugUnitTest         # すべての単体テスト
./gradlew :app:assembleRelease      # R8 で縮小した release 版（署名なし）
```

## ディレクトリ

- `app/` … アプリ本体（一覧・詳細・サインインの組み立て）
- `core/` … 共通モジュール（model / network / data / datastore / designsystem / ui / testing）
- `feature/` … 画面ごとの機能モジュール（userlist / userdetail / signin）
- `build-logic/` … Gradle の共通設定（convention plugin）
- `docs/design/` … デザイン仕様とモックアップ
