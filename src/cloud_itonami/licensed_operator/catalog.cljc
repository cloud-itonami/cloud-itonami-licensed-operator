(ns cloud-itonami.licensed-operator.catalog
  "Cited fact table for ONE question, per (jurisdiction, sector):

    自社がこの業をやろうとしたとき、
      (a) 自分が許認可の名義人（principal）になれるのか、
      (b) なれないなら、許認可を持つ第三者を主体に立てて自社はその
          ops 層に徹する（defer）ことができるのか、
      (c) その委譲が成立するために、その有資格者について何が真で
          なければならないのか。

  (b) がこの commons の存在理由である。cloud-itonami の実装済み
  actor の大半は、日本では自社が名義人になれない業種に載っている
  （農業・鉱業・製造・医療・金融・士業）。それらは「できない」で
  終わるのではなく、**有資格者に売る ops 層**としてなら成立しうる。
  その形は `cloud-itonami-isic-6910-legalsupport` が弁護士について
  実証済みで（法務省ガイドライン第4項: 弁護士が自ら精査し必要に応じ
  自ら修正するなら、他の全要件に該当しても違反しない）、ここはその
  一般化にあたる。

  カタログの規約は legalsupport と同一で、意図的に同じ:

    1. 捏造しない。(法域, 業種) が無ければ spec-basis は無い。
       `cloud-itonami.licensed-operator.gate` が既定で拒否する。
    2. `:rule/verification` が `:secondary-source-only` のルールは
       **制限的 verdict しか支えられない**。許可方向の結論の唯一の
       根拠にはできない（gate が `:unsettled` へ降格する）。
       慎重方向に誤ればカバレッジを失うだけだが、許可方向に誤れば
       無許可営業の刑事責任を利用者に負わせる。
    3. カバレッジは報告する。未収載は「規制が無い」ではなく「未調査」。

  収録日: 2026-07-26。")

;; ---------------------------------------------------------------------------
;; Vocabulary
;; ---------------------------------------------------------------------------

(def sectors
  {:sector/legal-services
   "法律事務の取扱い（ISIC 6910）"
   :sector/second-hand-dealing
   "古物営業 — 中古品の売買・交換を業として行うこと（ISIC 4774）"
   :sector/industrial-waste-collection
   "産業廃棄物の収集運搬（ISIC 3811）"
   :sector/warehousing
   "倉庫業 — 寄託を受けた物品の倉庫における保管（JSIC 4721 冷蔵倉庫業を含む）"
   :sector/food-manufacture
   "食品製造業 — 食品衛生法の要許可32業種（ISIC 1030/1071-1075/562 等）"
   :sector/medical-practice
   "医業 — 医療機関の開設と診療（ISIC 862/869）"
   :sector/employment-placement
   "有料職業紹介事業（ISIC 7810）"
   :sector/real-estate-brokerage
   "宅地建物取引業（ISIC 6820）"
   :sector/travel-agency
   "旅行業・旅行業者代理業（ISIC 7911）"})

(def routes
  {:route/principal
   "自社が許認可の名義人になり、自社が事業主体として営む。"
   :route/defer
   "許認可を持つ第三者を事業主体に立て、自社はその判断を代替しない ops 層に徹する。"})

(def licensee-requirements
  "`:route/defer` が成立するために有資格者側で真でなければならない
  こと。`cloud-itonami.licensed-operator.licensee` が実際に検査する。
  legalsupport の governor が弁護士について課していた検査を、業種に
  依存しない語彙に開いたもの。"
  {:req/licence-verified
   "有資格者の許認可が検証済みであること（自己申告ではなく、番号・
    名義・有効性を確認した記録があること）。"
   :req/same-jurisdiction
   "有資格者の許認可の法域が、案件の法域と一致すること。他法域の
    資格者を立てても委譲は成立しない — 越境は ops 層が無資格営業に
    化ける典型的な経路。"
   :req/scope-covers
   "許認可の範囲（品目・区分・業務範囲）が、当該行為をカバーして
    いること。許可はあるが区分が違う、は許可が無いのと同じ。"
   :req/not-expired
   "案件時点で許認可が有効期間内であること。"
   :req/personally-decided
   "有資格者本人が自ら判断し、必要に応じて自ら是正した記録がある
    こと。名義を借りただけ・ハンコを押しただけは委譲ではない。"
   :req/written-contract
   "法が委託の書面契約を要求する場合、その契約が存在すること。"})

(def verifications
  #{:primary-source-read :official-url-retrieved :secondary-source-only})

(def verdicts #{:admissible :conditional :prohibited :unsettled})

;; ---------------------------------------------------------------------------
;; Catalog
;; ---------------------------------------------------------------------------

(def catalog
  "[jurisdiction sector] -> entry.

  JPN の3業種のみ収録。これは日本の業法の全体像ではなく、**2026-07-26
  時点で一次/公式出典まで確認できた範囲**である。未収載業種について
  gate は既定で拒否する。"
  {["JPN" :sector/legal-services]
   {:jurisdiction "JPN"
    :sector :sector/legal-services
    :licence
    {:licence/name "弁護士資格（日本弁護士連合会・弁護士会への登録）"
     :licence/law "弁護士法第72条（非弁護士の法律事務の取扱い等の禁止）"
     :licence/authority "日本弁護士連合会 / 各弁護士会"
     :licence/obtainable-by-company? false
     :licence/note "法人が取得できる種類の許認可ではない（自然人の資格）。"}
    :rules
    [{:rule/id "jpn.bengoshi-ho-72"
      :rule/title "弁護士法第72条"
      :rule/quote
      (str "弁護士又は弁護士法人でない者は、報酬を得る目的で……その他一般の"
           "法律事件に関して鑑定、代理、仲裁若しくは和解その他の法律事務を"
           "取り扱い、又はこれらの周旋をすることを業とすることができない。")
      :rule/url "https://www8.cao.go.jp/kisei-kaikaku/kisei/meeting/wg/2501_06ai/260109/ai06_05.pdf"
      :rule/url-provenance :official-government-document
      :rule/verification :primary-source-read
      :rule/verification-note
      "法務省資料（令和8年1月9日、規制改革推進会議 資料5）の逐語引用を読了。"
      :rule/retrieved-at "2026-07-26"}
     {:rule/id "jpn.moj-ai-contract-guideline-2023"
      :rule/title "AI等を用いた契約書等関連業務支援サービスの提供と弁護士法第72条との関係について"
      :rule/url "https://www.moj.go.jp/content/001400675.pdf"
      :rule/url-provenance :official-government-site
      :rule/verification :primary-source-read
      :rule/verification-note "法務省ガイドライン（令和5年8月）PDF 全6ページ読了。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "第4項: サービスを弁護士・弁護士法人に提供し、当該弁護士が利用結果も"
           "踏まえて自ら精査し必要に応じ自ら修正する方法で利用するときは、"
           "他の全要件に該当する場合であっても72条に違反しない。"
           "委譲成立の条件が当局の文書に明記されている稀な例。")}]

    :route/principal
    {:verdict :prohibited
     :basis ["jpn.bengoshi-ho-72"]
     :condition "法人が弁護士資格を取得する経路は無い。名義人にはなれない。"}

    :route/defer
    {:verdict :admissible
     :basis ["jpn.moj-ai-contract-guideline-2023"]
     :condition
     (str "弁護士・弁護士法人を主体とし、当該弁護士が成果物を自ら精査し"
          "必要に応じ自ら修正する方法で用いること（ガイドライン4(1)）。")
     :licensee-requirements
     #{:req/licence-verified :req/same-jurisdiction :req/personally-decided}
     :implemented-by "cloud-itonami-isic-6910-legalsupport"}

    :known-gaps
    ["弁護士法27条（非弁提携）の原文・射程が未検証"
     "日弁連 弁護士等の業務広告に関する規程が未取得"]}

   ;; -----------------------------------------------------------------------
   ["JPN" :sector/second-hand-dealing]
   {:jurisdiction "JPN"
    :sector :sector/second-hand-dealing
    :licence
    {:licence/name "古物商許可"
     :licence/law "古物営業法第3条"
     :licence/authority "都道府県公安委員会"
     :licence/window "主たる営業所の所在地を管轄する警察署（防犯係）"
     :licence/fee-jpy 19000
     :licence/obtainable-by-company? true
     :licence/kyoninka-procedure :kobutsu-marunouchi
     :licence/valid-years nil
     :licence/note
     (str "法人でも取得できる。ITAD（法人PCの買取リユース）はこの区分に載る。"
          "手続きの data は kotoba-lang/kyoninka の :kobutsu-marunouchi。")}
    :rules
    [{:rule/id "jpn.keishicho-kobutsu-kyoka"
      :rule/title "古物商許可申請（警視庁 公式手続きページ）"
      :rule/url "https://www.keishicho.metro.tokyo.lg.jp/tetsuzuki/kobutsu/tetsuzuki/kyoka.html"
      :rule/url-provenance :official-police-site
      :rule/verification :official-url-retrieved
      :rule/verification-note
      "警視庁の手続きページを取得して読了（2026-07-26）。手数料・窓口・法人必要書類を確認。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "申請先は主たる営業所の所在地を管轄する警察署（防犯係）。手数料 19,000 円。"
           "法人の必要書類は 定款 / 登記事項証明書 / 役員全員および営業所管理者の"
           "略歴書・本籍記載住民票の写し・誓約書・身分証明書 / URL使用権限疎明資料"
           "（該当営業形態のみ）。根拠条文として古物営業法施行規則第1条の3第3項各号、"
           "古物営業法第13条第1項を挙げる。"
           "※『どの行為に許可が要るか』の範囲はこのページには記載が無い。")}
     {:rule/id "jpn.kobutsu-eigyo-ho-3"
      :rule/title "古物営業法 第3条（許可）"
      :rule/instrument "古物営業法（昭和24年法律第108号）"
      :rule/quote
      (str "前条第二項第一号又は第二号に掲げる営業を営もうとする者は、"
           "都道府県公安委員会（以下「公安委員会」という。）の許可を受けなければならない。")
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和二十四年法律第百八号;article=3"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API（v1 articles）から条文本文を取得して読了。"
      :rule/retrieved-at "2026-07-26"}
     {:rule/id "jpn.kobutsu-eigyo-ho-2"
      :rule/title "古物営業法 第2条（定義）"
      :rule/instrument "古物営業法（昭和24年法律第108号）"
      :rule/quote
      (str "２ この法律において「古物営業」とは、次に掲げる営業をいう。"
           "一 古物を売買し、若しくは交換し、又は委託を受けて売買し、若しくは交換する"
           "営業であつて、古物を売却すること又は自己が売却した物品を当該売却の相手方から"
           "買い受けることのみを行うもの以外のもの　"
           "二 古物市場（古物商間の古物の売買又は交換のための市場をいう。）を経営する営業　"
           "三 古物の売買をしようとする者のあつせんを競りの方法…により行う営業"
           "（前号に掲げるものを除く。以下「古物競りあつせん業」という。）")
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和二十四年法律第百八号;article=2"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から条文本文を取得して読了。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "許可の対象は2条2項の1号（売買・交換・委託売買）と2号（古物市場の経営）のみ。"
           "**3号の古物競りあつせん業は3条の許可対象から外れている**（3条が1号・2号だけを"
           "挙げているため）。自社が1号・2号の営業の当事者にならなければ、"
           "3条の許可義務は及ばない。")}]

    :route/principal
    {:verdict :conditional
     :basis ["jpn.keishicho-kobutsu-kyoka"]
     :condition
     (str "古物商許可を取得済みであること。法人が取得できる許可であり、"
          "手数料 19,000 円・管轄警察署申請という取得経路が公式に確認できている。"
          "取得前は名義人になれない。")}

    :route/defer
    {:verdict :conditional
     :basis ["jpn.kobutsu-eigyo-ho-2" "jpn.kobutsu-eigyo-ho-3"]
     :condition
     (str "自社が2条2項1号の営業（古物の売買・交換・委託を受けての売買交換）"
          "および2号（古物市場の経営）の当事者にならないこと。3条の許可義務は"
          "この2つにしか及ばない。許可を持つ古物商が売買の当事者となり、自社は"
          "システム提供・物流手配に徹する形であれば3条の外に立つ。"
          "ただし3号の古物競りあつせん業（競りの方法によるあつせん）に該当する"
          "形態を採る場合、3条の許可対象外である代わりに別途の規律"
          "（届出等、法21条の7 前後）が及ぶ — その条文は未取得。")
     :licensee-requirements
     #{:req/licence-verified :req/same-jurisdiction :req/scope-covers}}

    :known-gaps
    ["古物競りあつせん業の届出義務（法21条の7 前後）の条文が未取得"
     "営業所の実体要件（サービスオフィスの適格性）は管轄署への要確認事項として kyoninka が保持"
     "標準処理期間が警視庁公式ページに記載なし（法定40日は未確認）"]}

   ;; -----------------------------------------------------------------------
   ["JPN" :sector/industrial-waste-collection]
   {:jurisdiction "JPN"
    :sector :sector/industrial-waste-collection
    :licence
    {:licence/name "産業廃棄物収集運搬業許可"
     :licence/law "廃棄物の処理及び清掃に関する法律 第14条第1項"
     :licence/authority "都道府県知事（東京都の場合 東京都環境局 資源循環推進部）"
     :licence/fee-jpy 81000
     :licence/obtainable-by-company? true
     :licence/kyoninka-procedure :sanpai-shuun-tokyo
     :licence/valid-years {:min 5 :basis "廃掃法14条2項（五年を下らない期間ごとに更新）"}
     :licence/exemptions
     [{:exemption/id :own-waste-self-transport
       :exemption/detail
       (str "14条1項但書 —「事業者（自らその産業廃棄物を運搬する場合に限る。）…"
            "については、この限りでない」。**自ら排出した産業廃棄物を自ら運搬する"
            "排出事業者には許可が要らない。** ITAD にとってこれは決定的な分岐で、"
            "顧客の PC を引き取るなら排出事業者は顧客であって自社ではないため"
            "この但書に乗れない。買取（古物）スキームなら廃棄物ですらなくなる。"
            "kyoninka が保持する『廃棄物該当性』の legal-question は、"
            "この但書のどちら側に立つかを決める問いにほかならない。")}
      {:exemption/id :moppara-recycling
       :exemption/detail
       "同但書 — 専ら再生利用の目的となる産業廃棄物のみの収集運搬を業として行う者も対象外。"}]
     :licence/note
     (str "法人でも取得できる。講習会修了証の提出が求められる。"
          "手続きの data は kotoba-lang/kyoninka の :sanpai-shuun-tokyo。")}
    :rules
    [{:rule/id "jpn.tokyo-sanpai-license-application"
      :rule/title "産業廃棄物処理業 許可申請（東京都環境局 公式ページ）"
      :rule/url "https://www.kankyo.metro.tokyo.lg.jp/resource/industrial_waste/on_processor/license_application"
      :rule/url-provenance :official-prefectural-site
      :rule/verification :official-url-retrieved
      :rule/verification-note
      (str "東京都環境局の申請ページを取得して読了（2026-07-26）。"
           "手数料 81,000 円と講習会修了証の要求を確認。"
           "根拠法条・許可権者・標準処理期間・有効期間はこのページには記載が無い。")
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "新規許可申請の手数料は産業廃棄物・特別管理産業廃棄物ともに 81,000 円。"
           "令和8年4月1日以降の予約日については講習会の修了証（写し）の提出が必要。")}
     {:rule/id "jpn.haikibutsu-ho-14"
      :rule/title "廃棄物処理法 第14条（産業廃棄物処理業）"
      :rule/instrument "廃棄物の処理及び清掃に関する法律（昭和45年法律第137号）"
      :rule/quote
      (str "産業廃棄物…の収集又は運搬を業として行おうとする者は、当該業を行おうとする"
           "区域（運搬のみを業として行う場合にあつては、産業廃棄物の積卸しを行う区域に"
           "限る。）を管轄する都道府県知事の許可を受けなければならない。"
           "ただし、事業者（自らその産業廃棄物を運搬する場合に限る。）、専ら再生利用の"
           "目的となる産業廃棄物のみの収集又は運搬を業として行う者その他環境省令で"
           "定める者については、この限りでない。")
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和四十五年法律第百三十七号;article=14"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から14条全体を取得して読了。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "許可権者は業を行う区域を管轄する都道府県知事。**但書に3類型の適用除外**"
           "（自ら運搬する排出事業者 / 専ら再生利用目的 / 環境省令で定める者）。"
           "2項により五年を下らない政令期間ごとの更新を要し、更新しなければ失効する。")}]

    :route/principal
    {:verdict :conditional
     :basis ["jpn.tokyo-sanpai-license-application"]
     :condition
     (str "許可を取得済みであること。法人が取得できる許可で、手数料 81,000 円・"
          "講習会修了証という取得経路が公式に確認できている。取得前は名義人になれない。")}

    :route/defer
    {:verdict :conditional
     :basis ["jpn.haikibutsu-ho-14"]
     :condition
     (str "許可業者に委託する形自体は廃掃法が想定する枠組みだが、委託基準"
          "（書面契約・マニフェスト交付等）の条文を原文で未検証。ここを確認する"
          "まで運営者の宣誓を要する。なお ITAD の宅配回収では『廃棄物該当性』"
          "（買取リユース＝古物商の範囲 / 廃棄物＝廃掃法の範囲）自体が未決で、"
          "そもそも収集運搬許可の要否がスキーム設計に依存する — kyoninka の"
          ":legal-questions が行政書士確認事項として保持している。")
     :licensee-requirements
     #{:req/licence-verified :req/same-jurisdiction :req/scope-covers
       :req/not-expired :req/written-contract}}

    :known-gaps
    ["委託基準（第12条第5項〜、施行令第6条の2）の条文原文が未取得"
     "標準処理期間が東京都公式ページに記載なし（第三者情報の約60日は未確認）"
     "14条2項の「政令で定める期間」の具体値が未確認（五年を下らない、とのみ確認）"
     "廃棄物該当性（ITAD 宅配回収スキーム）が未決 — 14条1項但書のどちら側に立つかを決める問い"]}

   ;; -----------------------------------------------------------------------
   ["JPN" :sector/warehousing]
   {:jurisdiction "JPN"
    :sector :sector/warehousing
    :licence
    {:licence/name "倉庫業の登録"
     :licence/law "倉庫業法 第3条"
     :licence/authority "国土交通大臣（地方運輸局長に委任される場合がある）"
     :licence/obtainable-by-company? true
     :licence/note
     (str "登録制。倉庫施設が保管物品に応じた基準を満たすことと、倉庫ごとに"
          "倉庫管理主任者を選任することが要件として国交省ページに明記されている。"
          "冷蔵倉庫については標準冷蔵倉庫寄託約款・冷蔵施設明細書等の別書類がある。")}

    ;; 冷蔵倉庫業は倉庫業法だけでは足りない。食品衛生法の営業届出も要る。
    :additional-gates
    [{:licence/name "食品衛生法の営業届出（冷凍又は冷蔵倉庫業）"
      :licence/law "食品衛生法（令和3年6月1日施行の営業届出制度）"
      :licence/authority "管轄の保健所"
      :licence/obtainable-by-company? true
      :licence/applies-when :refrigerated
      :licence/note
      (str "東京都の公式資料は「届出が不要な業種」として『食品又は添加物の貯蔵"
           "又は運搬のみをする営業』を挙げつつ、**冷凍又は冷蔵倉庫業は届出が必要な"
           "業種**と明示的に除外している。JSIC 4721（冷蔵倉庫業）はここに当たるため、"
           "倉庫業法の登録とは別に保健所への届出が要る。届出には手数料も有効期間も"
           "無いが、食品衛生責任者の設置と HACCP に沿った衛生管理が伴う。")}]

    :rules
    [{:rule/id "jpn.mlit-soukogyo"
      :rule/title "倉庫業法（国土交通省 物流ページ）"
      :rule/url "https://www.mlit.go.jp/seisakutokatsu/freight/butsuryu05100.html"
      :rule/url-provenance :official-government-site
      :rule/verification :official-url-retrieved
      :rule/verification-note
      (str "国交省の倉庫業法ページを取得して読了（2026-07-26）。法第3条に基づく"
           "登録と、施設基準・倉庫管理主任者の選任要件を確認。登録権者・無登録営業の"
           "罰則・標準処理期間はこのページには記載が無い。")
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "倉庫業は法第3条に基づく登録制。保管する物品に応じた倉庫施設の基準を"
           "クリアした倉庫であること、倉庫ごとに一定の要件を備えた倉庫管理主任者を"
           "選任すること等が必要。冷蔵倉庫向けの標準冷蔵倉庫寄託約款・冷蔵施設明細書"
           "等が掲載されている。")}
     {:rule/id "jpn.tokyo-shokuhin-kyoka-todokede"
      :rule/title "食品衛生法の営業許可・営業届出の区分（東京都保健医療局『食品衛生の窓』）"
      :rule/url "https://www.hokeniryo1.metro.tokyo.lg.jp/shokuhin/kaisei/files/kyoka_todokede_todokede.pdf"
      :rule/url-provenance :official-prefectural-site
      :rule/verification :primary-source-read
      :rule/verification-note "東京都の公式 PDF（要許可32業種の一覧を含む全2ページ）を読了。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "令和3年6月1日以降、要許可32業種／要届出業種／届出不要業種の3区分。"
           "届出不要業種の2に『食品又は添加物の貯蔵又は運搬のみをする営業』が挙がるが、"
           "**冷凍又は冷蔵倉庫業は届出が必要な業種として明示的に除外**されている。")}
     {:rule/id "jpn.soukogyo-ho-3"
      :rule/title "倉庫業法 第3条（登録）"
      :rule/instrument "倉庫業法（昭和31年法律第121号）"
      :rule/quote "倉庫業を営もうとする者は、国土交通大臣の行う登録を受けなければならない。"
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和三十一年法律第百二十一号;article=3"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から条文本文を取得して読了。"
      :rule/retrieved-at "2026-07-26"}
     {:rule/id "jpn.soukogyo-ho-2"
      :rule/title "倉庫業法 第2条（定義）"
      :rule/instrument "倉庫業法（昭和31年法律第121号）"
      :rule/quote
      (str "２ この法律で「倉庫業」とは、寄託を受けた物品の倉庫における保管"
           "（保護預りその他の他の営業に付随して行われる保管又は携帯品の一時預り"
           "その他の比較的短期間に限り行われる保管であつて…第六条第一項第四号の"
           "基準に適合する施設又は設備を有する倉庫において行うことが必要でないと"
           "認められるものとして政令で定めるものを除く。）を行う営業をいう。")
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和三十一年法律第百二十一号;article=2"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から条文本文を取得して読了。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "倉庫業の構成要素は『**寄託を受けた**物品の倉庫における保管を行う営業』。"
           "寄託を受ける当事者にならなければ3条の登録義務は及ばない。"
           "他の営業に付随する保管・短期の一時預りは政令で除外されうる。")}
     {:rule/id "jpn.soukogyo-ho-6"
      :rule/title "倉庫業法 第6条（登録の拒否）"
      :rule/instrument "倉庫業法（昭和31年法律第121号）"
      :rule/quote
      (str "国土交通大臣は、第四条の規定による登録の申請が次の各号のいずれかに"
           "該当する場合には、その登録を拒否しなければならない。"
           "一 申請者が一年以上の拘禁刑に処せられ、その執行を終わり、又は執行を"
           "受けることがなくなつた日から二年を経過しない者であるとき。"
           "二 申請者が第二十一条の規定による登録の取消しを受け、その取消しの日から"
           "二年を経過しない者であるとき。"
           "三 申請者が法人である場合において、その役員が前二号のいずれかに"
           "該当する者であるとき。"
           "四 倉庫の施設又は設備が倉庫の種類に応じて国土交通省令で定める基準に"
           "適合しないとき。"
           "五 第十一条の規定による倉庫管理主任者を確実に選任すると認められないとき。")
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和三十一年法律第百二十一号;article=6"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から6条を取得して読了。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "登録は羈束処分で、拒否事由は5号に限定列挙されている。"
           "**法人の場合その役員の欠格が申請者の欠格になる**（3号）。"
           "施設基準は国交省令（4号）、倉庫管理主任者の確実な選任（5号）が実体要件。")}]

    :route/principal
    {:verdict :conditional
     :basis ["jpn.soukogyo-ho-3" "jpn.soukogyo-ho-6" "jpn.mlit-soukogyo"
             "jpn.tokyo-shokuhin-kyoka-todokede"]
     :condition
     (str "倉庫業法第3条の登録を受けていること。登録は羈束処分だが6条の拒否事由に"
          "当たらないことが要る —— 施設・設備が国交省令の基準に適合し（6条1項4号）、"
          "倉庫管理主任者を確実に選任すると認められ（5号）、**法人ならその役員が"
          "欠格に当たらない**（3号）こと。冷蔵倉庫であれば加えて食品衛生法の営業届出を"
          "済ませていること — :additional-gates を参照。片方だけでは足りない。")}

    :route/defer
    {:verdict :conditional
     :basis ["jpn.soukogyo-ho-2" "jpn.soukogyo-ho-3"]
     :condition
     (str "自社が寄託を受ける当事者にならないこと。2条2項の『倉庫業』は"
          "**寄託を受けた**物品の保管を行う営業と定義されているため、"
          "登録倉庫業者が寄託の相手方となり、自社は在庫調整・受発注・"
          "温度記録の ops 層に徹する形であれば3条の登録義務は及ばない。"
          "冷蔵倉庫を扱う場合、寄託を受けなくとも食品衛生法側の届出の要否は"
          "別途判断が要る（:additional-gates 参照）。")
     :licensee-requirements
     #{:req/licence-verified :req/same-jurisdiction :req/scope-covers
       :req/written-contract}}

    :known-gaps
    ["無登録営業の罰則（1年以下の懲役 or 100万円以下の罰金とされる）が公式未確認"
     "登録の標準処理期間（大臣権限3か月・地方運輸局長権限2か月とされる）が公式未確認"
     "倉庫業法第三条の登録の基準等に関する告示（国交省告示第43号）が未取得"
     "6条1項4号が委ねる国土交通省令の施設基準の具体値が未取得"
     "2条2項が除外する『政令で定めるもの』の具体的範囲が未確認"]}

   ;; -----------------------------------------------------------------------
   ["JPN" :sector/food-manufacture]
   {:jurisdiction "JPN"
    :sector :sector/food-manufacture
    :licence
    {:licence/name "食品衛生法の営業許可（要許可32業種）"
     :licence/law "食品衛生法（令和3年6月1日施行の営業許可制度）"
     :licence/authority "営業所を所管する保健所"
     :licence/obtainable-by-company? true
     :licence/note
     (str "許可には手数料・更新手続き・営業施設の基準・衛生管理の基準がすべて伴う"
          "（届出には手数料も更新も施設基準も無い）。cloud-itonami の実装済み食品系"
          "actor が載る区分: 11 菓子製造業（isic-1071/1073）/ 24 麺類製造業"
          "（isic-1074）/ 25 そうざい製造業・26 複合型そうざい製造業（isic-1075, 562）/"
          "27 冷凍食品製造業・28 複合型冷凍食品製造業 / 16 水産製品製造業（isic-1020）/"
          "21 酒類製造業（isic-1101。ただし酒類は酒税法の製造免許が別途要る）/"
          "1 飲食店営業（isic-5610）。")}
    :rules
    [{:rule/id "jpn.tokyo-shokuhin-kyoka-list"
      :rule/title "食品衛生法の要許可32業種一覧（東京都保健医療局『食品衛生の窓』）"
      :rule/url "https://www.hokeniryo1.metro.tokyo.lg.jp/shokuhin/kaisei/files/kyoka_todokede_todokede.pdf"
      :rule/url-provenance :official-prefectural-site
      :rule/verification :primary-source-read
      :rule/verification-note "東京都の公式 PDF 全2ページを読了。32業種の番号と名称を直接確認。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "令和3年6月1日以降の要許可32業種: 1飲食店営業 2調理機能を有する自動販売機 "
           "3食肉販売業 4魚介類販売業 5魚介類競り売り営業 6集乳業 7乳処理業 "
           "8特別牛乳搾取処理業 9食肉処理業 10食品の放射線照射業 11菓子製造業 "
           "12アイスクリーム類製造業 13乳製品製造業 14清涼飲料水製造業 15食肉製品製造業 "
           "16水産製品製造業 17氷雪製造業 18液卵製造業 19食用油脂製造業 "
           "20みそ又はしょうゆ製造業 21酒類製造業 22豆腐製造業 23納豆製造業 "
           "24麺類製造業 25そうざい製造業 26複合型そうざい製造業 27冷凍食品製造業 "
           "28複合型冷凍食品製造業 29漬物製造業 30密封包装食品製造業 31食品の小分け業 "
           "32添加物製造業。許可・届出とも食品衛生責任者の設置と HACCP に沿った衛生管理が要る。")}
     {:rule/id "jpn.tokyo-shokuhin-window"
      :rule/title "食品衛生法の営業許可と届出（東京都保健医療局）"
      :rule/url "https://www.hokeniryo1.metro.tokyo.lg.jp/shokuhin/kyokatodokede/index.html"
      :rule/url-provenance :official-prefectural-site
      :rule/verification :official-url-retrieved
      :rule/verification-note
      "都の公式ページを取得して読了。申請先が「営業所を所管する保健所」であることと施行日を確認。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary "令和3年6月1日から新たな営業許可制度・営業届出制度が開始。申請先は営業所を所管する保健所。"}
     {:rule/id "jpn.shokuhin-eisei-ho-55"
      :rule/title "食品衛生法 第55条（営業の許可）"
      :rule/instrument "食品衛生法（昭和22年法律第233号）"
      :rule/quote
      (str "前条に規定する営業を営もうとする者は、厚生労働省令で定めるところにより、"
           "都道府県知事の許可を受けなければならない。……"
           "都道府県知事は、第一項の許可に五年を下らない有効期間その他の必要な条件を"
           "付けることができる。")
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和二十二年法律第二百三十三号;article=55"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から条文本文を取得して読了。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "許可権者は都道府県知事（政令市等では市長等）。施設が54条の基準に合えば"
           "許可しなければならない羈束処分だが、法違反での処罰から2年未満・"
           "許可取消しから2年未満・**その役員にそれらの該当者がいる法人**は"
           "許可を与えないことができる（欠格）。有効期間は五年を下らない範囲で付される。")}
     {:rule/id "jpn.shokuhin-eisei-ho-54"
      :rule/title "食品衛生法 第54条（施設基準）"
      :rule/instrument "食品衛生法（昭和22年法律第233号）"
      :rule/quote
      (str "都道府県は、公衆衛生に与える影響が著しい営業（食鳥処理の事業を除く。）"
           "であつて、政令で定めるものの施設につき、厚生労働省令で定める基準を"
           "参酌して、条例で、公衆衛生の見地から必要な基準を定めなければならない。")
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和二十二年法律第二百三十三号;article=54"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から54条を取得して読了。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "55条が『前条に規定する営業』として参照している元の条文。"
           "**要許可業種は政令が定め、施設基準は都道府県の条例が定める**"
           "（厚労省令を参酌）。したがって施設基準には構造的に自治体差がある —"
           "全国一律の基準を前提に設計してはならない。")}
     {:rule/id "jpn.shokuhin-eisei-ho-57"
      :rule/title "食品衛生法 第57条（営業の届出）"
      :rule/instrument "食品衛生法（昭和22年法律第233号）"
      :rule/quote
      (str "営業（第五十四条に規定する営業、公衆衛生に与える影響が少ない営業で政令で"
           "定めるもの及び食鳥処理の事業を除く。）を営もうとする者は、厚生労働省令で"
           "定めるところにより、あらかじめ、その営業所の名称及び所在地その他厚生労働省令"
           "で定める事項を都道府県知事に届け出なければならない。")
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和二十二年法律第二百三十三号;article=57"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から条文本文を取得して読了。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "**届出は許可業種の残余ではなく独立の義務**。要許可業種と政令で定める"
           "低リスク営業・食鳥処理を除く『営業』はすべて事前届出の対象になる —"
           "製造を委託して自社は販売だけ、という形でも届出は免れない。")}]

    :route/principal
    {:verdict :conditional
     :basis ["jpn.shokuhin-eisei-ho-55" "jpn.shokuhin-eisei-ho-54"
             "jpn.tokyo-shokuhin-kyoka-list" "jpn.tokyo-shokuhin-window"]
     :condition
     (str "該当する要許可業種について保健所の営業許可を受けていること。法人で取得"
          "できる。営業施設の基準・食品衛生責任者の設置・HACCP に沿った衛生管理が伴い、"
          "更新手続きもある。酒類製造業（21）は加えて酒税法の製造免許が要る点に注意。")}

    :route/defer
    {:verdict :conditional
     :basis ["jpn.shokuhin-eisei-ho-57" "jpn.tokyo-shokuhin-kyoka-list"]
     :condition
     (str "製造は許可を持つ製造者が行い（受託製造 / OEM）、自社は要許可32業種に"
          "当たらない範囲にとどまること。**ただし57条により自社側も事前届出が要る** —"
          "届出は許可業種の残余ではなく独立の義務で、要許可業種と政令で定める"
          "低リスク営業を除く『営業』はすべて対象。どこまでが『製造』かは業態依存で、"
          "31 食品の小分け業が独立した許可業種として立っていることに示されるとおり、"
          "小分け・包装だけでも許可側に落ちうる。")
     :licensee-requirements
     #{:req/licence-verified :req/same-jurisdiction :req/scope-covers
       :req/written-contract}}

    :known-gaps
    ["54条が委ねる政令（要許可業種の指定）と各自治体の条例（施設基準の具体値）が未取得"
     "許可手数料額が未確認（自治体差がある）"
     "57条が除外する『公衆衛生に与える影響が少ない営業で政令で定めるもの』の範囲が未確認"
     "酒税法の製造免許は未収載 — 21 酒類製造業を扱うなら別途調査が要る"]}

   ;; -----------------------------------------------------------------------
   ["JPN" :sector/medical-practice]
   {:jurisdiction "JPN"
    :sector :sector/medical-practice
    :licence
    {:licence/name "医療機関の開設許可 / 医師免許"
     :licence/law "医療法 第7条（開設許可）・第7条第2項及び第54条（営利性の否定）"
     :licence/authority "都道府県知事等"
     :licence/obtainable-by-company? false
     :licence/note
     (str "営利を目的とする法人は開設者になれない。株式会社が医業を営む経路は"
          "無く、医療法人・医師個人等が開設者となる。")}
    :rules
    [{:rule/id "jpn.mhlw-1952-iyu-190"
      :rule/title "厚生省医務局長回答 昭和27年6月24日 医収第190号（公益法人の設立認可について）"
      :rule/quote
      (str "医業はその収益の使途の如何を問わず営利を目的として営むことは許されない"
           "のであって公益事業を行うための資金調達の目的で病院事業を営むことは"
           "許されないものと解する。")
      :rule/url "https://www.mhlw.go.jp/topics/bukyoku/isei/igyou/igyoukeiei/tuchi/270624.pdf"
      :rule/url-provenance :official-government-site
      :rule/verification :primary-source-read
      :rule/verification-note
      (str "厚生労働省サイトの通知 PDF 全2ページを読了（2026-07-26）。"
           "高知県知事照会（昭和27年5月30日 27医第309号）に対する回答で、"
           "照会側が医療法第7条第2項及び同法第54条を根拠に「医業は医療法上"
           "営利事業ではない」と述べている点も同じ文書上で確認した。")
      :rule/retrieved-at "2026-07-26"}
     {:rule/id "jpn.ishi-ho-17"
      :rule/title "医師法 第17条"
      :rule/instrument "医師法（昭和23年法律第201号）"
      :rule/quote "医師でなければ、医業をなしてはならない。"
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和二十三年法律第二百一号;article=17"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から条文本文を取得して読了。全文が一文。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "医業の主体は自然人たる医師に限られる。法人は—医療法人であっても—"
           "医業そのものをなす主体にはなれず、医師を置いて医療機関を開設する形しかない。")}
     {:rule/id "jpn.iryo-ho-7"
      :rule/title "医療法 第7条（開設の許可）"
      :rule/instrument "医療法（昭和23年法律第205号）"
      :rule/quote
      (str "病院を開設しようとするとき、…臨床研修等修了医師…及び…臨床研修等修了"
           "歯科医師…でない者が診療所を開設しようとするとき、又は助産師…でない者が"
           "助産所を開設しようとするときは、開設地の都道府県知事…の許可を"
           "受けなければならない。")
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和二十三年法律第二百五号;article=7"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から7条全体を取得して読了。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "病院は誰が開設する場合でも許可が要る。診療所は臨床研修等修了医師"
           "本人が開設する場合を除き許可が要る — つまり**法人が診療所を開設する"
           "には常に7条の許可が要る**。営利法人がその許可を得られないことは"
           "医収第190号の解釈による。")}
     {:rule/id "jpn.iryo-ho-54"
      :rule/title "医療法 第54条（剰余金配当の禁止）"
      :rule/instrument "医療法（昭和23年法律第205号）"
      :rule/quote "医療法人は、剰余金の配当をしてはならない。"
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和二十三年法律第二百五号;article=54"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から54条を取得して読了。全文が一文。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "医収第190号が引用していた条文の実物。**委譲側の設計を直接縛る** —"
           "医療法人を主体に立てても、そこから剰余金の形で利益を取ることはできない。"
           "いわゆる MS 法人の対価が実質的な配当と評価されれば54条に触れうる。")}]

    :route/principal
    {:verdict :prohibited
     :basis ["jpn.ishi-ho-17" "jpn.mhlw-1952-iyu-190" "jpn.iryo-ho-7"
             "jpn.iryo-ho-54"]
     :condition
     (str "営利を目的として医業を営むことは許されない。株式会社が開設者となる"
          "経路は無い。")}

    :route/defer
    {:verdict :conditional
     :basis ["jpn.ishi-ho-17" "jpn.mhlw-1952-iyu-190"]
     :condition
     (str "医療法人・医師個人等が開設者となり、自社は**医業に当たらない支援**"
          "（設備・システム・事務受託）に徹すること。医師法17条は医業の主体を"
          "医師に限るので、自社の関与が『医業をなす』側に一歩でも入れば成立しない。"
          "加えて54条が医療法人の剰余金配当を禁じているので、**委譲先から利益を"
          "取り出す設計自体が縛られる** —— 業務委託料等が実質的な配当と評価されない"
          "水準・算定根拠であることが要る。MS 法人を介した実質的支配の限界を示す"
          "通知は未収載のため、運営者の宣誓を要する。")
     :licensee-requirements
     #{:req/licence-verified :req/same-jurisdiction :req/personally-decided}}

    :known-gaps
    ["「医業」の外延（どこからが医行為か）の判断基準・通知が未収載"
     "MS 法人（メディカルサービス法人）に関する規律・通知が未収載"
     "昭和27年の回答が現行運用でどこまで維持されているかの後続通知が未確認"]}

   ;; -----------------------------------------------------------------------
   ["JPN" :sector/employment-placement]
   {:jurisdiction "JPN"
    :sector :sector/employment-placement
    :licence
    {:licence/name "有料職業紹介事業の許可"
     :licence/law "職業安定法 第30条"
     :licence/authority "厚生労働大臣"
     :licence/obtainable-by-company? true
     :licence/note
     (str "法人で取得できる。申請書に役員全員の氏名・住所と、事業所ごとに選任する"
          "職業紹介責任者の氏名・住所を記載し、事業所ごとの事業計画書を添付する"
          "（30条2項・3項）。資産要件は厚生労働省令側にあり未取得。")}
    :rules
    [{:rule/id "jpn.shokugyo-anteiho-30"
      :rule/title "職業安定法 第30条（有料職業紹介事業の許可）"
      :rule/instrument "職業安定法（昭和22年法律第141号）"
      :rule/quote
      (str "有料の職業紹介事業を行おうとする者は、厚生労働大臣の許可を"
           "受けなければならない。")
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和二十二年法律第百四十一号;article=30"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から30条全体を取得して読了。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "許可権者は厚生労働大臣。申請には法人の役員全員の氏名住所、事業所名称"
           "所在地、32条の14により選任する職業紹介責任者の氏名住所の記載と、"
           "事業所ごとの事業計画書（求職者の見込数等を記載）の添付を要する。"
           "**「有料の」職業紹介**が許可の対象であり、無料職業紹介は別条の規律。")}
     {:rule/id "jpn.shokugyo-anteiho-4"
      :rule/title "職業安定法 第4条（定義）"
      :rule/instrument "職業安定法（昭和22年法律第141号）"
      :rule/quote
      (str "この法律において「職業紹介」とは、求人及び求職の申込みを受け、"
           "求人者と求職者との間における雇用関係の成立をあつせんすることをいう。"
           "……この法律において「無料の職業紹介」とは、職業紹介に関し、いかなる名義でも、"
           "その手数料又は報酬を受けないで行う職業紹介をいう。"
           "この法律において「有料の職業紹介」とは、無料の職業紹介以外の職業紹介をいう。")
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和二十二年法律第百四十一号;article=4"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から4条を取得して読了。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "職業紹介の核は『雇用関係の成立を**あつせん**する』こと。"
           "有料／無料の別は**いかなる名義でも手数料・報酬を受けるか**で決まる。"
           "30条の許可は『有料の』職業紹介事業にしか及ばないので、"
           "一切の手数料・報酬を受けない構造なら30条の外に立つ"
           "（ただし無料職業紹介事業自体の規律は別条にあり未取得）。")}]

    :route/principal
    {:verdict :conditional
     :basis ["jpn.shokugyo-anteiho-30"]
     :condition
     (str "有料職業紹介事業の許可を得ていること。法人で取得でき、"
          "cloud-itonami-isic-7810（employment agency actor）が載る業種。"
          "職業紹介責任者の選任が要る。")}

    :route/defer
    {:verdict :conditional
     :basis ["jpn.shokugyo-anteiho-4" "jpn.shokugyo-anteiho-30"]
     :condition
     (str "許可を持つ紹介事業者が『雇用関係の成立をあつせん』する主体となり、"
          "自社は候補者管理・スケジューリング等の ops 層に徹すること。"
          "4条の定義から境界は2段になる: ①あつせん行為そのものを行わなければ"
          "そもそも職業紹介でない ②あつせんを行っても**いかなる名義でも手数料・"
          "報酬を受けなければ**『無料の職業紹介』となり、30条の許可（有料に限る）は"
          "及ばない。②に寄る場合、無料職業紹介事業自体の規律（別条、未取得）を"
          "確認すること。")
     :licensee-requirements
     #{:req/licence-verified :req/same-jurisdiction :req/personally-decided
       :req/written-contract}}

    :known-gaps
    ["無料職業紹介事業の規律（第33条系）の条文原文が未取得"
     "許可基準（資産要件・事業所要件）は厚生労働省令側にあり未取得"
     "手数料規制（第32条の3）が未収載"
     "労働者派遣（isic-7820）は別法（労働者派遣法）で未収載"]}

   ;; -----------------------------------------------------------------------
   ["JPN" :sector/real-estate-brokerage]
   {:jurisdiction "JPN"
    :sector :sector/real-estate-brokerage
    :licence
    {:licence/name "宅地建物取引業の免許"
     :licence/law "宅地建物取引業法 第3条"
     :licence/authority "国土交通大臣（2以上の都道府県に事務所）／都道府県知事（1都道府県のみ）"
     :licence/obtainable-by-company? true
     :licence/valid-years {:exact 5 :basis "宅建業法3条2項（免許の有効期間は五年）"}
     :licence/note
     (str "法人で取得できる。事務所の分布で免許権者が変わる。更新申請中に"
          "有効期間が満了しても処分がされるまで従前の免許が効力を持つ（3条4項）。")}
    :rules
    [{:rule/id "jpn.takken-ho-3"
      :rule/title "宅地建物取引業法 第3条（免許）"
      :rule/instrument "宅地建物取引業法（昭和27年法律第176号）"
      :rule/quote
      (str "宅地建物取引業を営もうとする者は、二以上の都道府県の区域内に事務所…を"
           "設置してその事業を営もうとする場合にあつては国土交通大臣の、一の都道府県の"
           "区域内にのみ事務所を設置してその事業を営もうとする場合にあつては当該事務所の"
           "所在地を管轄する都道府県知事の免許を受けなければならない。"
           "２ 前項の免許の有効期間は、五年とする。")
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和二十七年法律第百七十六号;article=3"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から3条全体（6項まで）を取得して読了。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "免許権者は事務所の分布で決まる。有効期間5年で更新制。更新申請中に"
           "満了しても処分までは従前の免許が有効（4項）。大臣免許は登録免許税、"
           "更新は手数料を要する（6項）。")}
     {:rule/id "jpn.takken-ho-2"
      :rule/title "宅地建物取引業法 第2条第2号（宅地建物取引業の定義）"
      :rule/instrument "宅地建物取引業法（昭和27年法律第176号）"
      :rule/quote
      (str "二 宅地建物取引業　宅地若しくは建物（建物の一部を含む。以下同じ。）の"
           "売買若しくは交換又は宅地若しくは建物の売買、交換若しくは貸借の"
           "**代理若しくは媒介**をする行為で業として行うものをいう。")
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和二十七年法律第百七十六号;article=2"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から2条全体（4号まで）を取得して読了。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "**媒介が明文で含まれる**のがこの業種の特徴。自己が売主・買主にならなくとも、"
           "売買・交換・貸借の媒介を業として行えば免許が要る。"
           "なお自ら行う**貸借**（自社物件の賃貸）は定義に入らない"
           "（売買・交換のみが自ら行う類型として挙がる）。")}]

    :route/principal
    {:verdict :conditional
     :basis ["jpn.takken-ho-3"]
     :condition
     (str "宅地建物取引業の免許を受けていること。法人で取得でき、"
          "cloud-itonami-isic-6820（real estate actor）が載る業種。"
          "宅地建物取引士の設置義務・営業保証金または保証協会加入は"
          "別条の規律で未取得。")}

    :route/defer
    {:verdict :conditional
     :basis ["jpn.takken-ho-2" "jpn.takken-ho-3"]
     :condition
     (str "免許を持つ宅建業者が取引の当事者・媒介者となり、自社は物件情報の"
          "整理や内見調整等の ops 層に徹すること。**2条2号が『媒介』を明文で"
          "含んでいるので境界は厳しい** —— 自己が売主・買主にならなくとも、"
          "売買・交換・貸借の媒介を業として行えばその時点で免許が要る。"
          "成立しうるのは、宅建業者の**内部業務**向けにシステムを提供する位置に"
          "とどまる形。取引当事者を引き合わせる機能を持たせた瞬間に媒介側へ倒れる。")
     :licensee-requirements
     #{:req/licence-verified :req/same-jurisdiction :req/not-expired
       :req/written-contract}}

    :known-gaps
    ["「媒介」の外延（どこからが媒介か）の判例・通達が未収載 — 実務上の分岐点"
     "宅地建物取引士の設置義務（第31条の3）が未収載"
     "営業保証金・弁済業務保証金分担金（第25条・第64条の9）が未収載"]}

   ;; -----------------------------------------------------------------------
   ["JPN" :sector/travel-agency]
   {:jurisdiction "JPN"
    :sector :sector/travel-agency
    :licence
    {:licence/name "旅行業の登録"
     :licence/law "旅行業法 第3条"
     :licence/authority "観光庁長官（第3種・地域限定は都道府県知事に委任される場合がある）"
     :licence/obtainable-by-company? true
     :licence/note
     (str "法人で取得できる。3条は旅行業と旅行業者代理業の双方を登録の対象とする。"
          "種別ごとの営業保証金・基準資産額は別条・省令側にあり未取得。")}
    :rules
    [{:rule/id "jpn.ryokogyo-ho-3"
      :rule/title "旅行業法 第3条（登録）"
      :rule/instrument "旅行業法（昭和27年法律第239号）"
      :rule/quote
      (str "旅行業又は旅行業者代理業を営もうとする者は、観光庁長官の行う登録を"
           "受けなければならない。")
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和二十七年法律第二百三十九号;article=3"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から条文本文を取得して読了。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "**旅行業だけでなく旅行業者代理業も登録の対象**。"
           "他社の旅行商品を代理販売する形でも登録を免れない点が、"
           "他業種の『主体にならなければ規制外』という構図と異なる。")}
     {:rule/id "jpn.ryokogyo-ho-2"
      :rule/title "旅行業法 第2条（定義）"
      :rule/instrument "旅行業法（昭和27年法律第239号）"
      :rule/quote
      (str "この法律で「旅行業」とは、**報酬を得て**、次に掲げる行為を行う事業"
           "（専ら運送サービスを提供する者のため、旅行者に対する運送サービスの提供に"
           "ついて、代理して契約を締結する行為を行うものを除く。）をいう。……"
           "三 旅行者のため、運送等サービスの提供を受けることについて、代理して契約を"
           "締結し、媒介をし、又は取次ぎをする行為　"
           "四 運送等サービスを提供する者のため、旅行者に対する運送等サービスの提供に"
           "ついて、代理して契約を締結し、又は媒介をする行為　……"
           "九 旅行に関する相談に応ずる行為")
      :rule/url "https://laws.e-gov.go.jp/api/1/articles;lawNum=昭和二十七年法律第二百三十九号;article=2"
      :rule/url-provenance :official-legislation-api
      :rule/verification :primary-source-read
      :rule/verification-note "e-Gov 法令 API から2条全体（9号まで＋代理業の定義）を取得して読了。"
      :rule/retrieved-at "2026-07-26"
      :rule/summary
      (str "柱書の要件は『**報酬を得て**』＋各号の行為。3号（旅行者のための代理・媒介・"
           "取次ぎ）と4号（提供者のための代理・媒介）が広く、旅行者に向いた"
           "プラットフォームはほぼ確実にどちらかに当たる。9号は『旅行に関する相談に"
           "応ずる行為』まで含む。")}]

    :route/principal
    {:verdict :conditional
     :basis ["jpn.ryokogyo-ho-3"]
     :condition
     (str "旅行業の登録を受けていること。法人で取得でき、"
          "cloud-itonami-isic-7911（travel agency actor）が載る業種。")}

    :route/defer
    {:verdict :conditional
     :basis ["jpn.ryokogyo-ho-2" "jpn.ryokogyo-ho-3"]
     :condition
     (str "2条1項の柱書が『**報酬を得て**、次に掲げる行為を行う事業』と限定して"
          "いるので、境界は各号の行為に自社が入るかで決まる。3号（旅行者のため"
          "運送等サービスの提供を受けることについて代理・媒介・取次ぎ）と"
          "4号（提供者のため旅行者に対する提供について代理・媒介）が広く、"
          "**旅行者に向いたプラットフォームはほぼ確実にどちらかに当たる**。"
          "9号が『旅行に関する相談に応ずる行為』まで含む点にも注意。"
          "成立しうるのは、登録業者の**内部業務**向けにシステムを提供するなど、"
          "旅行者にも運送等サービス提供者にも向かない位置にとどまる形。")
     :licensee-requirements
     #{:req/licence-verified :req/same-jurisdiction :req/written-contract}}

    :known-gaps
    ["旅行サービス手配業（法2条系）の規律が未整理 — 代理業との区別"
     "種別（第1種〜第3種・地域限定）ごとの営業保証金・基準資産額が未取得"
     "登録権者の委任関係（都道府県知事への委任）が未確認"]}})

