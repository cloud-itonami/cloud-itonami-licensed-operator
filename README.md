# cloud-itonami-licensed-operator

許認可業種で、自社が名義人になれないとき、**許認可を持つ第三者を主体に立てて
自社はその ops 層に徹する（defer）ことができるか** を判定する `.cljc` commons。

`cloud-itonami-regulatory-tracker` と同じく **plain library** — advisor も
StateGraph も台帳も持たない。何も申請せず、許認可も持たず、官庁にも接触しない。
呼び出し側 actor の governor が結果を見て自分で HOLD/escalate/commit を決める。

**consumer**: `cloud-itonami-isic-6910-legalsupport`（`licensee/verify` に
reviewer 検査を委譲。抽出元でもある）。

**76 tests / 1,151 assertions green** (`clojure -M:test`)、`clojure -M:lint` clean。

## なぜ要るか

cloud-itonami の実装済み actor 177 本の大半は、日本では**自社が名義人になれない
業種**に載っている（農業・鉱業・製造・医療・金融・士業）。これらを「日本では
できない」で終わらせると、実装済みの資産のほとんどが国内で死ぬ。

死なせない形は既に一つ実証されている。
[`cloud-itonami-isic-6910-legalsupport`](https://github.com/cloud-itonami/cloud-itonami-isic-6910-legalsupport)
は、法人が弁護士資格を取れないという事実を回避せず受け入れた上で、法務省
ガイドライン第4項（**弁護士が自ら精査し必要に応じ自ら修正するなら、他の全要件に
該当しても弁護士法72条に違反しない**）に載る形で成立させている。

この repo はその形の一般化にあたる。弁護士に固有だった検査 —— 資格が検証済みか /
案件と同一法域か / 本人が自ら判断したか —— を業種に依存しない語彙へ開き、
どの許認可業種でも同じ判定ができるようにした。

`regulatory-tracker` との違いははっきりしている: あちらは**許認可を取る**進行
（draft → submitted → approved）の管理。こちらは**許認可を持たないまま動けるか**
の判定。同じ「規制」でも別の問いで、既存 commons は後者を持っていなかった。

## 判定するもの

(法域, 業種) ごとに2つの経路を持ち、それぞれに cited verdict が付く。

| 業種 | 許認可 | 法人で取得可 | ゲート数 | principal | defer |
|---|---|---|---|---|---|
| legal-services | 弁護士資格 | **不可** | 1 | `:prohibited` | **`:admissible`** |
| medical-practice | 医療機関の開設許可 | **不可** | 1 | `:prohibited` | `:conditional` |
| second-hand-dealing | 古物商許可 | 可 | 1 | `:conditional` | `:conditional` |
| industrial-waste-collection | 産廃収集運搬業許可 | 可 | 1 | `:conditional` | `:conditional` |
| waste-disposal | 産廃処分業許可 | 可 | 1 | `:conditional` | `:conditional` |
| food-manufacture | 食品衛生法 営業許可（32業種） | 可 | 1 | `:conditional` | `:conditional` |
| alcohol-manufacture | 酒類の製造免許 | 可 | 1 | `:conditional` | `:conditional` |
| warehousing | 倉庫業の登録 | 可 | **2** | `:conditional` | `:conditional` |
| telecom | 電気通信事業の登録／届出 | 可 | **2** | `:conditional` | `:unsettled` |
| employment-placement | 有料職業紹介事業の許可 | 可 | 1 | `:conditional` | `:conditional` |
| real-estate-brokerage | 宅地建物取引業の免許 | 可 | 1 | `:conditional` | `:conditional` |
| travel-agency | 旅行業の登録 | 可 | 1 | `:conditional` | `:conditional` |
| financial-instruments | 金融商品取引業の登録 | 可 | 1 | `:conditional` | `:unsettled` |

地方の層として `JPN-13`（東京都）が industrial-waste-collection /
food-manufacture / second-hand-dealing の3件。国の層を継承し、上乗せだけを持ちます。

`:conditional` は「カタログが検証できない事実（許認可を実際に持っているか等）に
かかっている」の意味で、運営者の宣誓（attestation）でのみ解錠する。`:unsettled`
は未調査で、**宣誓では解錠できない**。

### 許認可は1つとは限らない

`warehousing` のゲート数が 2 なのは、**JSIC 4721 冷蔵倉庫業が倉庫業法第3条の
登録（国交省）だけでは営業できない**からです。東京都の公式資料は「届出が不要な
業種」として『食品又は添加物の貯蔵又は運搬のみをする営業』を挙げつつ、
**冷凍又は冷蔵倉庫業を明示的に除外**しており、保健所への食品衛生法の営業届出が
別途要る。主たる許認可だけを見て「取れた＝営業できる」と読むと、確信を持って
違法になります。`gate/all-gates` と `plan` の `:additional-gates` がこれを
必ず返します。

```clojure
(gate/all-gates "JPN" :sector/warehousing)
;; => [{:licence/name "倉庫業の登録" :licence/law "倉庫業法 第3条" …}
;;     {:licence/name "食品衛生法の営業届出（冷凍又は冷蔵倉庫業）"
;;      :licence/applies-when :refrigerated …}]
```

### 法域は国だけではない（`JPN-13`）

許認可の実務値は都道府県で違います。産廃収集運搬の手数料 81,000 円は**東京都の値**
であって全国一律ではないし、食品衛生法の施設基準は54条により**都道府県の条例**が
定めます。これを国の層に書くと、大阪で営業する利用者に東京の数字を渡すことになる。

カタログのキーは `[jurisdiction sector]` で、`"JPN-13"` のような ISO 3166-2 形の
id を置くと `entry` が親（`"JPN"`）から継承して解決します。ルール・副次ゲート・
`:known-gaps` は積み上がり、`:licence` は地方の値が上書きします。47都道府県ぶん
複製する必要はありません。

**地方の層は締める方向にしか動けません。** 条例や規則が法律の禁止を適法にすることは
できないので、地方の verdict が国より緩ければ `entry` はそれを無視して国の verdict を
維持し、`:sub-national-loosening-ignored` に事実を残します。

```clojure
(cat/entry "JPN-13" :sector/industrial-waste-collection)
;; => {:inherited-from "JPN"
;;     :licence {:licence/law "廃棄物の処理及び清掃に関する法律 第14条第1項"  ; 国から
;;               :licence/authority "東京都知事（東京都環境局 資源循環推進部）" ; 都で上書き
;;               :licence/fee-jpy 81000}                                      ; 都だけが持つ
;;     :rules [...国のルール2件...]}
```

## 委譲はカタログが開いただけでは成立しない

これが設計の要点です。`:route/defer` が `:admissible` でも、`plan` は立てた
有資格者の**実レコード**を毎回検査し、記録が無い項目は fail closed で落とす。

```clojure
(gate/plan {:jurisdiction "JPN" :sector :sector/legal-services
            :holder {:holder/id "L-JP" :holder/licence-jurisdiction "USA"
                     :holder/licence-verified? true}
            :matter {:matter/jurisdiction "JPN" :matter/personally-decided? true}})
;; => {:route :blocked
;;     :blockers [{:rule :req/same-jurisdiction
;;                 :detail "資格法域 \"USA\" が案件法域 \"JPN\" と一致しない。…"}]
;;     :next {:action :fix-holder-record …}}
```

検査できる要件は6つ: `:req/licence-verified`（自己申告は検証ではない）/
`:req/same-jurisdiction`（**越境は ops 層が無資格営業に化ける典型経路**）/
`:req/scope-covers`（許可はあるが区分が違うのは許可が無いのと同じ）/
`:req/not-expired` / `:req/personally-decided`（**名義を立てただけ・押印しただけは
委譲ではない**）/ `:req/written-contract`。

要求された要件をこの namespace が実装していない場合、それは違反として返る —
**未実装の検査を「満たした」ことにはしない**。

## principal を先に試す

`plan` の経路選択は `principal → defer → blocked` の順で固定してあります。
自社が名義人になれるならその経路を採る。委譲は「許認可を取らずに済ませる裏口」
ではないので、先に試させない。

```clojure
(gate/plan {:jurisdiction "JPN" :sector :sector/second-hand-dealing
            :licence-held? false})
;; => {:route :blocked
;;     :next {:action :obtain-licence :licence "古物商許可"
;;            :kyoninka-procedure :kobutsu-marunouchi}}
```

閉じているとき `:next` が次の一手を返し、取得可能な許認可なら
[`kotoba-lang/kyoninka`](https://github.com/kotoba-lang/kyoninka)（日本の許認可
手続きの procedure-as-data）の該当手続き id を指します。これが ITAD の現在地です
—— Gftd Japan は古物商・産廃とも**取得作業中**なので、`principal` は
`:licence-not-held` で閉じ、`:next` が kyoninka を指す。

## カタログの規約は legalsupport と同一

意図的に同じにしてあります。

1. **捏造しない。** (法域, 業種) が無ければ spec-basis は無く、gate は既定で拒否する。
2. **未検証出典から許可を出さない。** `:rule/verification` が
   `:secondary-source-only` のルールは制限的 verdict しか支えられず、
   `:admissible` の唯一の根拠にはできない（`verdict-for` が `:unsettled` へ降格し
   `:downgraded-from` を残す）。慎重方向に誤ればカバレッジを失うだけだが、許可方向に
   誤れば無許可営業の刑事責任を利用者に負わせる。
3. **カバレッジは報告する。** 未収載は「規制が無い」ではなく「未調査」。

現在: **27件（法域×業種）・51の異なるルール** —— JPN 13 + JPN-13 3 + GBR 5 + DEU 3 +
FRA 1 + CAN-ON 2。うち **46 が条文原文の読了**、5 が公式ページの取得で、
**二次情報のみに依拠したルールはゼロ**です。

取得経路は法域ごとに違い、いずれも認証不要:
日本 e-Gov 法令 API、英国 legislation.gov.uk（各条に `data.xml`）、
ドイツ gesetze-im-internet.de（`xml.zip`）、EU は EUR-Lex、
カナダ・オンタリオは e-Laws の生 HTML（**WebFetch では JS シェルしか返らないので
curl で取得して抽出**）。フランスは Légifrance が curl を 403 で拒むため WebFetch 経由。

### 地方の層には2種類ある

カナダを入れて、`JPN-13` で作った「国の法律＋地方の上乗せ」という前提が
**崩れる**ケースが出ました。カナダでは法律サービスも不動産業も州の専属管轄で、
**対応する連邦法が存在しません**。

- `:overlay` —— 国の法律が本体、地方が手数料・施設基準・窓口を上乗せ（JPN-13）
- `:exclusive` —— 立法権が地方に専属し、国の層が無い（CAN-ON）

`entry` は親が無ければ地方の層をそのまま返すので機構は共通ですが、
`:exclusive` を明示しないと**「親を書き忘れた孤児」と区別できません**。
テストが両者を別々に検査します。

### 入口の作りは4通り

`gate/compare-sector` が同一業種を横断で並べ、`:licence/regime` が型を持ちます。
法律サービスだけで:

| 法域 | 型 | 中身 |
|---|---|---|
| JPN | `:prior-authorisation` | 弁護士法72条。**法人が名義人になれない** |
| GBR | `:reserved-activities-only` | LSA 2007。6類型だけ閉じる |
| DEU | `:prohibition-with-registration-exceptions` | RDG §3 原則禁止＋§10 の分野別登録。**法人も登録できる** |
| FRA | `:prohibition-with-registration-exceptions` | 独と同型だが、開けるのが**人の資格**（法学士等）。法人は不可 |
| CAN-ON | `:prior-authorisation` | LSA s.26.1。**一つの職業内に2段**（弁護士 / paralegal） |

不動産仲介では GBR が `:negative-licensing`（事前免許なし・禁止命令で事後排除）に
なり4型そろいます。フランス art.54 は『**à titre habituel et rémunéré**』を
構成要件に組み込んでおり、**有償性と反復性が条文レベルの要件**なのは収録法域で
フランスだけです。

日本の許認可業種は数百、都道府県は47、世界の法域は約200あり、これは27件にすぎません。
各エントリの `:known-gaps` が何を確認していないかを名指ししています。

## 使い方

```bash
clojure -M:test   # 76 tests / 1,151 assertions
clojure -M:lint   # clj-kondo, errors fail
```

```clojure
(require '[cloud-itonami.licensed-operator.gate :as gate])

(gate/summary)                                  ; どこでどの経路が開くか一覧
(gate/citations "JPN" :sector/legal-services :route/defer)
(gate/plan {:jurisdiction "JPN" :sector :sector/legal-services
            :holder holder :matter matter})
```

## Related

- [`cloud-itonami-isic-6910-legalsupport`](https://github.com/cloud-itonami/cloud-itonami-isic-6910-legalsupport) — この形の最初の実証（弁護士）であり、**最初の consumer**。reviewer 検査はこの repo から抽出されたもので、2026-07-26 に legalsupport 側の自前実装を撤去して `licensee/verify` への委譲に切り替えた
- [`cloud-itonami-regulatory-tracker`](https://github.com/cloud-itonami/cloud-itonami-regulatory-tracker) — 許認可を**取る**進行の管理（別の問い）
- [`kotoba-lang/kyoninka`](https://github.com/kotoba-lang/kyoninka) — 日本の許認可手続きの procedure-as-data。`:next` の行き先

## License

AGPL-3.0-or-later.
