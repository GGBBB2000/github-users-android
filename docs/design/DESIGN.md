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
- 統計の下に「活動の要約」を全幅で置く（対応端末のみ）
- プロフィールとアクティビティは2列のグリッドで**常に表示**（開閉しない）
- （検討中）上部もスマホと同じカード形式に揃え、最初から展開した状態にする

### 活動の要約（端末上の AI）

そのユーザーの活動を、端末上の AI（Gemini Nano、ML Kit GenAI の Prompt API）で2〜3文にまとめる。通信も費用もかからず、サーバーには何も送らない。

#### 前提
- **対応していない端末では、要約に関する表示を一切出さない**。判定は端末名で決めず、`checkStatus()` の結果で行う（`UNAVAILABLE` なら非表示）。判定が終わるまでも出さない
- **数値は AI に作らせない**。下の「もとにした数値」はアプリで計算し、AI には「この値だけを使って文章にする」ことだけを任せる
- **生成はユーザーが押したときだけ**。詳細を開いても自動では作らない
- 作った要約は、アプリのプロセスが続く間、`login` ごとにメモリーに覚えておく（アプリ全体で1つのキャッシュ。詳細の ViewModel ではなく Repository に置く）。同じユーザーを開き直したら、押さなくても出す

#### 置き場所
- スマホ：**プロフィールカードのすぐ下**（間隔 16dp、左右 16dp）。要約はプロフィール・草・リポジトリをまとめた内容なので、それぞれのセクションより上に「全体のまとめ」として置く。生成前は1行の入口だけなので場所を取らない
- タブレット：統計の4枚のカードの下に、**全幅の行**として置く（2列のグリッドの上）
- 横向きのスマホ（詳細ペインが狭い）はスマホと同じ表示
- カード：スマホは `surfaceContainerLow`、タブレットは `surface`（ほかのカードと同じ）。角丸16dp、余白16dp（入口だけのときは上下12dp）

#### 生成前の入口
- 1行：AI のアイコン（Material Symbols の `auto_awesome`、24dp、primary）、「このユーザーの活動を、端末上の AI で短い文章にまとめられます」（bodyMedium）、右端に FilledTonalButton「要約を作る」
- AI のアイコンは、入口と要約の見出しの2か所だけに使う。AI が作った内容があることの目印にする

#### 生成できたとき
上から（間隔 12dp）：
1. 見出し：`auto_awesome`（20dp、primary）＋「AI による要約」（titleSmall、primary）
2. 文章：**bodyLarge**（onSurface）。まとまった文章として読むものなので、ほかの補助テキストより大きくする
3. もとにした数値（文章と食い違っていないかを確かめるため）
   - スマホ：TextButton「もとにした数値（11）」とシェブロン。押すと開く（初期は閉じる。`rememberSaveable`）。開くと `surface` の背景・角丸12dp のリストで、各行は左にラベル（bodySmall、onSurfaceVariant）、右に値（bodyMedium、Medium）、高さ 40dp、行の間に区切り線
   - タブレット：常に表示。`outlineVariant` の区切り線の下に4列のグリッド（ラベル bodySmall／値 bodyMedium Medium）
   - 取れなかった値（未ログインで草がないなど）は行ごと出さない。件数の表示もその数にする
4. 下の行：左に注記「端末上の AI が作成した文章です。内容が正しくない場合があります。数値はアプリで計算しています」（labelSmall、onSurfaceVariant）、右に TextButton「もう一度作る」（更新アイコン付き）。回数の上限があるので FilledButton にはしない

- **ストリーミングはしない**。出来上がってから一度に表示する。理由：(1) 下の「数値の確認」を通ってから出したい、(2) 2〜3文なら待ち時間は数秒〜十数秒で、途中の文章を読む意味が小さい、(3) TalkBack の読み上げが1回で済む

