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

(deftest additional-gates-are-shaped-like-licences
  (testing "副次ゲートも許認可と同じ形で持ち、法条と当局を名指しする"
    (doseq [[jid sector] (cat/keys-covered)
            g (cat/additional-gates jid sector)]
      (is (seq (:licence/name g)))
      (is (seq (:licence/law g)))
      (is (seq (:licence/authority g)))
      (is (contains? #{true false} (:licence/obtainable-by-company? g))))))

(deftest refrigerated-warehousing-has-two-gates
  (testing "冷蔵倉庫業は倉庫業法の登録だけでは営業できない — 食品衛生法の届出も要る"
    (let [gs (cat/additional-gates "JPN" :sector/warehousing)]
      (is (= 1 (count gs)))
      (is (re-find #"食品衛生法" (:licence/name (first gs))))
      (is (= :refrigerated (:licence/applies-when (first gs)))))
    (is (= "倉庫業法 第3条"
           (get-in (cat/entry "JPN" :sector/warehousing) [:licence :licence/law])))))

(deftest medical-practice-cannot-be-held-by-a-company
  (testing "営利法人は医業の名義人になれない — 委譲以外の経路が無い"
    (let [e (cat/entry "JPN" :sector/medical-practice)]
      (is (false? (get-in e [:licence :licence/obtainable-by-company?])))
      (is (= :prohibited (get-in e [:route/principal :verdict])))
      (is (= :conditional (get-in e [:route/defer :verdict])))
      (let [r (cat/rule "JPN" :sector/medical-practice "jpn.mhlw-1952-iyu-190")]
        (is (= :primary-source-read (:rule/verification r)))
        (is (re-find #"営利を目的として営むことは許されない" (:rule/quote r)))))))

(deftest food-manufacture-lists-the-32-permit-categories
  (let [r (cat/rule "JPN" :sector/food-manufacture "jpn.tokyo-shokuhin-kyoka-list")]
    (is (= :primary-source-read (:rule/verification r)))
    (doseq [k ["菓子製造業" "麺類製造業" "そうざい製造業" "冷凍食品製造業"
               "水産製品製造業" "食品の小分け業"]]
      (is (re-find (re-pattern k) (:rule/summary r))
          (str k " が32業種の記載に見当たらない")))))

(deftest non-itad-sectors-now-outnumber-itad-ones
  (testing "commons が ITAD 専用にならないこと — 実装済み actor が載る業種を収録する"
    (let [itad #{:sector/second-hand-dealing :sector/industrial-waste-collection}
          all (map second (cat/keys-covered))]
      (is (> (count (remove itad all)) (count (filter itad all)))
          (str "ITAD 以外の業種が過半でない: " (pr-str all))))))

(deftest itad-sectors-link-to-their-kyoninka-procedures
  (testing "ITAD の2件は取得手続きが kyoninka に data として在ることを指す"
    (is (= :kobutsu-marunouchi
           (get-in (cat/entry "JPN" :sector/second-hand-dealing)
                   [:licence :licence/kyoninka-procedure])))
    (is (= :sanpai-shuun-tokyo
           (get-in (cat/entry "JPN" :sector/industrial-waste-collection)
                   [:licence :licence/kyoninka-procedure])))))
