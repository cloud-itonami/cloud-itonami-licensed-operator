(ns cloud-itonami.licensed-operator.gate
  "Pure decision layer over `catalog` + `licensee`. This is what a
  calling actor's governor invokes; like
  `cloud-itonami-regulatory-tracker` this is a plain library — no
  advisor, no StateGraph, no ledger of its own. It files nothing, holds
  no licence and contacts no authority.

  Three properties, each proved by a test:

    1. DENY BY DEFAULT. An uncatalogued (jurisdiction, sector), an
       unknown route, and every verdict other than `:admissible`
       resolve to not-open. There is no fallback sector.

    2. NO PERMISSION FROM AN UNVERIFIED SOURCE. An `:admissible`
       verdict is honoured only if at least one basis rule was read at
       the primary or official level; otherwise it is downgraded to
       `:unsettled` with `:downgraded-from` recorded. Restrictive
       verdicts need no such backing.

    3. A DEFERRAL IS ONLY AS GOOD AS THE HOLDER. `:route/defer` being
       `:admissible` in the catalog opens nothing on its own —
       `plan` still runs every `:licensee-requirements` check against
       the actual holder record, and a missing field fails closed."
  (:require [cloud-itonami.licensed-operator.catalog :as cat]
            [cloud-itonami.licensed-operator.licensee :as lic]))

(def ^:private route-keys {:route/principal :route/principal
                           :route/defer :route/defer})

(defn- basis-rules [jid sector e]
  (keep #(cat/rule jid sector %) (:basis e)))

(defn verdict-for
  "Resolve the verdict for `route` of `[jid sector]`. Always returns a
  map with at least `:verdict`; never nil, never throws."
  [jid sector route]
  (let [base {:jurisdiction jid :sector sector :route route}]
    (cond
      (nil? (route-keys route))
      (assoc base :verdict :uncovered
             :reason (str "unknown route " (pr-str route)
                          " — expected :route/principal or :route/defer"))

      (nil? (cat/entry jid sector))
      (assoc base :verdict :uncovered
             :reason (str "(" (pr-str jid) ", " (pr-str sector)
                          ") はカタログ未収載。spec-basis が無いため成立と判定しない"
                          "（『規制が無い』ではなく未調査）。"))

      :else
      (let [e (get (cat/entry jid sector) route)]
        (if (nil? e)
          (assoc base :verdict :uncovered
                 :reason (str "route " (pr-str route) " の記載が無い。"))
          (let [rs (basis-rules jid sector e)
                verified? (boolean (some cat/verified-rule? rs))
                declared (:verdict e)
                effective (if (and (= :admissible declared) (not verified?))
                            :unsettled
                            declared)]
            (cond-> (merge base
                           (select-keys e [:basis :condition :licensee-requirements
                                           :implemented-by])
                           {:verdict effective :basis-rules (vec rs)})
              (not= effective declared)
              (assoc :downgraded-from declared
                     :reason (str "基礎ルールがいずれも secondary-source-only のため "
                                  ":admissible を :unsettled に降格した。")))))))))

(defn open?
  "True only for an `:admissible` verdict that survived the
  verified-source check. `:conditional` is NOT open — it means
  lawfulness turns on a fact the catalog cannot check (a licence the
  operator holds, a registration). Unlock those with `attestations`."
  [jid sector route]
  (= :admissible (:verdict (verdict-for jid sector route))))

(defn open-with?
  "Like `open?` but lets an operator unlock a `:conditional` route by
  attesting that its stated condition holds. An attestation never
  unlocks `:prohibited`, `:unsettled` or `:uncovered` — you cannot
  attest your way past a cited prohibition or past the absence of
  research."
  [jid sector route attestations]
  (let [{:keys [verdict]} (verdict-for jid sector route)]
    (or (= :admissible verdict)
        (and (= :conditional verdict)
             (contains? (set attestations) route)))))

(defn citations
  "The cited rules backing a route — what an operator shows a regulator
  when asked why they believed they could do this."
  [jid sector route]
  (mapv #(select-keys % [:rule/id :rule/title :rule/url :rule/verification
                         :rule/verification-note])
        (:basis-rules (verdict-for jid sector route))))

(defn licence
  "The PRIMARY licence facts for `[jid sector]`, including the
  `:licence/kyoninka-procedure` id when the acquisition procedure is
  modelled as data in `kotoba-lang/kyoninka`.

  Callers must not treat this as the whole regulatory picture — see
  `all-gates`."
  [jid sector]
  (:licence (cat/entry jid sector)))

(defn all-gates
  "Every gate that must be satisfied to operate as principal — the
  primary licence first, then any `:additional-gates`. A sector can
  require more than one authorisation from more than one authority, and
  clearing only the obvious one is a way to be confidently illegal:
  JSIC 4721 refrigerated warehousing needs the 倉庫業法 registration
  from MLIT *and* a 食品衛生法 notification to the public health centre."
  [jid sector]
  (vec (keep identity (cons (licence jid sector) (cat/additional-gates jid sector)))))