#### 生成中
- 見出しの行の右端に TextButton「中止」
- 文章の場所に3本のスケルトン（`surfaceContainerHighest`、高さ 14dp、角丸7dp、最後の1本は幅 62%）
- 下に小さい CircularProgressIndicator（16dp）と「端末上の AI で作成中です（10 秒ほどかかります）」（bodySmall、onSurfaceVariant）
- 詳細から離れたら中止する（AI はアプリが前面にあるときしか動かない。裏に回ると `BACKGROUND_USE_BLOCKED` になる）

#### AI への頼み方と、結果の確認
- 渡すのは「もとにした数値」の表だけ。プロンプトで「この値以外の数値・事実を書かない」「日本語で2〜3文」「箇条書きや記号を使わない」と指示する。`temperature` は低め（0.2 前後）、`maxOutputTokens` で長さの上限を決める
- 生成後、文章の中の数字（`1,269`、`2026年5月` などの数）が、すべて渡した値の中にあるかをアプリで確かめる。ない数字があれば表示せず「要約を作れませんでした」にする（自動では作り直さない。回数の上限があるため）
- 値どうしを勝手に結びつけた文（例：「最も多かった月」と「最長の連続」は別々の値なのに「9月に23日連続」と書く）は機械では見つけにくい。そのため数値の一覧を必ず画面に出し、注記を付ける

#### 状態ごとの表示

| 状態（ML Kit） | 表示 |
|---|---|
| 端末が対応していない（`UNAVAILABLE`） | 何も出さない |
| モデルがない（`DOWNLOADABLE`） | 入口の文言を「要約には AI のモデルのダウンロードが必要です」、補足「数百 MB 以上あります。Wi-Fi への接続をおすすめします」（bodySmall）、ボタン「ダウンロード」。スマホでは文の下にボタンを右寄せ |
| ダウンロード中（`DOWNLOADING`） | 「AI のモデルをダウンロード中」と、ダウンロード済みの量（bodySmall）、LinearProgressIndicator（全体の量が分かれば進み具合、分からなければ不確定）、「ほかの画面に移っても続きます。終わったら『要約を作る』を押せるようになります」。ダウンロードはアプリ全体で管理し、終わっても自動では生成しない |
| ダウンロードに失敗 | 「AI のモデルをダウンロードできませんでした」と「再試行」 |
| 生成中 | 上の「生成中」 |
| 生成できた | 上の「生成できた」 |
| 混み合っている（`BUSY`） | 見出しの下に、時計のアイコン、「混み合っています」、補足「しばらくしてから、もう一度お試しください」、TextButton「再試行」 |
| 今日の上限（`PER_APP_BATTERY_USE_QUOTA_EXCEEDED`） | 情報のアイコン、「今日はこれ以上要約を作れません」、補足「端末の電池の使用量の上限に達しました。明日以降にお試しください」。**再試行は出さない**。すでに要約がある状態で「もう一度作る」がこれになったときは、要約は残して Snackbar で伝える |
| そのほかの失敗・数値の確認に通らない | `error` 色のアイコン、「要約を作れませんでした」、TextButton「再試行」。画面全体のエラーにはしない |
| 未ログイン（草がない） | **草なしで、プロフィールとリポジトリだけで要約する**。サインインは草のセクションですでに勧めているので、ここでは重ねて勧めない |
| Organization / Bot | 未ログインと同じく、プロフィールとリポジトリだけで要約する |
| データが少ない | 入口の位置に「要約できるほどの活動がありません」（アイコンは onSurfaceVariant）、ボタンなし。目安：公開リポジトリが 0 件で、かつ過去1年間のコントリビューションが 10 件未満（草が取れないときはリポジトリ 0 件だけで判断） |

#### 中身が変わったとき
- 未ログインで要約を作った後にサインインして草が取れるようになったら、要約は**そのまま残し**、注記の上に `secondaryContainer` の帯で「サインインしたので、コントリビューションの情報も含めて作り直せます」と TextButton「作り直す」を出す（自動では作り直さない）
- サインアウトしても、作った要約は消さない

