(ns cloud-itonami.licensed-operator.licensee
  "有資格者レコードの検査。`:route/defer` が成立するかどうかは、結局
  「立てた有資格者について何が真か」に落ちる — カタログはその条件を
  `:licensee-requirements` として持ち、ここが実際に検査する。

  `cloud-itonami-isic-6910-legalsupport` の governor が弁護士について
  課していた4つの検査（資格検証済み / 案件と同一法域 / 自ら精査した /
  範囲内）を、業種に依存しない語彙へ開いたもの。legalsupport は当面
  自前の検査を持ち続け、触るついでにこちらへ寄せる（一括書き換えは
  しない）。

  日付は `\"YYYY-MM-DD\"` 文字列で扱い辞書順比較する。`Date`/`js/Date`
  を使わないので `.cljc` のまま JVM / ClojureScript / WASM で同じ結果に
  なる。"
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------------------
;; Records
;; ---------------------------------------------------------------------------

;; holder — 立てる有資格者
;;   {:holder/id            "..."                     必須
;;    :holder/name          "..."
;;    :holder/licence-name  "古物商許可"
;;    :holder/licence-number "第123456789012号"
;;    :holder/licence-jurisdiction "JPN"               案件法域と突き合わせる
;;    :holder/licence-verified? true                   自己申告でなく確認済みか
;;    :holder/licence-verified-at "2026-07-26"
;;    :holder/licence-scope #{:office-equipment}       区分・品目・業務範囲
;;    :holder/licence-expires-on "2031-03-31"          無期限なら nil
;;   }
;;
;; matter — 委譲したい個別の案件
;;   {:matter/id "..." :matter/jurisdiction "JPN"
;;    :matter/act :office-equipment                    scope と突き合わせる
;;    :matter/as-of "2026-07-26"                       有効期間の判定基準日
;;    :matter/personally-decided? true                 有資格者が自ら判断した記録
;;    :matter/written-contract-ref "契約書ID or nil"}

(defn- blank? [v]
  (or (nil? v) (and (string? v) (str/blank? v)) (= :unknown v)))

;; ---------------------------------------------------------------------------
;; Requirement checks
;; ---------------------------------------------------------------------------

(def checks
  "requirement -> (fn [holder matter] -> nil | {:rule :detail}).
  nil means the requirement is satisfied. Each check fails CLOSED: a
  missing field is a violation, never an assumed pass — an unrecorded
  fact about someone else's licence is exactly the thing this namespace
  exists to refuse."
  {:req/licence-verified
   (fn [h _]
     (when-not (true? (:holder/licence-verified? h))
       {:rule :req/licence-verified
        :detail (str "有資格者 " (pr-str (:holder/id h))
                     " の許認可が検証済みでない。自己申告は検証ではない。")}))

   :req/same-jurisdiction
   (fn [h m]
     (let [hj (:holder/licence-jurisdiction h)
           mj (:matter/jurisdiction m)]
       (cond
         (or (blank? hj) (blank? mj))
         {:rule :req/same-jurisdiction
          :detail "有資格者の資格法域または案件の法域が記録されていない。"}

         (not= hj mj)
         {:rule :req/same-jurisdiction
          :detail (str "資格法域 " (pr-str hj) " が案件法域 " (pr-str mj)
                       " と一致しない。他法域の資格者を立てても委譲は成立せず、"
                       "ops 層が無資格営業に化ける。")})))

   :req/scope-covers
   (fn [h m]
     (let [scope (:holder/licence-scope h)
           act (:matter/act m)]
       (cond
         (blank? act)
         {:rule :req/scope-covers :detail "案件の行為区分が記録されていない。"}

         (empty? scope)
         {:rule :req/scope-covers
          :detail "有資格者の許認可の範囲（区分・品目）が記録されていない。"}

         (not (contains? (set scope) act))
         {:rule :req/scope-covers
          :detail (str "行為 " (pr-str act) " が許認可の範囲 "
                       (pr-str (vec (sort-by str scope)))
                       " に含まれない。許可はあるが区分が違うのは、許可が無いのと同じ。")})))

   :req/not-expired
   (fn [h m]
     (let [exp (:holder/licence-expires-on h)
           as-of (:matter/as-of m)]
       (cond
         (nil? exp) nil ; 有効期間の定めが無い許認可（古物商等）
         (blank? as-of)
         {:rule :req/not-expired :detail "判定基準日 :matter/as-of が記録されていない。"}

         (pos? (compare as-of exp))
         {:rule :req/not-expired
          :detail (str "許認可は " exp " に満了しており、案件基準日 " as-of " 時点で無効。")})))

   :req/personally-decided
   (fn [_ m]
     (when-not (true? (:matter/personally-decided? m))
       {:rule :req/personally-decided
        :detail (str "有資格者が自ら判断し必要に応じ自ら是正した記録が無い。"
                     "名義を立てただけ・押印しただけは委譲ではない。")}))

   :req/written-contract
   (fn [_ m]
     (when (blank? (:matter/written-contract-ref m))
       {:rule :req/written-contract
        :detail "法が要求する書面契約の参照が記録されていない。"}))})

;; ---------------------------------------------------------------------------
;; API
;; ---------------------------------------------------------------------------

(defn verify
  "Check `holder` against `matter` for every requirement in
  `requirements`. Returns a vector of violations, empty when the
  deferral holds.

  An unknown requirement keyword is itself a violation — a caller that
  asks for a check this namespace cannot perform must not be told
  everything is fine."
  [holder matter requirements]
  (vec
   (keep (fn [req]
           (if-let [f (get checks req)]
             (f holder matter)
             {:rule :unknown-requirement
              :detail (str "検査できない要件 " (pr-str req)
                           " が要求された。未実装の検査を満たしたことにはできない。")}))
         (sort-by str requirements))))

(defn ok?
  "True only when every requirement is satisfied."
  [holder matter requirements]
  (empty? (verify holder matter requirements)))

(defn explain
  "Human-readable summary for an audit ledger entry."
  [holder matter requirements]
  (let [vs (verify holder matter requirements)]
    {:holder (:holder/id holder)
     :matter (:matter/id matter)
     :requirements (vec (sort-by str requirements))
     :ok? (empty? vs)
     :violations vs}))