(defn plan
  "The whole decision for one concrete situation. Returns

    {:route      :principal | :defer | :blocked
     :open?      bool
     :verdicts   {:route/principal ... :route/defer ...}
     :blockers   [{:rule ... :detail ...}]
     :licence    <licence facts or nil>
     :citations  [...]
     :next       <what to do to open the closed route, or nil>}

  `situation` keys:
    :jurisdiction   案件の法域
    :sector         業種
    :licence-held?  自社が当該許認可の名義人か（既定 false）
    :attestations   #{:route/principal :route/defer} 運営者が宣誓した経路
    :holder         立てる有資格者のレコード（:route/defer を採るとき）
    :matter         個別案件のレコード（同上）

  Preference order is deliberate: 自社が名義人なら principal、そうで
  なければ defer、どちらも開かないなら blocked。ops 層に徹する経路を
  『許認可を取らずに済ませる裏口』として先に試させない。"
  [{:keys [jurisdiction sector licence-held? attestations holder matter]}]
  (let [att (set attestations)
        vp (verdict-for jurisdiction sector :route/principal)
        vd (verdict-for jurisdiction sector :route/defer)
        lic-facts (licence jurisdiction sector)
        principal-open? (and (boolean licence-held?)
                             (open-with? jurisdiction sector :route/principal att))
        defer-catalog-open? (open-with? jurisdiction sector :route/defer att)
        reqs (:licensee-requirements vd)
        holder-violations (if (and defer-catalog-open? (seq reqs))
                            (lic/verify (or holder {}) (or matter {}) reqs)
                            [])
        defer-open? (and defer-catalog-open? (empty? holder-violations))
        chosen (cond principal-open? :principal
                     defer-open? :defer
                     :else :blocked)
        blockers
        (cond-> []
          (and (not principal-open?) (not licence-held?) (= :conditional (:verdict vp)))
          (conj {:rule :licence-not-held
                 :detail (str "自社が名義人になる経路は許認可の取得が条件"
                              (when-let [n (:licence/name lic-facts)] (str "（" n "）"))
                              "。未取得のため principal は開かない。")})

          (and (not principal-open?) (= :prohibited (:verdict vp)))
          (conj {:rule :principal-prohibited
                 :detail (or (:condition vp) "自社が名義人になる経路は塞がっている。")})

          (and (not defer-catalog-open?) (not principal-open?))
          (conj {:rule :defer-not-admissible
                 :detail (str "委譲経路の verdict は " (pr-str (:verdict vd)) "。"
                              (or (:reason vd) (:condition vd) ""))})

          (seq holder-violations)
          (into holder-violations))]
    {:route chosen
     :open? (not= :blocked chosen)
     :verdicts {:route/principal vp :route/defer vd}
     :blockers (if (= :blocked chosen) blockers [])
     :licence lic-facts
     ;; 主たる許認可だけ見て『取れた＝営業できる』と読ませない。
     :additional-gates (cat/additional-gates jurisdiction sector)
     ;; 国家を縛る制約。**verdict には一切影響しない**（catalog の
     ;; supranational-rules の docstring 参照）。読み手が「指令が比例性を
     ;; 要求している」を「許可を飛ばしてよい」と取り違えないよう、
     ;; ラベル付きで verdict の外に置く。
     :supranational-constraints (cat/supranational-constraints jurisdiction sector)
     :citations (citations jurisdiction sector
                           (case chosen :principal :route/principal :route/defer))
     :next (when (= :blocked chosen)
             (cond
               (and (= :conditional (:verdict vp)) (:licence/obtainable-by-company? lic-facts))
               {:action :obtain-licence
                :licence (:licence/name lic-facts)
                :kyoninka-procedure (:licence/kyoninka-procedure lic-facts)
                :detail "法人で取得できる許認可。kyoninka の手続き data で取得を進める。"}

               (seq holder-violations)
               {:action :fix-holder-record
                :detail "委譲先の要件が満たされていない。上記 blockers を解消する。"}

               (contains? #{:unsettled :uncovered} (:verdict vd))
               {:action :research-jurisdiction
                :detail (str "委譲経路が未調査。" (or (:condition vd) "")
                             " 原典を確認してカタログに追記するまで、この経路は開かない。")}

               :else
               {:action :none
                :detail "この法域・業種では現時点でどちらの経路も開かない。"}))}))

(defn compare-sector
  "The same sector across every catalogued jurisdiction. This is what a
  multi-jurisdiction catalog is for: it shows that the same economic
  activity is regulated by structurally different means.

  `:regime` surfaces `:licence/regime` where an entry declares one —
  `:negative-licensing` marks the jurisdictions that impose no prior
  authorisation and instead exclude bad actors after the fact, which is
  a different world from a permit regime even when the underlying
  activity is identical."
  [sector]
  (->> (cat/keys-covered)
       (filter #(= sector (second %)))
       (mapv (fn [[jid _]]
               (let [e (cat/entry jid sector)]
                 {:jurisdiction jid
                  :licence (get-in e [:licence :licence/name])
                  :law (get-in e [:licence :licence/law])
                  :regime (get-in e [:licence :licence/regime] :prior-authorisation)
                  :supranational (mapv :constraint/id (cat/supranational-constraints jid sector))
                  :principal (:verdict (verdict-for jid sector :route/principal))
                  :defer (:verdict (verdict-for jid sector :route/defer))
                  :gates (count (all-gates jid sector))})))))

(defn summary
  "Cross-sector rollup: which routes are open where. Useful for deciding
  what a fleet of actors can actually run in a given jurisdiction."
  []
  (into {}
        (for [[jid sector] (cat/keys-covered)]
          [[jid sector]
           {:principal (:verdict (verdict-for jid sector :route/principal))
            :defer (:verdict (verdict-for jid sector :route/defer))
            :licence (get-in (cat/entry jid sector) [:licence :licence/name])
            :obtainable? (get-in (cat/entry jid sector) [:licence :licence/obtainable-by-company?])}])))
