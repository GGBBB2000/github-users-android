# GitHub Users アプリ デザイン仕様

GitHub の User API を使う Android アプリ（Kotlin / Jetpack Compose / Material 3）の UI 仕様。
同じフォルダの `mockups/*.dc.html` は、Claude のデザインキャンバスで作ったモックアップのソース。
レイアウトの数値（dp・色・余白）を確認したいときに参照する。HTML としてそのまま実装するものではない（ファイルの一覧は末尾）。

## 前提

- Material 3 の標準テーマ（ベースラインの紫）を使う。ライト／ダークの両方に対応。Android 12 以降はダイナミックカラーにしてもよい
- 色は必ず `MaterialTheme.colorScheme` のロール経由で指定する（ハードコードしない）
- 文字は `MaterialTheme.typography` のロールを使う
- 必須の Composable：`LazyColumn`、`Image`（アバター。Coil の `AsyncImage` も可）、`Text`

## 画面構成とアダプティブ

- Navigation 3 の `NavDisplay` と `ListDetailSceneStrategy`（material3-adaptive-navigation3）で画面を構成する
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

## 3. サインイン（任意）

GitHub の Device Flow でサインインする。

### 方針
- サインインは任意。しなくてもすべての機能を使える。サインインすると API の利用上限が 1 時間 60 回から 5,000 回に増える
- アプリではパスワードを入力しない。アプリが表示するコードを、GitHub のページ（Custom Tabs）で入力して許可してもらう
- 要求する権限はない（公開情報の読み取りだけ）。GitHub の許可画面にも追加の権限は表示されない

### 入口
- 一覧の TopAppBar のオーバーフローメニュー（メニューは `surfaceContainer`、角丸4dp、項目の高さ48dp、先頭にアイコン24dp）
  - 未ログイン：「再読み込み」「GitHub にサインイン」
  - ログイン中：先頭に「@{login} でサインイン中」（押せない項目、onSurfaceVariant、アバター 24dp 付き）、その下に「サインアウト」、区切り線、「再読み込み」
  - メニューを開いている間は、オーバーフローボタンに `surfaceContainerHighest` の丸い背景を付ける
- レート制限になったとき（未ログインの場合のみ）
  - Snackbar：「API の利用上限に達しました。サインインすると上限が増えます」とアクション「サインイン」
  - 初回の読み込み失敗の画面：FilledButton「GitHub にサインイン」と OutlinedButton「再試行」を並べる。上限が解除されるまでは再試行しても失敗するので、サインインを主な操作にする
    - タイトル「API の利用上限に達しました」、本文「サインインすると上限が 1 時間 5,000 回に増えます。サインインしない場合は {リセット時刻} 以降に再試行できます。」
    - アイコンは砂時計（errorContainer の丸）
  - 詳細画面でも同じ

### サインイン画面
- 幅 600dp 以上かつ高さ 480dp 以上ならダイアログ（幅は最大 560dp、角丸 28dp、`surfaceContainerHigh`）。それ以外（スマホ、横向きのスマホ）は全画面
- 全画面のときは TopAppBar に閉じるボタン（×）、タイトル「GitHub にサインイン」
- ダイアログのときは、タイトル（headlineSmall）の行の右端に閉じるボタン（×）を置く。外側のタップで閉じることに気づきにくいため
- 中身は縦スクロールできるようにする。左右の余白 24dp、要素の間隔 20dp

上から順に：
1. 説明（bodyLarge）：「GitHub のページで次のコードを入力して、アクセスを許可してください。」
2. コード（例：`WDJB-MJHT`）
   - `surfaceContainerHighest` の背景、高さ 96dp、角丸 16dp、中央寄せ。文字は displaySmall を等幅（Roboto Mono など）にしたもの、字間を少し広げる
   - 右側にコピーの IconButton。Android 12 以前はコピー後に Snackbar「コードをコピーしました」を出す（13 以降はシステムが通知するので出さない）
3. FilledButton「GitHub を開く」（全幅、高さ 48dp、先頭に外部リンクのアイコン）
   - 押すとコードをクリップボードにコピーしてから、`verification_uri` を Custom Tabs で開く。GitHub のページで貼り付けるだけで済むようにする
   - コードを取得した直後に自動でブラウザを開くことはしない（先にコードを見せる）
4. 補足（bodyMedium、onSurfaceVariant）：「パソコンなど別の端末で github.com/login/device を開いて入力することもできます」
5. 待機中の表示：`surfaceContainerLow` の背景、角丸 16dp の枠の中に、小さい CircularProgressIndicator と「許可されるのを待っています…」（bodyMedium）、その下に「コードの有効期限：あと {N} 分」（labelMedium、onSurfaceVariant）
6. 注記（bodySmall、onSurfaceVariant、先頭に鍵のアイコン）：「このアプリが受け取るのは公開情報の読み取り権限だけです。」。全画面のときは画面の下端に寄せる

### サインイン画面の状態

