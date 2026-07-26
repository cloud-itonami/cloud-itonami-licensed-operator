(ns cloud-itonami.licensed-operator.licensee-test
  (:require [clojure.test :refer [deftest is testing]]
            [cloud-itonami.licensed-operator.licensee :as lic]))

(def ^:private holder
  {:holder/id "kobutsu-partner-1"
   :holder/name "株式会社リユースパートナー"
   :holder/licence-name "古物商許可"
   :holder/licence-number "第301010000000号"
   :holder/licence-jurisdiction "JPN"
   :holder/licence-verified? true
   :holder/licence-verified-at "2026-07-26"
   :holder/licence-scope #{:office-equipment :machines}
   :holder/licence-expires-on nil})

(def ^:private matter
  {:matter/id "M-1"
   :matter/jurisdiction "JPN"
   :matter/act :office-equipment
   :matter/as-of "2026-07-26"
   :matter/personally-decided? true
   :matter/written-contract-ref "CT-2026-0001"})

(def ^:private all-reqs
  #{:req/licence-verified :req/same-jurisdiction :req/scope-covers
    :req/not-expired :req/personally-decided :req/written-contract})

(defn- rules-of [vs] (set (map :rule vs)))

(deftest a-complete-holder-satisfies-every-requirement
  (is (lic/ok? holder matter all-reqs)
      (pr-str (lic/verify holder matter all-reqs))))

(deftest unverified-licence-fails
  (is (contains? (rules-of (lic/verify (assoc holder :holder/licence-verified? false)
                                       matter all-reqs))
                 :req/licence-verified))
  (testing "自己申告(欄が無い)も検証ではない"
    (is (contains? (rules-of (lic/verify (dissoc holder :holder/licence-verified?)
                                         matter all-reqs))
                   :req/licence-verified))))

(deftest cross-border-holder-fails
  (testing "他法域の資格者を立てても委譲は成立しない"
    (let [vs (lic/verify (assoc holder :holder/licence-jurisdiction "USA")
                         matter all-reqs)]
      (is (contains? (rules-of vs) :req/same-jurisdiction))
      (is (re-find #"無資格営業" (:detail (first (filter #(= :req/same-jurisdiction (:rule %)) vs))))))))

(deftest missing-jurisdiction-fails-closed
  (testing "記録が無いことは『満たされている』ではない"
    (is (contains? (rules-of (lic/verify (dissoc holder :holder/licence-jurisdiction)
                                         matter all-reqs))
                   :req/same-jurisdiction))
    (is (contains? (rules-of (lic/verify (assoc holder :holder/licence-jurisdiction :unknown)
                                         matter all-reqs))
                   :req/same-jurisdiction))))

(deftest out-of-scope-act-fails
  (testing "許可はあるが区分が違うのは、許可が無いのと同じ"
    (is (contains? (rules-of (lic/verify holder (assoc matter :matter/act :vehicles) all-reqs))
                   :req/scope-covers))
    (is (contains? (rules-of (lic/verify (assoc holder :holder/licence-scope #{})
                                         matter all-reqs))
                   :req/scope-covers))))

(deftest expiry-is-compared-against-the-matter-date
  (let [h (assoc holder :holder/licence-expires-on "2026-03-31")]
    (is (contains? (rules-of (lic/verify h matter all-reqs)) :req/not-expired))
    (is (not (contains? (rules-of (lic/verify h (assoc matter :matter/as-of "2026-01-01")
                                              all-reqs))
                        :req/not-expired))
        "基準日が満了前なら有効"))
  (testing "有効期間の定めが無い許認可（古物商）は期限で落ちない"
    (is (not (contains? (rules-of (lic/verify holder matter all-reqs)) :req/not-expired)))))

(deftest name-only-deferral-fails
  (testing "名義を立てただけ・押印しただけは委譲ではない"
    (is (contains? (rules-of (lic/verify holder (assoc matter :matter/personally-decided? false)
                                         all-reqs))
                   :req/personally-decided))
    (is (contains? (rules-of (lic/verify holder (dissoc matter :matter/personally-decided?)
                                         all-reqs))
                   :req/personally-decided))))

(deftest missing-written-contract-fails
  (is (contains? (rules-of (lic/verify holder (dissoc matter :matter/written-contract-ref)
                                       all-reqs))
                 :req/written-contract))
  (is (contains? (rules-of (lic/verify holder (assoc matter :matter/written-contract-ref "  ")
                                       all-reqs))
                 :req/written-contract)))

(deftest unknown-requirement-is-a-violation-not-a-pass
  (testing "実装していない検査を『満たした』ことにはできない"
    (let [vs (lic/verify holder matter #{:req/bonded :req/licence-verified})]
      (is (contains? (rules-of vs) :unknown-requirement)))))

(deftest an-empty-holder-fails-everything-it-can
  (let [vs (lic/verify {} {} all-reqs)]
    (is (= 5 (count vs))
        (str "空レコードは :req/not-expired 以外の5件で落ちるはず: " (pr-str (rules-of vs))))
    (is (not (contains? (rules-of vs) :req/not-expired))
        "有効期限の記載が無い許認可は期限では落とさない")))

(deftest explain-is-ledger-shaped
  (let [e (lic/explain holder matter all-reqs)]
    (is (true? (:ok? e)))
    (is (= "kobutsu-partner-1" (:holder e)))
    (is (= "M-1" (:matter e)))
    (is (= (vec (sort-by str all-reqs)) (:requirements e)))))
