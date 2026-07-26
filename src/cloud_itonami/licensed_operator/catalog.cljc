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
   "医業 — 医療機関の開設と診療（ISIC 862/869）"})

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
      :rule/title "古物営業法第3条（許可）"
      :rule/url "https://www.keishicho.metro.tokyo.lg.jp/tetsuzuki/kobutsu/tetsuzuki/kyoka.html"
      :rule/url-provenance :cited-by-official-site
      :rule/verification :secondary-source-only
      :rule/verification-note
      (str "許可義務の根拠条文そのもの（法3条）の原文は未取得。警視庁ページが"
           "挙げるのは施行規則1条の3第3項と法13条1項で、3条の逐語は確認できて"
           "いない。制限的 verdict の根拠にのみ使用可。")
      :rule/retrieved-at "2026-07-26"
      :rule/summary "古物商・古物市場主を営もうとする者は公安委員会の許可を受けなければならない。"}]

    :route/principal
    {:verdict :conditional
     :basis ["jpn.keishicho-kobutsu-kyoka"]
     :condition
     (str "古物商許可を取得済みであること。法人が取得できる許可であり、"
          "手数料 19,000 円・管轄警察署申請という取得経路が公式に確認できている。"
          "取得前は名義人になれない。")}

    :route/defer
    {:verdict :unsettled
     :basis []
     :condition
     (str "許可を持つ古物商を主体に立て、自社はシステム提供に徹する形が"
          "『古物営業を営む』に当たらないかは未検証。古物営業法2条の"
          "『古物営業』の定義と、法3条の『営もうとする者』の解釈を原文で"
          "確認するまで、この経路を成立と判定しない。")
     :licensee-requirements
     #{:req/licence-verified :req/same-jurisdiction :req/scope-covers}}

    :known-gaps
    ["古物営業法 第2条（定義）・第3条（許可）の条文原文が未取得"
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
      :rule/title "廃棄物処理法 第14条第1項（産業廃棄物収集運搬業の許可）"
      :rule/url "https://www.kankyo.metro.tokyo.lg.jp/resource/industrial_waste/on_processor/license_application"
      :rule/url-provenance :cited-in-corpus
      :rule/verification :secondary-source-only
      :rule/verification-note
      (str "条文原文は未取得。kyoninka の収録値および ADR-2607141620 由来。"
           "制限的 verdict の根拠にのみ使用可。")
      :rule/retrieved-at "2026-07-26"
      :rule/summary "産業廃棄物の収集又は運搬を業として行おうとする者は、都道府県知事の許可を受けなければならない。"}]

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
    ["廃棄物処理法 第14条・委託基準（第12条第5項〜、施行令第6条の2）の条文原文が未取得"
     "標準処理期間・有効期間が東京都公式ページに記載なし（第三者情報の約60日・5年は未確認）"
     "廃棄物該当性（ITAD 宅配回収スキーム）が未決 — 行政書士確認待ち"]}

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
           "**冷凍又は冷蔵倉庫業は届出が必要な業種として明示的に除外**されている。")}]

    :route/principal
    {:verdict :conditional
     :basis ["jpn.mlit-soukogyo" "jpn.tokyo-shokuhin-kyoka-todokede"]
     :condition
     (str "倉庫業法第3条の登録を受けていること（施設基準の充足と倉庫管理主任者の"
          "選任を含む）。冷蔵倉庫であれば加えて食品衛生法の営業届出を済ませて"
          "いること — :additional-gates を参照。片方だけでは足りない。")}

    :route/defer
    {:verdict :unsettled
     :basis []
     :condition
     (str "登録倉庫業者に寄託し自社は在庫調整・受発注の ops 層に徹する形が"
          "『倉庫業を営む』に当たらないかは未検証。倉庫業法2条の『倉庫業』の定義と"
          "3条の『営もうとする者』の解釈を原文で確認するまで、この経路を成立と"
          "判定しない。")
     :licensee-requirements
     #{:req/licence-verified :req/same-jurisdiction :req/scope-covers
       :req/written-contract}}

    :known-gaps
    ["倉庫業法 第2条（定義）・第3条（登録）の条文原文が未取得"
     "無登録営業の罰則（1年以下の懲役 or 100万円以下の罰金とされる）が公式未確認"
     "登録の標準処理期間（大臣権限3か月・地方運輸局長権限2か月とされる）が公式未確認"
     "倉庫業法第三条の登録の基準等に関する告示（国交省告示第43号）が未取得"]}

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
      :rule/summary "令和3年6月1日から新たな営業許可制度・営業届出制度が開始。申請先は営業所を所管する保健所。"}]

    :route/principal
    {:verdict :conditional
     :basis ["jpn.tokyo-shokuhin-kyoka-list" "jpn.tokyo-shokuhin-window"]
     :condition
     (str "該当する要許可業種について保健所の営業許可を受けていること。法人で取得"
          "できる。営業施設の基準・食品衛生責任者の設置・HACCP に沿った衛生管理が伴い、"
          "更新手続きもある。酒類製造業（21）は加えて酒税法の製造免許が要る点に注意。")}

    :route/defer
    {:verdict :conditional
     :basis ["jpn.tokyo-shokuhin-kyoka-list"]
     :condition
     (str "製造は許可を持つ製造者が行い（受託製造 / OEM）、自社は食品衛生法上"
          "届出で足りる範囲（食品販売業等）にとどまること。**自社側も届出は要る** —"
          "要許可業種でも届出不要業種でもない営業は届出の対象。どこまでが『製造』か"
          "は業態依存で、31 食品の小分け業が独立した許可業種として立っていることに"
          "示されるとおり、小分け・包装だけでも許可側に落ちうる。")
     :licensee-requirements
     #{:req/licence-verified :req/same-jurisdiction :req/scope-covers
       :req/written-contract}}

    :known-gaps
    ["食品衛生法の条文原文（営業許可・届出の根拠条項）が未取得"
     "施設基準（政令・条例）が未取得"
     "許可の有効期間・更新間隔・手数料額が未確認（自治体差がある）"
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
      :rule/retrieved-at "2026-07-26"}]

    :route/principal
    {:verdict :prohibited
     :basis ["jpn.mhlw-1952-iyu-190"]
     :condition
     (str "営利を目的として医業を営むことは許されない。株式会社が開設者となる"
          "経路は無い。")}

    :route/defer
    {:verdict :conditional
     :basis ["jpn.mhlw-1952-iyu-190"]
     :condition
     (str "医療法人・医師個人等が開設者となり、自社は医行為に当たらない支援"
          "（設備・システム・事務受託）に徹すること。ただし、いわゆる MS 法人を"
          "介した実質的支配の規律や、医療法人への経営関与の限界を条文・通知で"
          "未検証。医行為の範囲（医師法17条）も原文未取得。運営者の宣誓を要する。")
     :licensee-requirements
     #{:req/licence-verified :req/same-jurisdiction :req/personally-decided}}

    :known-gaps
    ["医師法第17条（医業の独占）の条文原文が未取得"
     "医療法第7条・第54条の条文原文が未取得（上記通知が引用しているのみ）"
     "MS 法人（メディカルサービス法人）に関する規律・通知が未収載"
     "昭和27年の回答が現行運用でどこまで維持されているかの後続通知が未確認"]}})

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
