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
  - 画面は `NavigableListDetailPaneScaffold`（material3-adaptive）で構成する。スマホは1ペイン遷移、タブレットは2ペイン
  - 色は `MaterialTheme.colorScheme`、文字は `MaterialTheme.typography` のロール経由で指定し、ハードコードしない
  - ライト／ダーク両対応
  - 状態パターン（スケルトン、初回エラー、追加読み込みエラー、レート制限）をすべて実装する
- DESIGN.md で「検討中」「改善案」「未決定」となっている項目は、実装前にユーザーに確認する

## 技術スタック（予定）

- Kotlin / Jetpack Compose / Material 3 / material3-adaptive
- 画像読み込み：Coil（`AsyncImage`）
- その他（HTTP クライアント、DI、シリアライズなど）は実装開始時に決めてここに追記する

## ディレクトリ

- `docs/design/` … デザイン仕様とモックアップ