#### アクセシビリティ
- 見出し「AI による要約」は `heading()`
- 文章の読み上げは「AI が作成した要約：」で始める（`contentDescription`）。AI が作ったことが音声でも分かるようにする
- 完了は、文章のノードを `liveRegion = LiveRegionMode.Polite` にして伝える。ストリーミングしないので、読み上げは1回だけ
- 生成中の「端末上の AI で作成中です」も `liveRegion = Polite`
- 数値は文章とは別に読む。各行はラベルと値を1つにまとめる（`mergeDescendants`。例「よく活動する曜日、木曜日」）。開閉ボタンは展開状態を持つ

#### 注意（実装前に確かめること）
- 対応端末と、Prompt API が**日本語の文章をどこまで正しく作れるか**は公式の資料に一覧がない。実機で確かめ、日本語で返ってこないときは「そのほかの失敗」として扱う
- ブートローダーをアンロックした端末では使えない。端末の初期化の直後なども、しばらく使えないことがある

### アクティビティ
セクション見出し（labelLarge、primary 色）＋リスト。
- GitHub 登録日：`created_at` を「2007年10月20日（19年前）」の形式で表示
- 最終更新：`updated_at` を相対表示
- 草（コントリビューションのカレンダー）：リストの下に置く。詳細は次の項目

### 草（コントリビューションのカレンダー）

過去1年間（GitHub のプロフィールと同じ）だけを表示する。年の切り替えはしない。
データは GraphQL API の `user(login) { contributionsCollection { contributionCalendar { totalContributions weeks { contributionDays { date weekday contributionCount contributionLevel } } months { name firstDay totalWeeks } } } }`。**サインインが必要**。

#### 置き場所
- スマホ：「アクティビティ」のリストの下、間隔 8dp。カードの背景は `surfaceContainerLow`（リストと同じ）、角丸16dp、内側の余白16dp
- タブレット：「プロフィール／アクティビティ」の2列のグリッドの下に、**全幅の行**として置く（間隔 24dp）。カードの背景は `surface`（ペイン内の他のカードと同じ）
- 横向きのスマホなど、詳細ペインが狭いときは、下の「マスの大きさ」のルールで自動的に横スクロールになる

#### カードの中身（上から、間隔 12dp）
1. 見出しの行（高さ 20dp）：左に「過去1年間のコントリビューション」（titleSmall、onSurface）、右端に「1,234 件」（titleSmall、primary）。件数は `totalContributions` を3桁区切りで表示
2. 草：左に曜日のラベルの列（幅 20dp）、右にマスの領域
   - 月のラベル：マスの上の行（高さ 16dp、マスとの間隔 4dp）。labelSmall（11sp）、onSurfaceVariant、「11月」の形式。`months[]` の `firstDay` を含む週の列の左端に置く。次のラベルまで3列未満なら省く（左端の、途中から始まる月が重ならないように）
   - 曜日のラベル：GitHub と同じく **月・水・金** だけ。labelSmall、onSurfaceVariant、その行のマスの中央にそろえる
   - 行は `weekday`（0=日曜を一番上）。最初と最後の週は7日に満たないので、ない日のマスは描かない
3. 下の行（高さ 16dp 以上）：左に「選択中の日」の表示、右に凡例
   - 選択中の日：未選択なら「マスをタップすると件数を表示」（bodySmall、onSurfaceVariant）。選択中は「2026/9/17（木）  24 件」（bodySmall、onSurface、Medium）
   - 凡例：「少ない ■■■■■ 多い」。文字は labelSmall、マスは草と同じ大きさ。TalkBack では「凡例：色が濃いほど件数が多い」と読む

#### マスの大きさ（レイアウト）
`BoxWithConstraints` で、マスの領域の幅 W（カードの内側の幅 − 曜日の列 20dp）から決める。
- 1列あたりの幅 `p = floor(W / 53)` が **13dp 以上**なら全体表示：マス `min(p − 3, 13)`dp、間隔 3dp。左寄せ
- 13dp 未満なら**横スクロール**：マス 11dp、間隔 3dp（1列 14dp、全体 約 740dp）。最初は**右端（最近の週）**を表示する（`rememberScrollState()` を `scrollTo(maxValue)`、または `LazyRow` の `reverseLayout`）
  - 曜日のラベルの列はスクロールしない（固定）。月のラベルはマスと一緒にスクロールする
  - 左にスクロールできるときは、左端に幅 24dp のフェード（カードの背景色 → 透明）を重ねて、続きがあることを示す
