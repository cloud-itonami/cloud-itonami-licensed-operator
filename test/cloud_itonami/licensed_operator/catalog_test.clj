(ns cloud-itonami.licensed-operator.catalog-test
  (:require [clojure.test :refer [deftest is testing]]
            [cloud-itonami.licensed-operator.catalog :as cat]))

(defn- all-routes []
  (for [[k e] cat/catalog
        route [:route/principal :route/defer]]
    [k route (get e route)]))

(deftest every-rule-is-citable
  (doseq [[jid sector] (cat/keys-covered)
          r (cat/rules jid sector)]
    (is (string? (:rule/id r)))
    (is (seq (:rule/title r)))
    (is (re-find #"^https://" (str (:rule/url r)))
        (str (:rule/id r) " の URL が https でない"))
    (is (contains? cat/verifications (:rule/verification r)))
    (is (= "2026-07-26" (:rule/retrieved-at r)))))

(deftest rule-ids-are-unique-within-a-key
  (doseq [[jid sector] (cat/keys-covered)]
    (let [ids (map :rule/id (cat/rules jid sector))]
      (is (= (count ids) (count (set ids)))
          (str jid "/" sector " に重複した rule id")))))

(deftest secondary-sources-explain-themselves
  (doseq [[jid sector] (cat/keys-covered)
          r (cat/rules jid sector)
          :when (= :secondary-source-only (:rule/verification r))]
    (is (seq (:rule/verification-note r))
        (str (:rule/id r) " が未検証なのに理由の説明が無い"))))

(deftest every-route-is-present-and-in-vocabulary
  (doseq [[[jid sector] route e] (all-routes)]
    (is (some? e) (str jid "/" sector " に " route " の記載が無い"))
    (is (contains? cat/verdicts (:verdict e))
        (str jid "/" sector "/" route " の verdict が語彙外"))))

(deftest every-basis-id-resolves
  (doseq [[[jid sector] route e] (all-routes)
          rid (:basis e)]
    (is (some? (cat/rule jid sector rid))
        (str jid "/" sector "/" route " が未定義の rule id を参照: " rid))))

(deftest permissive-verdicts-are-never-groundless
  (testing ":admissible は必ず一次/公式で検証済みのルールに支えられる"
    (doseq [[[jid sector] route e] (all-routes)
            :when (= :admissible (:verdict e))]
      (let [rs (keep #(cat/rule jid sector %) (:basis e))]
        (is (seq rs) (str jid "/" sector "/" route " が根拠ゼロで :admissible"))
        (is (some cat/verified-rule? rs)
            (str jid "/" sector "/" route
                 " の :admissible が secondary-source-only のみに依拠"))))))

(deftest restrictive-verdicts-cite-something
  (doseq [[[jid sector] route e] (all-routes)
          :when (= :prohibited (:verdict e))]
    (is (seq (:basis e))
        (str jid "/" sector "/" route " が根拠ゼロで :prohibited（禁止の捏造も捏造）"))))

(deftest unsettled-and-conditional-name-the-condition
  (doseq [[[jid sector] route e] (all-routes)
          :when (contains? #{:unsettled :conditional} (:verdict e))]
    (is (seq (:condition e))
        (str jid "/" sector "/" route " が条件の説明を欠く"))))

(deftest defer-routes-declare-their-licensee-requirements
  (testing "委譲経路は『相手について何が真であるべきか』を必ず宣言する"
    (doseq [[[jid sector] route e] (all-routes)
            :when (and (= :route/defer route)
                       (contains? #{:admissible :conditional} (:verdict e)))]
      (is (seq (:licensee-requirements e))
          (str jid "/" sector " の委譲が要件ゼロで開いている"))
      (doseq [req (:licensee-requirements e)]
        (is (contains? cat/licensee-requirements req)
            (str "未定義の要件 " req))))))

(deftest licence-facts-are-consistent
  (doseq [[jid sector] (cat/keys-covered)
          :let [l (:licence (cat/entry jid sector))]]
    (is (seq (:licence/name l)))
    (is (seq (:licence/law l)))
    (is (contains? #{true false} (:licence/obtainable-by-company? l))
        (str jid "/" sector " の :licence/obtainable-by-company? が未指定"))
    (when (:licence/fee-jpy l)
      (is (pos-int? (:licence/fee-jpy l))))))

(deftest coverage-is-reported-honestly
  (let [c (cat/coverage [["JPN" :sector/legal-services]
                         ["JPN" :sector/second-hand-dealing]
                         ["JPN" :sector/pharmacy]])]
    (is (= 3 (:requested c)))
    (is (= 2 (:covered c)))
    (is (= [["JPN" :sector/pharmacy]] (:missing-keys c)))
    (is (pos? (:rule-count c)))
    (is (contains? (:rules-by-verification c) :official-url-retrieved))
    (is (seq (:known-gaps c)))))

(deftest itad-sectors-link-to-their-kyoninka-procedures
  (testing "ITAD の2件は取得手続きが kyoninka に data として在ることを指す"
    (is (= :kobutsu-marunouchi
           (get-in (cat/entry "JPN" :sector/second-hand-dealing)
                   [:licence :licence/kyoninka-procedure])))
    (is (= :sanpai-shuun-tokyo
           (get-in (cat/entry "JPN" :sector/industrial-waste-collection)
                   [:licence :licence/kyoninka-procedure])))))
