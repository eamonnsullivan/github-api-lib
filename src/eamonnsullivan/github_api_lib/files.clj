(ns eamonnsullivan.github-api-lib.files
  (:require [eamonnsullivan.github-api-lib.core :as core]))


(defn get-file-text
  "Get the text of a file in a repo.

  You can use \"HEAD\" if you want a file on the default branch, but
  you aren't sure of its name (e.g. \"main\" or \"master\")."
  [access-token owner repo branch filepath]
  (let [variables {:owner owner :name repo :file (format "%s:%s" branch filepath)}
        response (core/make-graphql-post
                  access-token
                  (core/get-graphql "get-file-text-query")
                  variables)]
    (-> response
        :data
        :repository
        :object
        :text)))

(defn get-first-file-text
  "Get the text of a file in a repo. We try each of the files specified
  and return the first one that exists or nil if none of them do."
  [access-token owner repo branch files]
  (first (remove nil? (map #(get-file-text access-token owner repo branch %) files))))
