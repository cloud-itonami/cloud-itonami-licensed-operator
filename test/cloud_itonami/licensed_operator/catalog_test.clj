(ns cloud-itonami.licensed-operator.catalog-test
  (:require [clojure.test :refer [deftest is testing]]
            [cloud-itonami.licensed-operator.catalog :as cat]))

(defn- national-keys []
  (remove #(cat/parent-jurisdiction (first %)) (keys cat/catalog)))

(defn- sub-national-keys []
  (filter #(cat/parent-jurisdiction (first %)) (keys cat/catalog)))

(defn- all-routes
  "国の層の全 route。地方の層は route を省略して継承してよいので含めない。"
  []
  (for [k (national-keys)
        route [:route/principal :route/defer]]
    [k route (get (cat/raw-entry (first k) (second k)) route)]))

(deftest every-rule-is-citable
  (doseq [[jid sector] (cat/keys-covered)
          r (cat/rules jid sector)]
    (is (string? (:rule/id r)))
    (is (seq (:rule/title r)))
    (is (re-find #"^https://" (str (:rule/url r)))
        (str (:rule/id r) " の URL が https でない"))
    (is (contains? cat/verifications (:rule/verification r)))
    ;; 収録日は固定値ではない（カタログは追記されていく）。要求するのは
    ;; 「いつ読んだかが記録されていること」であって、全部が同じ日であること
    ;; ではない —— 固定値で縛ると、追記のたびに既存の日付を書き換えたく
    ;; なってしまい、出典の鮮度が嘘になる。
    (is (re-find #"^\d{4}-\d{2}-\d{2}$" (str (:rule/retrieved-at r)))
        (str (:rule/id r) " に取得日が無い / ISO 日付でない"))))

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

(deftest overlay-layers-inherit-rather-than-duplicate
  (testing ":overlay は国の層を継承する — 47都道府県ぶん複製しない"
    (doseq [[jid sector] (sub-national-keys)
            :when (= :overlay (cat/sub-national-kind jid sector))]
      (let [nat (cat/parent-jurisdiction jid)
            resolved (cat/entry jid sector)]
        (is (some? (cat/raw-entry nat sector))
            (str jid " は :overlay なのに親 " nat " に " sector " が無い（孤児の地方層）"))
        (is (= nat (:inherited-from resolved)))
        (is (some? (get resolved :route/principal))
            "継承後は route が揃うこと")
        (is (>= (count (:rules resolved))
                (count (cat/rules nat sector)))
            "国の層のルールを失わないこと")))))

(deftest exclusive-layers-stand-alone
  (testing ":exclusive は国の層が存在しない — 立法権が地方に専属する事項"
    (let [ex (filter #(= :exclusive (apply cat/sub-national-kind %)) (sub-national-keys))]
      (is (seq ex) "少なくとも1件は :exclusive があること（CAN-ON）")
      (doseq [[jid sector] ex]
        (is (nil? (cat/raw-entry (cat/parent-jurisdiction jid) sector))
            (str jid "/" sector " は :exclusive なのに親の層がある —— :overlay の間違いでは"))
        (let [e (cat/entry jid sector)]
          (is (some? (get e :route/principal)) "単独で route が揃っていること")
          (is (nil? (:inherited-from e)) "継承元を持たないこと")
          (is (seq (:rules e)) "自前のルールを持つこと"))))))

(deftest every-sub-national-layer-declares-its-kind
  (doseq [[jid sector] (sub-national-keys)]
    (is (contains? cat/sub-national-kinds (cat/sub-national-kind jid sector))
        (str jid "/" sector " の sub-national-kind が語彙外"))))

(deftest sub-national-layers-may-only-tighten
  (testing "条例・規則は法律が禁じたことを適法にできない"
    (doseq [[jid sector] (sub-national-keys)
            route [:route/principal :route/defer]]
      (let [nat (cat/parent-jurisdiction jid)
            nv (get-in (cat/raw-entry nat sector) [route :verdict])
            lv (get-in (cat/raw-entry jid sector) [route :verdict])]
        (when (and nv lv)
          (is (>= (get cat/restrictiveness lv) (get cat/restrictiveness nv))
              (str jid "/" sector "/" route " が国の層より緩い: "
                   (pr-str lv) " < " (pr-str nv))))))))

(deftest a-loosening-sub-national-layer-is-refused
  (testing "緩めようとした地方の層は無視され、その事実が記録される"
    (with-redefs [cat/catalog
                  (assoc cat/catalog ["JPN-13" :sector/medical-practice]
                         {:jurisdiction "JPN-13" :sector :sector/medical-practice
                          :route/principal {:verdict :admissible
                                            :basis []
                                            :condition "都の判断で解禁したことにする"}})]
      (let [r (get-in (cat/entry "JPN-13" :sector/medical-practice)
                      [:route/principal])]
        (is (= :prohibited (:verdict r)) "国の :prohibited が維持されること")
        (is (re-find #"条例・規則は法律が禁じたことを適法にできない"
                     (:sub-national-loosening-ignored r)))))))

(deftest every-basis-id-resolves
  (testing "地方の層も含め、解決後のルール集合の中に根拠が存在すること"
    (doseq [[jid sector] (cat/keys-covered)
            route [:route/principal :route/defer]
            rid (get-in (cat/entry jid sector) [route :basis])]
      (is (some? (cat/rule jid sector rid))
          (str jid "/" sector "/" route " が未定義の rule id を参照: " rid)))))

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
  (testing "解決後の licence は（地方の層でも）名称・法条・取得可否が揃う"
    (doseq [[jid sector] (cat/keys-covered)
            :let [l (:licence (cat/entry jid sector))]]
      (is (seq (:licence/name l)))
      (is (seq (:licence/law l)))
      (is (contains? #{true false} (:licence/obtainable-by-company? l))
          (str jid "/" sector " の :licence/obtainable-by-company? が未指定"))
      (when (:licence/fee-jpy l)
        (is (pos-int? (:licence/fee-jpy l)))))))

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

(deftest requirement-bases-resolve-and-are-declared
  (testing "要件に実定法の根拠が付いているなら、その rule が実在し要件も宣言されていること"
    (doseq [[jid sector] (cat/keys-covered)
            :let [d (get (cat/entry jid sector) :route/defer)]
            [req rid] (:licensee-requirement-basis d)]
      (is (contains? cat/licensee-requirements req)
          (str "未定義の要件 " req " に根拠が付いている"))
      (is (contains? (:licensee-requirements d) req)
          (str req " が根拠付きなのに :licensee-requirements に無い"))
      (is (some? (cat/rule jid sector rid))
          (str req " の根拠 " rid " が解決できない"))
      (is (cat/verified-rule? (cat/rule jid sector rid))
          (str req " の根拠 " rid " が未検証出典")))))

(deftest waste-delegation-standards-back-two-requirements
  (testing "廃掃法施行令6条の2 — scope-covers と written-contract に政令の明文根拠"
    (doseq [sector [:sector/industrial-waste-collection :sector/waste-disposal]]
      (let [d (get (cat/entry "JPN" sector) :route/defer)]
        (is (= {:req/scope-covers "jpn.haiki-rei-6-2"
                :req/written-contract "jpn.haiki-rei-6-2"}
               (:licensee-requirement-basis d))
            (str sector " の要件根拠が付いていない"))))
    (let [r (cat/rule "JPN" :sector/waste-disposal "jpn.haiki-rei-6-2")]
      (is (= :primary-source-read (:rule/verification r)))
      (is (re-find #"その事業の範囲に含まれる" (:rule/quote r)))
      (is (re-find #"委託契約は、書面により行い" (:rule/quote r))))))

(deftest telecom-threshold-is-a-concrete-geography-test
  (testing "施行規則3条 — 市町村／都道府県を超えるかで登録と届出が分かれる"
    (let [r (cat/rule "JPN" :sector/telecom "jpn.denki-kisoku-3")]
      (is (= :primary-source-read (:rule/verification r)))
      (is (re-find #"一の市町村" (:rule/quote r)))
      (is (re-find #"一の都道府県の区域" (:rule/quote r))))
    (is (re-find #"回線設備を自ら設置しないサービス"
                 (get-in (cat/entry "JPN" :sector/telecom) [:route/principal :condition]))
        "設備を持たない事業者がどちらに落ちるかが書かれていること")))

(deftest food-permit-categories-come-from-the-cabinet-order
  (testing "要許可32業種は施行令35条が指定している — 業種名だけで当てはめると外す"
    (let [r (cat/rule "JPN" :sector/food-manufacture "jpn.shokuhin-eisei-rei-35")]
      (is (= :primary-source-read (:rule/verification r)))
      (is (re-find #"法第五十四条の規定により都道府県が施設についての基準を定めるべき営業"
                   (:rule/quote r)))
      (is (re-find #"パン及びあん類を含む" (:rule/quote r))
          "各号が定義を伴うことの実例が引用に入っていること"))))