- 目安：スマホ縦（幅 360〜412dp）は横スクロール（1画面に約 21〜25 週）。タブレットの詳細ペイン（モックアップでは W = 692dp）は p = 13 で全体表示（マス 10dp）
- 角丸はマス 2dp
- 文字は sp なので、文字を大きくすると月のラベルの行が高くなる。マスは dp のまま

#### 色（5段階）
濃さは API の `contributionLevel` をそのまま使う（アプリで四分位を計算しない）。色はすべて `MaterialTheme.colorScheme` から作る。GitHub の緑は使わない。

| `contributionLevel` | 色 |
|---|---|
| `NONE` | `surfaceContainerHighest` |
| `FIRST_QUARTILE` | `primary.copy(alpha = 0.30f).compositeOver(カードの背景)` |
| `SECOND_QUARTILE` | `primary.copy(alpha = 0.52f).compositeOver(カードの背景)` |
| `THIRD_QUARTILE` | `primary.copy(alpha = 0.76f).compositeOver(カードの背景)` |
| `FOURTH_QUARTILE` | `primary` |

- 不透明度を掛けた色は、カードの背景に重ねて**不透明な色にしてから**描く（重なりやスクロールのフェードで色が変わらないように）
- ロールの組み合わせ（`primaryContainer` など）にしない理由：ライトとダークで明るさの順番が逆になるロールがあり、「濃い＝多い」が保てないため。`primary` の不透明度なら、どちらのテーマでも背景から離れるほど多い、になる
- ダイナミックカラーでも同じ式で作れる

#### 日ごとの件数の確認（操作）
- **タップで日を選ぶ**。マス1つずつをタップの対象にはしない（48dp を満たせないため）。草の領域全体で `pointerInput` の `detectTapGestures` を受け、指の位置から列と行を計算して、最も近いマスの日を選ぶ
- 選んだマスは、外側に 2dp の枠（`onSurface`）を描く。下の行の「選択中の日」が切り替わる
- 同じマスをもう一度タップすると選択を外す。選択は `rememberSaveable` で保持し、別のユーザーを開いたら外す
- 吹き出し（ツールチップ）は使わない：指でマスが隠れるのと、横スクロールの領域では位置がずれやすいため。長押しやドラッグでの連続選択もしない（横スクロールの操作とぶつかるため）

#### 状態ごとの表示

| 状態 | 表示 |
|---|---|
| 読み込み中 | カードの形はそのまま（高さが変わらないように）。見出しの文字は2本のバー（`surfaceContainerHighest`、高さ 14dp、角丸7dp）、マスはすべて `surfaceContainerHighest`、月と曜日のラベルと凡例は出さない |
| 未ログイン | 同じカードの中に：40dp の丸（`primaryContainer`）に草のアイコン、「コントリビューションのカレンダー」（titleSmall）、「サインインすると、コントリビューションのカレンダーを表示できます」（bodyMedium、onSurfaceVariant）、FilledTonalButton「GitHub にサインイン」（既存のサインイン画面を開く）。スマホは縦に並べてボタンを右寄せ、タブレットは横1行 |
| 取得に失敗 | カードの中に1行：エラーのアイコン（`error`、24dp）、「コントリビューションを読み込めませんでした」（bodyMedium）、TextButton「再試行」。画面全体のエラーにはしない |
| 0 件 | 空のカレンダー（すべて `NONE`）を出し、見出しは「0 件」、下の行は「過去1年間のコントリビューションはありません」。凡例とタップは出さない |
| Organization / Bot | セクションごと出さない（API が返さないため） |
| サインインの設定がない（Client ID が空） | セクションごと出さない |

