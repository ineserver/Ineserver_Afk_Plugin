# IneServerStatusPlugin

いねさば用のステータス管理プラグインです。プレイヤーのステータス（AFK、作業中、撮影中など）を管理し、TABリストのプレフィックスやパーティクル表示を行います。

## 特徴
- **ステータス管理**: GUIまたはコマンドでステータスを変更可能。
- **TABプレフィックス**: LuckPermsのTransientDataを使用して、ステータスに応じたプレフィックスを自動適用（要LuckPermsグループ設定）。
- **AFK機能**: 移動すると自動解除。理由（ご飯、お風呂など）を指定すると専用のパーティクルが表示されます。
- **パーティクル**: ステータスに応じて頭上にパーティクルを表示（作業中はエンチャントテーブルの文字など）。

## コマンド
- `/status`: ステータス選択GUIを開きます。
- `/status <status> [reason]`: ステータスを直接設定します。
- `/afk [reason]`: AFKステータスに設定します（`/status afk`のショートカット）。

### ステータス一覧
- `normal` (解除): 通常状態に戻します。
- `chat` (雑談歓迎): `group.chat` グループに追加。
- `afk` (AFK): `group.afk` グループに追加。理由指定でパーティクル変化。
- `work` (作業中): `group.work` グループに追加。エンチャント文字パーティクル。
- `rec` (撮影中): `group.rec` グループに追加。
- `cat` (ねこ): `group.scat` グループに追加。

## 必要要件
- Paper 1.21.4+
- LuckPerms 5.4+

## AFKパーティクル
AFKの理由に含まれる単語によってパーティクルが変化します。
- 睡眠, 寝, sleep -> 雲 (CLOUD)
- ご飯, 飯, food, eat -> ハート (HEART)
- 風呂, bath, shower -> 泡 (BUBBLE_POP)
- トイレ, wc -> 水滴 (FALLING_WATER)
- 散歩, 運動, walk, run -> ハッピー (HAPPY_VILLAGER)
- 作業, work -> エンチャント文字 (ENCHANT)
- その他 -> 音符 (NOTE)