;; ---------------------------------------------------------------------------
;; Accessors
;; ---------------------------------------------------------------------------

(defn entry
  "The catalog entry for `[jid sector]`, or nil. nil means NO spec-basis."
  [jid sector]
  (get catalog [jid sector]))

(defn keys-covered [] (vec (sort-by (juxt first (comp str second)) (keys catalog))))

(defn jurisdictions [] (vec (sort (distinct (map first (keys catalog))))))

(defn rules [jid sector] (get (entry jid sector) :rules []))

(defn rule [jid sector rule-id]
  (first (filter #(= rule-id (:rule/id %)) (rules jid sector))))

(defn verified-rule?
  "許可方向の結論を支えられる検証水準か。"
  [r]
  (contains? #{:primary-source-read :official-url-retrieved}
             (:rule/verification r)))

(defn additional-gates
  "許認可が1つとは限らない。`:licence` が主たるゲートで、ここには**同時に
  満たさなければ営業できない別のゲート**が入る。実例: JSIC 4721 冷蔵倉庫業は
  倉庫業法第3条の登録に加えて食品衛生法の営業届出が要る（東京都の公式資料が
  『貯蔵・運搬のみの営業』を届出不要としつつ冷凍・冷蔵倉庫業を明示的に除外して
  いる）。主たる許認可だけを見て『取れた＝営業できる』と判断させないために、
  カタログとして別立てで持つ。"
  [jid sector]
  (get (entry jid sector) :additional-gates []))

(defn known-gaps [jid sector] (get (entry jid sector) :known-gaps []))

(defn coverage
  "正直なカバレッジ報告。未収載の (法域, 業種) は『規制が無い』ではなく
  『未調査』。日本の業法だけでも許認可業種は数百あり、ここにあるのは
  一次/公式出典まで確認できた3件にすぎない。"
  ([] (coverage (keys-covered)))
  ([requested]
   (let [have (filter catalog requested)
         missing (remove catalog requested)
         all (keys-covered)
         rs (mapcat (fn [[j s]] (rules j s)) all)]
     {:requested (count requested)
      :covered (count have)
      :covered-keys (vec have)
      :missing-keys (vec missing)
      :rule-count (count rs)
      :rules-by-verification (frequencies (map :rule/verification rs))
      :known-gaps (into {} (for [[j s] all
                                 :let [g (known-gaps j s)]
                                 :when (seq g)]
                             [[j s] g]))
      :note
      (str "収録 " (count all) " 件（(法域, 業種)の組）・" (count rs) " ルール。"
           "未収載の組について gate はいかなる経路も成立と判定しない。")})))