- 取得中に 401 が返ったら、既存のルールどおり自動でサインアウトし、「未ログイン」の表示に変わる
- サインインに成功したら、開いている詳細の草を読み込み直す

#### アクセシビリティ
- 草全体（月・曜日のラベルとマス）を1つの要素にする（`Modifier.clearAndSetSemantics`）。読み上げは要約：「過去1年間のコントリビューション 1,269 件。最も多い日は 2026年9月17日の 24 件」
- TalkBack ではタップの位置を指定できないので、`customActions` で選択を動かす：「前の日」「次の日」「前の週」「次の週」。最初の操作では最新の日を選ぶ
- 「選択中の日」の行は `liveRegion = LiveRegionMode.Polite`。読み上げは「2026年9月17日 木曜日 24 件」（画面の表示より長い形）
- 選んだ日は色だけでなく枠でも示す（色覚に頼らない）

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
- 角丸：ダイアログ 28dp、カードとペイン 24dp、内側のリスト・コード欄・草のカード 16dp、チップとラベル 8dp、メニュー・Snackbar 4dp、草のマス 2dp、ボタンと検索欄は全丸（pill）
- タップできる要素は 48dp 以上

## モックアップの一覧（`mockups/`）

ラッパーのファイルは、本体のファイルを引数（`dark`、`state`、`overlay` など）を変えて読み込んでいるだけ。数値は本体のファイルを見る。

| ファイル | 内容 | 本体 |
|---|---|---|
| `Main.dc.html` | スマホ / ユーザー一覧（`overlay` でメニュー・ダイアログ・Snackbar を重ねる） | — |
| `PhoneDetail.dc.html` | スマホ / 詳細（プロフィール折りたたみ・要約の入口。`contrib` で草、`summary` で要約の状態を切り替え） | — |
| `PhoneDetailExpanded.dc.html` | スマホ / 詳細（プロフィール展開） | PhoneDetail |
| `TabletListDetail.dc.html` | タブレット / リスト＋詳細（要約あり。`selected=false` で未選択、`contrib` で草、`summary` で要約の状態を切り替え） | — |
| `TabletEmpty.dc.html` | タブレット / 未選択 | TabletListDetail |
| `DarkMain.dc.html` ほか `Dark*.dc.html` | 上記のダーク版（`DarkPhoneDetail` は要約を作った後） | 各本体 |
| `States.dc.html` | 状態パターン（初回読み込み・エラー・追加読み込み失敗・初回のレート制限） | — |
| `Contribution.dc.html` | 草の部品（`state`：data／selected／empty／loading／signedOut／error／hidden、`layout`：phone／tablet、`dark`）。詳細の画面はこれを読み込む | — |
| `ContributionStates.dc.html` | 草の状態の一覧（スマホ幅） | Contribution |
| `DarkContributionStates.dc.html` | 上記のダーク版 | ContributionStates |
| `Summary.dc.html` | 要約の部品（`state`：entry／download／downloading／generating／done／stale／busy／quota／error／noData／hidden、`layout`：phone／tablet、`valuesOpen`、`dark`）。詳細の画面はこれを読み込む | — |
| `SummaryStates.dc.html` | 要約の状態の一覧（スマホ幅） | Summary |
| `DarkSummaryStates.dc.html` | 上記のダーク版 | SummaryStates |
| `PhoneDetailSummary.dc.html` | スマホ / 詳細（要約を作った後） | PhoneDetail |
| `SignIn.dc.html` | サインイン / 許可待ち（`state`、`layout`：screen／dialog、`dark`） | — |
| `SignInLoading` / `SignInExpired` / `SignInDenied` / `SignInError` / `SignInDark.dc.html` | サインインの各状態 | SignIn |
| `SignInTablet.dc.html` | サインイン / ダイアログ（600dp 以上） | SignIn |
| `AccountMenu.dc.html` | メニュー（未ログイン / ログイン中）、サインイン成功の Snackbar | Main |
| `SignOutFlow.dc.html` | サインアウト確認、完了の Snackbar、401 の Snackbar | Main |
