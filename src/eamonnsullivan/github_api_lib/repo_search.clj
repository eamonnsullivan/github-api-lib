(ns eamonnsullivan.github-api-lib.repo-search
  (:require [eamonnsullivan.github-api-lib.core :as core]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(def ^:dynamic *default-page-size* 25)

(def repo-search-query (slurp (io/resource "graphql/repo-search-query.graphql")))

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
   repo-search-query
   {:first page-size :query (get-query org topics) :after cursor}))

(defn get-all-pages
  [access-token org topics page-size]
  (let [page (get-page-of-repos access-token org topics page-size nil)]
    (loop [page page
           result []]
      (let [pageInfo (-> page :data :search :pageInfo)
            has-next (pageInfo :hasNextPage)
            cursor (pageInfo :endCursor)
            result (concat result (get-nodes page))]
        (if-not has-next
          (into [] result)
          (recur (get-page-of-repos access-token org topics page-size cursor)
                 (get-nodes page)))))))

(defn get-repos
  "Get information about repos in a given organisation, with the specified topics"
  ([access-token org topics] (get-all-pages access-token org topics *default-page-size*))
  ([access-token org topics page-size] (get-all-pages access-token org topics page-size)))
