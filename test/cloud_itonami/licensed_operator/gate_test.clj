(ns cloud-itonami.licensed-operator.gate-test
  (:require [clojure.string]
            [clojure.test :refer [deftest is testing]]
            [cloud-itonami.licensed-operator.catalog :as cat]
            [cloud-itonami.licensed-operator.gate :as gate]))

(def ^:private bengoshi
  {:holder/id "L-JP" :holder/licence-jurisdiction "JPN"
   :holder/licence-verified? true})

(def ^:private legal-matter
  {:matter/id "M-legal" :matter/jurisdiction "JPN"
   :matter/personally-decided? true})

(deftest uncatalogued-sector-is-denied
  (testing "未調査の業種は『規制が無い』ではなく『成立と判定しない』"
    (let [v (gate/verdict-for "JPN" :sector/pharmacy :route/defer)]
      (is (= :uncovered (:verdict v)))
      (is (re-find #"未調査" (:reason v))))
    (is (false? (gate/open? "JPN" :sector/pharmacy :route/defer)))
    (is (false? (gate/open-with? "JPN" :sector/pharmacy :route/defer #{:route/defer}))
        "未収載は宣誓でも解錠できない")))

(deftest unknown-jurisdiction-and-route-are-denied-without-throwing
  (is (= :uncovered (:verdict (gate/verdict-for "ZWE" :sector/legal-services :route/defer))))
  (is (= :uncovered (:verdict (gate/verdict-for "JPN" :sector/legal-services :route/nonsense)))))

(deftest only-admissible-is-open
  (is (true? (gate/open? "JPN" :sector/legal-services :route/defer)))
  (is (false? (gate/open? "JPN" :sector/legal-services :route/principal))
      "弁護士資格は法人が取得できないので principal は :prohibited")
  (is (false? (gate/open? "JPN" :sector/second-hand-dealing :route/principal))
      ":conditional は既定では開かない"))

(deftest attestation-unlocks-conditional-only
  (let [att #{:route/principal :route/defer}]
    (is (true? (gate/open-with? "JPN" :sector/second-hand-dealing :route/principal att))
        ":conditional は宣誓で解錠できる")
    (is (false? (gate/open-with? "JPN" :sector/legal-services :route/principal att))
        ":prohibited は宣誓で解錠できない")
    (testing ":unsettled は宣誓で解錠できない（現カタログに :unsettled は残っていないので合成する）"
      (with-redefs [cat/catalog
                    (assoc-in cat/catalog
                              [["JPN" :sector/travel-agency] :route/defer]
                              {:verdict :unsettled :basis [] :condition "未調査"})]
        (is (false? (gate/open-with? "JPN" :sector/travel-agency :route/defer att)))))))

(deftest secondary-only-permissions-are-downgraded
  (testing "カタログに現在 secondary-only ルールが無くても、降格機構自体は生きている"
    (with-redefs [cat/catalog
                  (-> cat/catalog
                      (update-in [["JPN" :sector/second-hand-dealing] :rules]
                                 conj {:rule/id "synthetic.hearsay"
                                       :rule/title "伝聞のみのルール"
                                       :rule/url "https://example.invalid/hearsay"
                                       :rule/verification :secondary-source-only
                                       :rule/verification-note "テスト用の合成ルール"
                                       :rule/retrieved-at "2026-07-26"})
                      (assoc-in [["JPN" :sector/second-hand-dealing] :route/defer]
                                {:verdict :admissible
                                 :basis ["synthetic.hearsay"]
                                 :condition "捏造された許可"
                                 :licensee-requirements #{:req/licence-verified}}))]
      (let [v (gate/verdict-for "JPN" :sector/second-hand-dealing :route/defer)]
        (is (= :unsettled (:verdict v)))
        (is (= :admissible (:downgraded-from v))))
      (is (false? (gate/open? "JPN" :sector/second-hand-dealing :route/defer))))))

(deftest the-catalog-currently-rests-entirely-on-read-sources
  (testing "e-Gov 法令 API で条文原文を入れた結果、二次情報のみのルールは残っていない"
    (let [rs (for [[j s] (cat/keys-covered) r (cat/rules j s)] r)]
      (is (seq rs))
      (is (every? cat/verified-rule? rs)
          (str "未検証出典に依拠したままのルール: "
               (pr-str (map :rule/id (remove cat/verified-rule? rs))))))))

(deftest plan-prefers-principal-when-the-licence-is-held
  (testing "自社が名義人ならその経路を採る — 委譲を裏口として先に試させない"
    (let [p (gate/plan {:jurisdiction "JPN" :sector :sector/second-hand-dealing
                        :licence-held? true
                        :attestations #{:route/principal}})]
      (is (= :principal (:route p)))
      (is (true? (:open? p)))
      (is (empty? (:blockers p)))
      (is (seq (:citations p))))))

(deftest plan-blocks-when-the-licence-is-not-held-yet
  (testing "ITAD の現在地: 古物商許可は取得可能だが未取得なので開かない"
    (let [p (gate/plan {:jurisdiction "JPN" :sector :sector/second-hand-dealing
                        :licence-held? false
                        :attestations #{:route/principal}})]
      (is (= :blocked (:route p)))
      (is (contains? (set (map :rule (:blockers p))) :licence-not-held))
      (is (= :obtain-licence (get-in p [:next :action])))
      (is (= :kobutsu-marunouchi (get-in p [:next :kyoninka-procedure]))
          "次の一手が kyoninka の手続き data を指すこと")
      (is (= "古物商許可" (get-in p [:licence :licence/name]))))))

(deftest plan-takes-the-defer-route-for-legal-services
  (testing "弁護士業は名義人になれないが、有資格者の ops 層としてなら開く"
    (let [p (gate/plan {:jurisdiction "JPN" :sector :sector/legal-services
                        :licence-held? false
                        :holder bengoshi :matter legal-matter})]
      (is (= :defer (:route p)))
      (is (true? (:open? p)))
      (is (some #(= "jpn.moj-ai-contract-guideline-2023" (:rule/id %)) (:citations p))))))

(deftest a-catalog-admissible-deferral-still-fails-on-a-bad-holder
  (testing "カタログが開いていても、相手の実レコードが要件を満たさなければ開かない"
    (let [p (gate/plan {:jurisdiction "JPN" :sector :sector/legal-services
                        :holder (assoc bengoshi :holder/licence-jurisdiction "USA")
                        :matter legal-matter})]
      (is (= :blocked (:route p)))
      (is (contains? (set (map :rule (:blockers p))) :req/same-jurisdiction))
      (is (= :fix-holder-record (get-in p [:next :action]))))
    (testing "名義を立てただけでも落ちる"
      (let [p (gate/plan {:jurisdiction "JPN" :sector :sector/legal-services
                          :holder bengoshi
                          :matter (assoc legal-matter :matter/personally-decided? false)})]
        (is (= :blocked (:route p)))
        (is (contains? (set (map :rule (:blockers p))) :req/personally-decided))))
    (testing "有資格者を渡さなければ当然落ちる"
      (let [p (gate/plan {:jurisdiction "JPN" :sector :sector/legal-services})]
        (is (= :blocked (:route p)))))))

(deftest plan-on-an-unresearched-route-says-so
  (let [p (gate/plan {:jurisdiction "JPN" :sector :sector/second-hand-dealing
                      :licence-held? false})]
    (is (= :blocked (:route p)))
    (is (= :obtain-licence (get-in p [:next :action]))
        "取得可能な許認可があるならまずそれを指す")))

(deftest plan-on-an-uncatalogued-sector-recommends-research
  (let [p (gate/plan {:jurisdiction "JPN" :sector :sector/pharmacy})]
    (is (= :blocked (:route p)))
    (is (= :research-jurisdiction (get-in p [:next :action])))
    (is (nil? (:licence p)))))

(deftest all-gates-surfaces-every-authorisation
  (testing "主たる許認可だけ見て『取れた＝営業できる』と読ませない"
    (let [gs (gate/all-gates "JPN" :sector/warehousing)]
      (is (= 2 (count gs)) "冷蔵倉庫業は倉庫業法の登録と食品衛生法の届出の2つ")
      (is (= "倉庫業の登録" (:licence/name (first gs))) "主たるゲートが先頭"))
    (is (= 1 (count (gate/all-gates "JPN" :sector/legal-services)))
        "副次ゲートが無い業種は主たるものだけ")
    (is (empty? (gate/all-gates "JPN" :sector/pharmacy))
        "未収載業種はゲートを一つも返さない")))

(deftest plan-carries-additional-gates-so-a-caller-cannot-miss-them
  (let [p (gate/plan {:jurisdiction "JPN" :sector :sector/warehousing
                      :licence-held? true :attestations #{:route/principal}})]
    (is (= :principal (:route p)))
    (is (= 1 (count (:additional-gates p))))
    (is (re-find #"食品衛生法" (:licence/name (first (:additional-gates p)))))))

(deftest medical-practice-only-opens-through-a-licence-holder
  (testing "営利法人は名義人になれないので、宣誓しても principal は開かない"
    (is (false? (gate/open-with? "JPN" :sector/medical-practice :route/principal
                                 #{:route/principal})))
    (let [p (gate/plan {:jurisdiction "JPN" :sector :sector/medical-practice
                        :licence-held? true :attestations #{:route/principal}})]
      (is (= :blocked (:route p))
          "licence-held? を立てても :prohibited は覆らない")
      (is (contains? (set (map :rule (:blockers p))) :principal-prohibited)))
    (testing "医療法人を立て、宣誓し、要件を満たせば defer が開く"
      (let [p (gate/plan {:jurisdiction "JPN" :sector :sector/medical-practice
                          :attestations #{:route/defer}
                          :holder {:holder/id "MC-1" :holder/licence-jurisdiction "JPN"
                                   :holder/licence-verified? true}
                          :matter {:matter/jurisdiction "JPN"
                                   :matter/personally-decided? true}})]
        (is (= :defer (:route p)))
        (is (some #(= "jpn.mhlw-1952-iyu-190" (:rule/id %)) (:citations p)))))))

(deftest food-manufacture-opens-as-principal-only-with-the-permit
  (let [blocked (gate/plan {:jurisdiction "JPN" :sector :sector/food-manufacture
                            :licence-held? false})]
    (is (= :blocked (:route blocked)))
    (is (= :obtain-licence (get-in blocked [:next :action])))
    (is (nil? (get-in blocked [:next :kyoninka-procedure]))
        "食品衛生法の営業許可は kyoninka に手続き data がまだ無い"))
  (let [ok (gate/plan {:jurisdiction "JPN" :sector :sector/food-manufacture
                       :licence-held? true :attestations #{:route/principal}})]
    (is (= :principal (:route ok)))))

(deftest warehousing-deferral-opens-on-the-statutory-definition
  (testing "倉庫業法2条2項が『寄託を受けた』物品の保管と定義しているので、寄託の当事者にならなければ登録義務は及ばない"
    (let [p (gate/plan {:jurisdiction "JPN" :sector :sector/warehousing
                        :licence-held? false :attestations #{:route/defer}
                        :holder {:holder/id "W-1" :holder/licence-jurisdiction "JPN"
                                 :holder/licence-verified? true
                                 :holder/licence-scope #{:cold-storage}}
                        :matter {:matter/jurisdiction "JPN" :matter/act :cold-storage
                                 :matter/written-contract-ref "CT-1"}})]
      (is (= :defer (:route p)))
      (is (some #(= "jpn.soukogyo-ho-2" (:rule/id %)) (:citations p))
          "定義条文が根拠として出ること"))
    (testing "書面契約の記録が無ければ落ちる"
      (let [p (gate/plan {:jurisdiction "JPN" :sector :sector/warehousing
                          :attestations #{:route/defer}
                          :holder {:holder/id "W-1" :holder/licence-jurisdiction "JPN"
                                   :holder/licence-verified? true
                                   :holder/licence-scope #{:cold-storage}}
                          :matter {:matter/jurisdiction "JPN" :matter/act :cold-storage}})]
        (is (= :blocked (:route p)))
        (is (contains? (set (map :rule (:blockers p))) :req/written-contract))))))

(deftest travel-agency-deferral-is-narrow-but-decidable
  (testing "2条1項の柱書「報酬を得て」＋各号の限定で境界が決まる"
    (is (= :conditional (:verdict (gate/verdict-for "JPN" :sector/travel-agency :route/defer))))
    (let [r (cat/rule "JPN" :sector/travel-agency "jpn.ryokogyo-ho-2")]
      (is (= :primary-source-read (:rule/verification r)))
      (is (re-find #"報酬を得て" (:rule/quote r)))
      (is (re-find #"媒介" (:rule/quote r)) "3号・4号の媒介が引用に含まれること"))
    (is (re-find #"内部業務"
                 (:condition (gate/verdict-for "JPN" :sector/travel-agency :route/defer)))
        "旅行者にも提供者にも向かない位置にとどまる、という条件が明示されること"))
  (testing "3条は代理業まで登録対象にしている"
    (is (re-find #"旅行業者代理業"
                 (:rule/quote (cat/rule "JPN" :sector/travel-agency "jpn.ryokogyo-ho-3"))))))

(deftest definition-articles-decide-the-deferral-boundaries
  (testing "どの業種でも『どこからが規制業か』は定義条文が決めている"
    (doseq [[sector rule-id needle] [[:sector/real-estate-brokerage "jpn.takken-ho-2" #"媒介"]
                                     [:sector/employment-placement "jpn.shokugyo-anteiho-4" #"あつせん"]
                                     [:sector/warehousing "jpn.soukogyo-ho-2" #"寄託を受けた"]
                                     [:sector/second-hand-dealing "jpn.kobutsu-eigyo-ho-2" #"売買"]
                                     [:sector/travel-agency "jpn.ryokogyo-ho-2" #"報酬を得て"]]]
      (let [r (cat/rule "JPN" sector rule-id)]
        (is (some? r) (str rule-id " が無い"))
        (is (= :primary-source-read (:rule/verification r)))
        (is (re-find needle (:rule/quote r))
            (str rule-id " の引用に境界語 " needle " が無い"))
        (is (contains? (set (get-in (cat/entry "JPN" sector) [:route/defer :basis])) rule-id)
            (str sector " の defer が定義条文を根拠に挙げていない"))))))

(deftest employment-placement-turns-on-taking-a-fee
  (testing "職安法4条 — 有料／無料の別はいかなる名義でも手数料を受けるかで決まる"
    (let [r (cat/rule "JPN" :sector/employment-placement "jpn.shokugyo-anteiho-4")]
      (is (re-find #"いかなる名義でも" (:rule/quote r)))
      (is (re-find #"手数料又は報酬を受けないで" (:rule/quote r))))
    (is (re-find #"無料の職業紹介"
                 (:condition (gate/verdict-for "JPN" :sector/employment-placement :route/defer))))))

(deftest medical-deferral-is-bounded-by-the-dividend-ban
  (testing "医療法54条は委譲先から利益を取り出す設計そのものを縛る"
    (let [r (cat/rule "JPN" :sector/medical-practice "jpn.iryo-ho-54")]
      (is (= "医療法人は、剰余金の配当をしてはならない。" (:rule/quote r)))
      (is (= :primary-source-read (:rule/verification r))))
    (is (re-find #"剰余金"
                 (:condition (gate/verdict-for "JPN" :sector/medical-practice :route/defer))))
    (is (contains? (set (get-in (cat/entry "JPN" :sector/medical-practice)
                                [:route/principal :basis]))
                   "jpn.iryo-ho-54"))))

(deftest waste-collection-exposes-the-self-transport-exemption
  (testing "廃掃法14条1項但書 — 自ら排出した産廃を自ら運ぶ排出事業者には許可が要らない"
    (let [l (gate/licence "JPN" :sector/industrial-waste-collection)
          ex (:licence/exemptions l)]
      (is (contains? (set (map :exemption/id ex)) :own-waste-self-transport))
      (is (re-find #"排出事業者は顧客であって自社ではない"
                   (:exemption/detail (first (filter #(= :own-waste-self-transport (:exemption/id %)) ex))))
          "ITAD にとってどちら側に立つかが分かれる点が書かれていること"))
    (let [r (cat/rule "JPN" :sector/industrial-waste-collection "jpn.haikibutsu-ho-14")]
      (is (= :primary-source-read (:rule/verification r)))
      (is (re-find #"自らその産業廃棄物を運搬する場合に限る" (:rule/quote r))))))

(deftest kogata-kaden-lifts-both-waste-licences-at-once
  (testing "小型家電リサイクル法13条は廃掃法14条1項と6項を同時に外す"
    (doseq [sector [:sector/industrial-waste-collection :sector/waste-disposal]]
      (let [ids (set (map :exemption/id
                          (:licence/exemptions (gate/licence "JPN" sector))))]
        (is (contains? ids :kogata-kaden-nintei)
            (str sector " に認定事業者の特例が無い"))
        (is (contains? ids :kogata-kaden-jutaku)
            (str sector " に13条3項（委託先）の特例が無い")))
      (testing "特例は但書ではなく別法なので、条文が引けること"
        (let [r (cat/rule "JPN" sector "jpn.kogata-kaden-13")]
          (is (= :primary-source-read (:rule/verification r)))
          (is (re-find #"許可を受けないで" (:rule/quote r)))
          (is (re-find #"認定事業者の委託を受けて" (:rule/quote r))
              "3項（自社が実行者のまま許可義務だけ外れる形）が引用に含まれること"))))))

(deftest kogata-kaden-scope-is-not-overclaimed
  (testing "対象機器は施行令1条の列挙で、柱書きの限定つき"
    (let [r (cat/rule "JPN" :sector/industrial-waste-collection "jpn.kogata-kaden-rei-1")]
      (is (re-find #"パーソナルコンピュータ" (:rule/quote r)))
      (is (re-find #"一般消費者が通常生活の用に供する" (:rule/quote r))
          "柱書きの限定を落として『PC は対象』とだけ読ませない")))
  (testing "解釈が未確認であることが gap として名指しされている"
    (doseq [sector [:sector/industrial-waste-collection :sector/waste-disposal]]
      (let [gaps (:known-gaps (cat/entry "JPN" sector))]
        (is (some #(re-find #"一般消費者が通常生活の用に供する" %) gaps))
        (is (some #(re-find #"主務省令" %) gaps)
            "認定基準を読んでいないことを申告していること")))))

(deftest kogata-kaden-does-not-open-any-route-on-its-own
  (testing "特例を収録しても verdict は動かない —— 認定を受けていない者には効かない"
    (doseq [sector [:sector/industrial-waste-collection :sector/waste-disposal]]
      (let [p (gate/plan {:jurisdiction "JPN" :sector sector :licence-held? false})]
        (is (= :blocked (:route p))
            (str sector " が認定なしで開いてしまっている"))))))

(deftest summary-is-consistent-with-verdicts
  (let [s (gate/summary)]
    (is (= (set (cat/keys-covered)) (set (keys s))))
    (doseq [[[jid sector] row] s]
      (is (= (:verdict (gate/verdict-for jid sector :route/principal)) (:principal row)))
      (is (= (:verdict (gate/verdict-for jid sector :route/defer)) (:defer row))))
    (testing "ITAD の2業種は法人で取得できる許認可を持つ"
      (is (true? (:obtainable? (get s ["JPN" :sector/second-hand-dealing]))))
      (is (true? (:obtainable? (get s ["JPN" :sector/industrial-waste-collection]))))
      (is (false? (:obtainable? (get s ["JPN" :sector/legal-services])))))))

(deftest telecom-has-registration-and-notification-gates
  (testing "電気通信事業法は規模で登録と届出に分かれる — 登録不要でも無規制ではない"
    (let [gs (gate/all-gates "JPN" :sector/telecom)]
      (is (= 2 (count gs)))
      (is (re-find #"登録" (:licence/name (first gs))))
      (is (re-find #"届出" (:licence/name (second gs))))
      (is (= :below-registration-threshold (:licence/applies-when (second gs)))))
    (let [r (cat/rule "JPN" :sector/telecom "jpn.denki-tsushin-ho-16")]
      (is (= :primary-source-read (:rule/verification r)))
      (is (re-find #"第九条の登録を受けるべき者を除く" (:rule/quote r))))))

(deftest alcohol-manufacture-has-a-minimum-volume-floor
  (testing "酒税法7条2項 — 見込数量が最低製造数量に達しなければそもそも免許を受けられない"
    (let [r (cat/rule "JPN" :sector/alcohol-manufacture "jpn.shuzei-ho-7")]
      (is (= :primary-source-read (:rule/verification r)))
      (is (re-find #"見込数量が当該酒類につき次に定める数量に達しない場合には、\s*受けることができない"
                   (clojure.string/replace (:rule/quote r) #"\s+" ""))
          "最低製造数量の要件が引用されていること"))
    (is (re-find #"最低製造数量"
                 (:condition (gate/verdict-for "JPN" :sector/alcohol-manufacture :route/principal))))))

(deftest waste-collection-and-disposal-are-separate-permits
  (testing "収集運搬（14条1項）と処分（14条6項）は別の許可"
    (is (not= (get-in (cat/entry "JPN" :sector/industrial-waste-collection)
                      [:licence :licence/law])
              (get-in (cat/entry "JPN" :sector/waste-disposal)
                      [:licence :licence/law])))
    (testing "どちらにも自己処理の適用除外がある"
      (doseq [[sector ex-id] [[:sector/industrial-waste-collection :own-waste-self-transport]
                              [:sector/waste-disposal :own-waste-self-disposal]]]
        (is (contains? (set (map :exemption/id
                                 (get-in (cat/entry "JPN" sector) [:licence :licence/exemptions])))
                       ex-id))))))

(deftest tokyo-layer-adds-local-facts-without-losing-national-ones
  (testing "地方の層は国の層に上乗せする — 手数料は東京都の値、条文は国のもの"
    (let [e (cat/entry "JPN-13" :sector/industrial-waste-collection)]
      (is (= "JPN" (:inherited-from e)))
      (is (= 81000 (get-in e [:licence :licence/fee-jpy])) "東京都の手数料")
      (is (re-find #"東京都" (get-in e [:licence :licence/authority])))
      (is (= "廃棄物の処理及び清掃に関する法律 第14条第1項" (get-in e [:licence :licence/law]))
          "国の法条は失われないこと")
      (is (some #(= "jpn.haikibutsu-ho-14" (:rule/id %)) (:rules e))
          "国のルールを継承していること"))
    (testing "国の層には東京都固有の値が混ざっていないこと"
      (is (nil? (get-in (cat/raw-entry "JPN" :sector/industrial-waste-collection)
                        [:licence :licence/fee-jpy]))
          "81,000円は全国一律ではないので国の層に置かない"))
    (testing "gate も地方の法域を解決できる"
      (let [p (gate/plan {:jurisdiction "JPN-13" :sector :sector/industrial-waste-collection
                          :licence-held? true :attestations #{:route/principal}})]
        (is (= :principal (:route p)))
        (is (seq (:citations p)))))))

(deftest england-regulates-the-same-activities-by-different-means
  (testing "同じ経済活動でも、日本の事前免許に対して英国は事前免許なしのことがある"
    (let [rows (gate/compare-sector :sector/real-estate-brokerage)
          by-j (into {} (map (juxt :jurisdiction identity) rows))]
      (is (= 4 (count rows)) "JPN・GBR・DEU・CAN-ON の4法域に収録があること")
      (is (= :prior-authorisation (:regime (get by-j "JPN"))))
      (is (= :negative-licensing (:regime (get by-j "GBR")))
          "英国 estate agency は事前免許でなく禁止命令による事後規律")
      (is (= :prior-authorisation (:regime (get by-j "DEU"))))
      (is (re-find #"免許" (:licence (get by-j "JPN"))))
      (is (re-find #"事前免許なし" (:licence (get by-j "GBR"))))
      (is (re-find #"34c" (:licence (get by-j "DEU"))))))
  (testing "職業紹介も同じ対照"
    (let [by-j (into {} (map (juxt :jurisdiction identity)
                             (gate/compare-sector :sector/employment-placement)))]
      (is (= :prior-authorisation (:regime (get by-j "JPN"))))
      (is (= :negative-licensing (:regime (get by-j "GBR")))))))

(deftest uk-waste-exemption-is-narrower-than-japans
  (testing "自己運搬の除外: 日本は場所を問わず、英国は同一構内のみ"
    (let [jp (map :exemption/id (get-in (cat/entry "JPN" :sector/industrial-waste-collection)
                                        [:licence :licence/exemptions]))
          gb (get-in (cat/entry "GBR" :sector/industrial-waste-collection)
                     [:licence :licence/exemptions])]
      (is (contains? (set jp) :own-waste-self-transport))
      (is (contains? (set (map :exemption/id gb)) :same-premises))
      (is (re-find #"日本の但書より狭い"
                   (:exemption/detail (first (filter #(= :same-premises (:exemption/id %)) gb))))))
    (let [r (cat/rule "GBR" :sector/industrial-waste-collection "gbr.copaa-1989-s1")]
      (is (= :primary-source-read (:rule/verification r)))
      (is (re-find #"within the same premises" (:rule/quote r))))))

(deftest uk-licenses-scrap-metal-not-second-hand-goods-generally
  (testing "英国は中古品全般でなく金属くずだけを免許制で捕まえる"
    (let [r (cat/rule "GBR" :sector/scrap-metal-dealing "gbr.smda-2013-s1")]
      (is (= :primary-source-read (:rule/verification r)))
      (is (re-find #"No person may carry on business as a scrap metal dealer" (:rule/quote r))))
    (is (nil? (cat/raw-entry "GBR" :sector/second-hand-dealing))
        "一般の中古品取引を GBR に収録していない —— 不在の推認で :admissible を出さないため")
    (is (re-find #"不在の推認"
                 (first (filter #(re-find #"不在の推認" %)
                                (cat/known-gaps "GBR" :sector/scrap-metal-dealing))))
        "その不在が known-gap として名指しされていること")))

(deftest compare-sector-is-consistent-with-verdicts
  (doseq [sector (keys cat/sectors)
          row (gate/compare-sector sector)]
    (is (= (:principal row)
           (:verdict (gate/verdict-for (:jurisdiction row) sector :route/principal))))
    (is (contains? cat/regimes (:regime row)))))

(deftest germany-is-a-third-regime-shape
  (testing "RDG は『6類型以外自由』でも『弁護士以外不可』でもない第三の型"
    (is (= :prohibition-with-registration-exceptions
           (get-in (cat/entry "DEU" :sector/legal-services) [:licence :licence/regime])))
    (let [r3 (cat/rule "DEU" :sector/legal-services "deu.rdg-3")
          r10 (cat/rule "DEU" :sector/legal-services "deu.rdg-10")]
      (is (re-find #"ist unzulässig, soweit sie nicht erlaubt wird" (:rule/quote r3))
          "原則禁止 + 限定列挙の例外")
      (is (re-find #"juristische Personen" (:rule/quote r10))
          "法人が登録できることが条文にある —— 日本との決定的な違い")))
  (testing "境界語は日本の法務省ガイドラインとほぼ同じ線を引いている"
    (let [r (cat/rule "DEU" :sector/legal-services "deu.rdg-2")]
      (is (re-find #"rechtliche Prüfung des Einzelfalls" (:rule/quote r)))
      (is (re-find #"個別事案の法的検討" (:rule/summary r))))))

(deftest german-waste-splits-on-hazardousness-not-on-activity
  (testing "二段構造の軸が日英と違う"
    (let [r53 (cat/rule "DEU" :sector/industrial-waste-collection "deu.krwg-53")
          r54 (cat/rule "DEU" :sector/industrial-waste-collection "deu.krwg-54")]
      (is (re-find #"anzuzeigen" (:rule/quote r53)) "非危険は届出")
      (is (re-find #"gefährlichen Abfällen.*bedürfen der \*\*Erlaubnis\*\*"
                   (clojure.string/replace (:rule/quote r54) #"\s+" " "))
          "危険は許可"))
    (testing "Händler と Makler まで名宛人なので取次ぎ層も捕捉されうる"
      (is (re-find #"Händler und Makler"
                   (:rule/quote (cat/rule "DEU" :sector/industrial-waste-collection "deu.krwg-53"))))
      (is (re-find #"委譲の逃げ道が特に狭い"
                   (:condition (gate/verdict-for "DEU" :sector/industrial-waste-collection
                                                 :route/defer)))))))

(deftest eu-constraints-bind-the-state-and-never-move-a-verdict
  (testing "役務指令は加盟国を縛る —— 事業者の verdict を動かしてはならない"
    (let [cs (cat/supranational-constraints "DEU" :sector/real-estate-brokerage)]
      (is (= 2 (count cs)))
      (is (every? #(= :member-state (:constraint/on %)) cs))
      (is (every? #(= :member-state (get-in % [:constraint/rule :rule/binds])) cs))
      (is (re-find #"事業者の義務を免除しない"
                   (:constraint/detail (first cs)))))
    (testing "plan は verdict の外に、ラベル付きで返す"
      (let [p (gate/plan {:jurisdiction "DEU" :sector :sector/real-estate-brokerage
                          :licence-held? true :attestations #{:route/principal}})]
        (is (= :principal (:route p)))
        (is (= 2 (count (:supranational-constraints p))))))
    (testing "EU 制約を全部消しても verdict は1つも変わらない"
      (let [before (into {} (for [[j sec] (cat/keys-covered)
                                  r [:route/principal :route/defer]]
                              [[j sec r] (:verdict (gate/verdict-for j sec r))]))]
        (with-redefs [cat/catalog
                      (into {} (for [[k e] cat/catalog]
                                 [k (dissoc e :supranational-constraints)]))]
          (let [after (into {} (for [[j sec] (cat/keys-covered)
                                     r [:route/principal :route/defer]]
                                 [[j sec r] (:verdict (gate/verdict-for j sec r))]))]
            (is (= before after)
                "supranational-constraints が verdict に漏れている")))))))

(deftest legal-services-shows-four-distinct-regime-shapes
  (testing "同じ『法律サービス』でも入口の作りが法域ごとに違う"
    (let [by-j (into {} (map (juxt :jurisdiction identity)
                             (gate/compare-sector :sector/legal-services)))]
      (is (= :prior-authorisation (:regime (get by-j "JPN")))
          "日本: 弁護士法72条 —— 資格者以外は原則不可")
      (is (= :reserved-activities-only (:regime (get by-j "GBR")))
          "英国: LSA 2007 —— 6類型だけが閉じており、それ以外は入口規制なし")
      (is (= :prohibition-with-registration-exceptions (:regime (get by-j "DEU")))
          "独: RDG §3 で原則禁止、§10 の能力別登録で門を開ける")
      (is (= :prohibited (:principal (get by-j "JPN")))
          "法人が名義人になれないのは日本だけ"))
    (testing "既定値に落ちている entry が無いこと（regime の書き忘れ検出）"
      (doseq [[jid sector] (cat/keys-covered)
              :when (nil? (cat/parent-jurisdiction jid))]
        (is (contains? cat/regimes
                       (get-in (cat/entry jid sector) [:licence :licence/regime]
                               :prior-authorisation)))))))

(deftest ontario-licenses-two-tiers-inside-one-profession
  (testing "26.1(1) が practise law と provide legal services を並べて禁じる"
    (let [r (cat/rule "CAN-ON" :sector/legal-services "can-on.lsa-26-1")]
      (is (= :primary-source-read (:rule/verification r)))
      (is (re-find #"practise law in Ontario or provide legal services in Ontario"
                   (:rule/quote r))))
    (is (re-find #"paralegal"
                 (get-in (cat/entry "CAN-ON" :sector/legal-services)
                         [:licence :licence/note]))
        "2段構造であることが記録されていること"))
  (testing "罰金額まで一次で取れている"
    (is (re-find #"\$25,000"
                 (:rule/quote (cat/rule "CAN-ON" :sector/legal-services "can-on.lsa-26-2"))))))

(deftest ontario-registration-requires-the-notice-not-the-application
  (let [r (cat/rule "CAN-ON" :sector/real-estate-brokerage "can-on.trebba-6")]
    (is (re-find #"until notified in writing by the registrar" (:rule/quote r))
        "申請では足りず通知の到達が条件"))
  (is (re-find #"申請済みでは足りない"
               (:condition (gate/verdict-for "CAN-ON" :sector/real-estate-brokerage
                                             :route/principal)))))

(deftest france-requires-payment-and-repetition-in-the-offence-itself
  (testing "art.54 は『à titre habituel et rémunéré』を構成要件に組み込む"
    (let [r (cat/rule "FRA" :sector/legal-services "fra.loi-71-1130-art54")]
      (is (= :primary-source-read (:rule/verification r)))
      (is (re-find #"à titre habituel et rémunéré" (:rule/quote r))))
    (is (false? (get-in (cat/entry "FRA" :sector/legal-services)
                        [:licence :licence/obtainable-by-company?]))
        "法人はこの資格を持てない")))

(deftest eu-constraints-now-span-two-member-states
  (testing "EU 層が単一法域の飾りでないこと"
    (let [with-eu (filter (fn [[j s]] (seq (cat/supranational-constraints j s)))
                          (cat/keys-covered))]
      (is (<= 2 (count (distinct (map first with-eu))))
          (str "EU 制約を持つ法域が2つ以上あること: " (pr-str (distinct (map first with-eu)))))
      (is (contains? (set (map first with-eu)) "DEU"))
      (is (contains? (set (map first with-eu)) "FRA")))))
