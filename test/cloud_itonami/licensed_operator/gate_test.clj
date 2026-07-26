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
    (is (false? (gate/open-with? "JPN" :sector/second-hand-dealing :route/defer att))
        ":unsettled は宣誓で解錠できない")))

(deftest secondary-only-permissions-are-downgraded
  (with-redefs [cat/catalog
                (assoc-in cat/catalog
                          [["JPN" :sector/second-hand-dealing] :route/defer]
                          {:verdict :admissible
                           :basis ["jpn.kobutsu-eigyo-ho-3"] ; secondary-source-only
                           :condition "捏造された許可"
                           :licensee-requirements #{:req/licence-verified}})]
    (let [v (gate/verdict-for "JPN" :sector/second-hand-dealing :route/defer)]
      (is (= :unsettled (:verdict v)))
      (is (= :admissible (:downgraded-from v))))
    (is (false? (gate/open? "JPN" :sector/second-hand-dealing :route/defer)))))

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
