(ns cloud-itonami.licensed-operator.gate-test
  (:require [clojure.test :refer [deftest is testing]]
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
    (is (false? (gate/open-with? "JPN" :sector/travel-agency :route/defer att))
        ":unsettled は宣誓で解錠できない")))

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

(deftest travel-agency-has-no-easy-deferral
  (testing "旅行業法3条は旅行業者代理業まで登録対象にしているので、委譲の逃げ道が狭い"
    (is (= :unsettled (:verdict (gate/verdict-for "JPN" :sector/travel-agency :route/defer))))
    (is (false? (gate/open-with? "JPN" :sector/travel-agency :route/defer #{:route/defer}))
        "宣誓では解錠できない")
    (let [r (cat/rule "JPN" :sector/travel-agency "jpn.ryokogyo-ho-3")]
      (is (re-find #"旅行業者代理業" (:rule/quote r))))))

(deftest waste-collection-exposes-the-self-transport-exemption
  (testing "廃掃法14条1項但書 — 自ら排出した産廃を自ら運ぶ排出事業者には許可が要らない"
    (let [l (gate/licence "JPN" :sector/industrial-waste-collection)
          ex (:licence/exemptions l)]
      (is (= 2 (count ex)))
      (is (contains? (set (map :exemption/id ex)) :own-waste-self-transport))
      (is (re-find #"排出事業者は顧客であって自社ではない"
                   (:exemption/detail (first (filter #(= :own-waste-self-transport (:exemption/id %)) ex))))
          "ITAD にとってどちら側に立つかが分かれる点が書かれていること"))
    (let [r (cat/rule "JPN" :sector/industrial-waste-collection "jpn.haikibutsu-ho-14")]
      (is (= :primary-source-read (:rule/verification r)))
      (is (re-find #"自らその産業廃棄物を運搬する場合に限る" (:rule/quote r))))))

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
