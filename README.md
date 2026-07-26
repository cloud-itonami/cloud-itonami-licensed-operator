# cloud-itonami-licensed-operator

許認可業種で、自社が名義人になれないとき、**許認可を持つ第三者を主体に立てて
自社はその ops 層に徹する（defer）ことができるか** を判定する `.cljc` commons。

`cloud-itonami-regulatory-tracker` と同じく **plain library** — advisor も
StageGraph も台帳も持たない。何も申請せず、許認可も持たず、官庁にも接触しない。
呼び出し側 actor の governor が結果を見て自分で HOLD/escalate/commit を決める。

**45 tests / 276 assertions green** (`clojure -M:test`)、`clojure -M:lint` clean。

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
| second-hand-dealing | 古物商許可 | 可 | 1 | `:conditional` | `:unsettled` |
| industrial-waste-collection | 産廃収集運搬業許可 | 可 | 1 | `:conditional` | `:conditional` |
| food-manufacture | 食品衛生法 営業許可（32業種） | 可 | 1 | `:conditional` | `:conditional` |
| warehousing | 倉庫業の登録 | 可 | **2** | `:conditional` | `:unsettled` |

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

現在: **6件（法域×業種）・11ルール** — 一次読了5（弁護士法72条 / 法務省ガイドライン /
東京都の食品衛生法 要許可32業種一覧 ×2 / 厚生省医務局長回答 医収第190号）、
公式ページ取得4（警視庁 古物商許可手続き / 東京都環境局 産廃許可申請 /
国交省 倉庫業法 / 東京都 食品衛生の窓）、二次情報のみ2（古物営業法3条・廃掃法14条の
**条文原文が未取得**）。

収録業種は cloud-itonami に実装済み actor が載っているところを優先しました:
`food-manufacture` は isic-1071/1073/1074/1075/1020/562 が、`warehousing` は
jsic-4721 が、`medical-practice` は isic-862/869 が該当します。

日本の許認可業種は数百あり、ここにあるのは一次/公式まで確認できた6件にすぎません。
各エントリは `:known-gaps` で何を確認していないかを名指ししています（倉庫業法・
食品衛生法・医師法17条・医療法7条の**条文原文はいずれも未取得**で、
現在は公式ページと通知に依拠しています）。

## 使い方

```bash
clojure -M:test   # 45 tests / 276 assertions
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

- [`cloud-itonami-isic-6910-legalsupport`](https://github.com/cloud-itonami/cloud-itonami-isic-6910-legalsupport) — この形の最初の実証（弁護士）。当面は自前の検査を持ち続け、触るついでにこちらへ寄せる
- [`cloud-itonami-regulatory-tracker`](https://github.com/cloud-itonami/cloud-itonami-regulatory-tracker) — 許認可を**取る**進行の管理（別の問い）
- [`kotoba-lang/kyoninka`](https://github.com/kotoba-lang/kyoninka) — 日本の許認可手続きの procedure-as-data。`:next` の行き先

## License

AGPL-3.0-or-later.