| 状態 | 表示 |
|---|---|
| コードを取得中 | コード欄をスケルトン表示、コピーボタンと「GitHub を開く」は無効 |
| 許可待ち | 上の通り。ブラウザから戻ってきても待機を続ける |
| 成功 | 画面を自動で閉じて元の画面に戻り、Snackbar「@{login} としてサインインしました」。レート制限で失敗していた読み込みは自動で再試行する |
| 有効期限切れ | コードを消し、時計のアイコン、「コードの有効期限が切れました」、本文「もう一度コードを取得して、最初からやり直してください。」、FilledButton「新しいコードを取得」 |
| 拒否された | 禁止のアイコン、「アクセスが許可されませんでした」、本文「GitHub のページでアクセスが拒否されました。」、FilledButton「もう一度試す」 |
| コードを取得できない（通信エラーなど） | 一覧の初回の読み込み失敗と同じ形式（errorContainer のアイコン、「コードを取得できませんでした」、「ネットワーク接続を確認して、もう一度お試しください。」、FilledButton「再試行」） |

- 有効期限切れと拒否のアイコンは `surfaceContainerHighest` の丸（80dp）に onSurfaceVariant。エラーではないので errorContainer は使わない
- 許可待ちの間に一時的な通信エラーが起きても画面には出さず、次の問い合わせで再試行する
- 閉じる（×、戻る操作、ダイアログの外側）と中断する。確認はしない
- 画面の回転やプロセスの終了があっても、有効期限内なら同じコードで続ける

### サインアウト
- メニューの「サインアウト」で確認ダイアログを出す
  - タイトル「サインアウトしますか？」
  - 本文「この端末に保存しているアクセストークンを削除します。」
  - ボタン：TextButton「キャンセル」「サインアウト」
- サインアウト後に Snackbar「サインアウトしました」とアクション「GitHub の設定」（GitHub 側のアクセス許可を取り消すページを Custom Tabs で開く）。アプリから GitHub 側の許可を取り消すことはできないため
- GitHub 側で許可が取り消されてトークンが無効になったとき（401）は、自動でサインアウトして読み込みを続け、Snackbar「GitHub のアクセス許可が無効になったため、サインアウトしました」とアクション「サインイン」を出す

### アクセシビリティ
- コードは1文字ずつ読み上げられるように `contentDescription` を設定する（例：「W D J B ハイフン M J H T」）
- 成功・有効期限切れ・拒否への切り替わりは `liveRegion`（Polite）で読み上げる。残り時間は読み上げない（毎分の読み上げを避ける）
- コピーボタンの説明は「コードをコピー」

### 改善案
- API の残り回数が少なくなったとき（例：残り 10 回）に、レート制限になる前にサインインを勧める

## 4. 状態パターン

| 状態 | 表示 |
|---|---|
| 初回読み込み | スケルトン表示（アバターの丸と2本の線、`surfaceContainerHighest`） |
| 初回の読み込み失敗 | 中央に errorContainer 色の丸アイコン、「読み込めませんでした」、補足テキスト、FilledButton「再試行」 |
| 追加読み込みの失敗 | リスト末尾に「続きを読み込めませんでした」と OutlinedButton「再試行」 |
| レート制限（403、`X-RateLimit-Remaining: 0`） | Snackbar「API の利用上限に達しました…」とアクション「再試行」。できればリセット時刻も表示する。未ログインのときはアクションを「サインイン」にする（「3. サインイン」を参照） |

## 余白と形状のまとめ
- 画面の左右の余白：スマホ 16dp、タブレット 24dp。ペインの間隔は 24dp
- 角丸：ダイアログ 28dp、カードとペイン 24dp、内側のリスト・コード欄 16dp、チップとラベル 8dp、メニュー・Snackbar 4dp、ボタンと検索欄は全丸（pill）
- タップできる要素は 48dp 以上

## モックアップの一覧（`mockups/`）

ラッパーのファイルは、本体のファイルを引数（`dark`、`state`、`overlay` など）を変えて読み込んでいるだけ。数値は本体のファイルを見る。

| ファイル | 内容 | 本体 |
|---|---|---|
| `Main.dc.html` | スマホ / ユーザー一覧（`overlay` でメニュー・ダイアログ・Snackbar を重ねる） | — |
| `PhoneDetail.dc.html` | スマホ / 詳細（プロフィール折りたたみ） | — |
| `PhoneDetailExpanded.dc.html` | スマホ / 詳細（プロフィール展開） | PhoneDetail |
| `TabletListDetail.dc.html` | タブレット / リスト＋詳細（`selected=false` で未選択） | — |
| `TabletEmpty.dc.html` | タブレット / 未選択 | TabletListDetail |
| `DarkMain.dc.html` ほか `Dark*.dc.html` | 上記のダーク版 | 各本体 |
| `States.dc.html` | 状態パターン（初回読み込み・エラー・追加読み込み失敗・初回のレート制限） | — |
| `SignIn.dc.html` | サインイン / 許可待ち（`state`、`layout=screen|dialog`、`dark`） | — |
| `SignInLoading` / `SignInExpired` / `SignInDenied` / `SignInError` / `SignInDark.dc.html` | サインインの各状態 | SignIn |
| `SignInTablet.dc.html` | サインイン / ダイアログ（600dp 以上） | SignIn |
| `AccountMenu.dc.html` | メニュー（未ログイン / ログイン中）、サインイン成功の Snackbar | Main |
| `SignOutFlow.dc.html` | サインアウト確認、完了の Snackbar、401 の Snackbar | Main |
