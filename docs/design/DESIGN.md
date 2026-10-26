# GitHub Users アプリ デザイン仕様

GitHub の User API を使う Android アプリ（Kotlin / Jetpack Compose / Material 3）の UI 仕様。
同じフォルダの `mockups/*.dc.html` は、Claude のデザインキャンバスで作ったモックアップのソース。
レイアウトの数値（dp・色・余白）を確認したいときに参照する。HTML としてそのまま実装するものではない。

## 前提

- Material 3 の標準テーマ（ベースラインの紫）を使う。ライト／ダークの両方に対応。Android 12 以降はダイナミックカラーにしてもよい
- 色は必ず `MaterialTheme.colorScheme` のロール経由で指定する（ハードコードしない）
- 文字は `MaterialTheme.typography` のロールを使う
- 必須の Composable：`LazyColumn`、`Image`（アバター。Coil の `AsyncImage` も可）、`Text`

## 画面構成とアダプティブ

- `NavigableListDetailPaneScaffold`（material3-adaptive）で画面を構成する
  - Compact（スマホ）：リスト → 詳細の1ペイン遷移。戻る操作でリストへ戻る
  - Expanded（タブレット）：左にリスト（幅 約400dp）、右に詳細の2ペイン
  - タブレットで未選択のとき、詳細ペインは**空欄**（`surfaceContainer` の背景のみ）

## 1. ユーザー一覧（リストペイン）

API：`GET /users?since={lastId}&per_page=…`（`since` でページング）

上から順に：
1. TopAppBar：タイトル「GitHub Users」（titleLarge）、右端にオーバーフローメニュー
2. SearchBar 風の絞り込み欄（高さ56dp、角丸28dp、`surfaceContainerHigh`）。プレースホルダは「ユーザー名で絞り込み」
   - `/users` には検索機能がないので、**読み込み済みの一覧をクライアント側で絞り込む**
3. `LazyColumn` の各行（ListItem、高さ72dp）
   - leading：アバター 48dp の円。読み込み中と失敗時は、頭文字入りの tonal な丸（primaryContainer / secondaryContainer / tertiaryContainer を順番に使う）
   - headline：`login`（titleMedium）
   - supporting：「ID {id} · {type}」（bodyMedium、onSurfaceVariant）
   - trailing：スマホのみシェブロン
   - タブレットの選択中の行は `secondaryContainer` の背景、角丸28dp
4. 末尾：追加読み込み中は小さい CircularProgressIndicator と「さらに読み込み中…」

## 2. ユーザー詳細（詳細ペイン）

API：`GET /users/{login}`、`GET /users/{login}/repos`

### TopAppBar
- 戻るボタン（スマホのみ）、タイトル `login`
- actions：「GitHub で開く」（html_url をブラウザ／Custom Tabs で開く）、共有

### プロフィールカード（スマホ）
`surfaceContainer` の背景、角丸24dp、左右の余白16dp。
- 1行目：アバター 72dp、`name`（titleLarge）と `@login`（bodyLarge、onSurfaceVariant）、右端に type のラベル（`secondaryContainer`、角丸8dp）
- `bio`（bodyMedium）。null ならこの行は表示しない
- 統計：`outlineVariant` の区切り線の下に、4列のグリッドで フォロワー／フォロー中／リポジトリ／Gist。数値は titleLarge、ラベルは labelMedium。1000以上は `24.1k` のように略す
- 展開エリア（`AnimatedVisibility`）：所属・場所・ウェブサイト・メール・X の ListItem（アイコン＋ラベル＋値）。`surface` の背景、角丸16dp
  - 値が null の項目は行ごと表示しない。5項目すべてが null なら開閉ボタンも出さない
  - ウェブサイトはリンク（primary 色）にする。メールは null なら「非公開」
- 開閉ボタン：カード全幅の TextButton「プロフィールの詳細」と下向きシェブロン。展開時は「…を閉じる」に変え、シェブロンを180°回転させる
- 展開状態は `rememberSaveable` で保持する

### タブレットの詳細ペイン
`surfaceContainer` の背景、角丸24dp、余白32dp。
- ヘッダー：アバター 104dp、name（headlineMedium）、@login、ラベル類、右端に FilledButton「GitHub で開く」
- 統計は4枚のカード（`surface` の背景、角丸16dp）
- プロフィールとアクティビティは2列のグリッドで**常に表示**（開閉しない）
- （検討中）上部もスマホと同じカード形式に揃え、最初から展開した状態にする

### アクティビティ
セクション見出し（labelLarge、primary 色）＋リスト。
- GitHub 登録日：`created_at` を「2007年10月20日（19年前）」の形式で表示
- 最終更新：`updated_at` を相対表示
- （改善案）草：GitHub の草は GraphQL API（認証必須）でしか取れない。認証なしでは Events API（直近90日、最大300件）でヒートマップを作る案がある。どちらにするかは未決定

### 人気なリポジトリ
- スマホは横スクロールのカード（LazyRow、幅248dp）、タブレットは3列のグリッド
- カード：リポジトリアイコン、名前（primary 色）、説明（2行で省略）、言語の色の丸＋言語名、★スター数、フォーク数
- 並び順はスター数の降順の上位3件。`/users/{login}/repos` にはスター順のソートがないので、取得後にクライアント側で並べ替える（または Search API の `q=user:{login}&sort=stars` を使う）

### リポジトリ一覧
- 見出しの横に件数（`public_repos`）
- FilterChip で並び替え：更新順（初期値、`sort=updated`）／スター順／名前順（`sort=full_name`）
- 各行：名前（titleMedium）、説明、言語・★・更新日（labelMedium）。行の間に区切り線
- 末尾に「さらに表示」の TextButton（`page` でページング）

## 3. 状態パターン

| 状態 | 表示 |
|---|---|
| 初回読み込み | スケルトン表示（アバターの丸と2本の線、`surfaceContainerHighest`） |
| 初回の読み込み失敗 | 中央に errorContainer 色の丸アイコン、「読み込めませんでした」、補足テキスト、FilledButton「再試行」 |
| 追加読み込みの失敗 | リスト末尾に「続きを読み込めませんでした」と OutlinedButton「再試行」 |
| レート制限（403、`X-RateLimit-Remaining: 0`） | Snackbar「API の利用上限に達しました…」とアクション「再試行」。できればリセット時刻も表示する |

## 余白と形状のまとめ
- 画面の左右の余白：スマホ 16dp、タブレット 24dp。ペインの間隔は 24dp
- 角丸：カードとペイン 24dp、内側のリスト 16dp、チップとラベル 8dp、ボタンと検索欄は全丸（pill）
- タップできる要素は 48dp 以上
