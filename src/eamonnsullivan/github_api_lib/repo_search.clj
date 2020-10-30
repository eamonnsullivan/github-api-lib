(ns eamonnsullivan.github-api-lib.repo-search
  (:require [eamonnsullivan.github-api-lib.core :as core]
            [clojure.string :as string]))

(def ^:dynamic *default-page-size* 25)

(defn get-query
  [org topics]
  (string/trim (str "org:" org " " (string/join " " (doall (map #(str "topic:" %) topics))))))

(defn fix-languages
  [result]
  (map (fn [repo] (merge repo {:languages (into [] (map #(str (% :name)) (-> repo :languages :nodes)))})) result))

(defn get-nodes
  [page]
  (fix-languages (-> page :data :search :nodes)))

(defn get-page-of-repos
  [access-token org topics page-size cursor]
  (core/make-graphql-post
   access-token
   (core/get-graphql "repo-search-query")
   {:first page-size :query (get-query org topics) :after cursor}))

(defn get-repos
  "Get information about repos in a given organisation, with the specified topics"
  ([access-token org topics] (get-repos access-token org topics *default-page-size*))
  ([access-token org topics page-size]
   (let [get-page (partial get-page-of-repos access-token org topics page-size)
         results? (fn [page] (some? (get-nodes page)))]
     (core/get-all-pages get-page results? get-nodes))))
