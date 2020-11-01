(ns eamonnsullivan.github-api-lib.files
  (:require [eamonnsullivan.github-api-lib.core :as core]))


(defn get-file
  "Get information and properties on a file in a repo, or nil if the
   file doesn't exist.

   You can use \"HEAD\" if you want a file on the default branch, but
   you aren't sure of its name (e.g. \"main\" or \"master\")."
  [access-token owner repo branch filepath]
  (let [variables {:owner owner :name repo :file (format "%s:%s" branch filepath)}
        response (core/make-graphql-post
                  access-token
                  (core/get-graphql "get-file-text-query")
                  variables)
        object (-> response :data :repository :object)]
    (when object
      (merge {:filepath filepath} object))))

(defn get-first-file
  "Get the first matching file in a repo. We try each of the files specified
  and return the first one that exists or nil if none of them do."
  [access-token owner repo branch files]
  (loop [files files
         result nil]
    (if-not (seq files)
      result
      (let [result (get-file access-token owner repo branch (first files))]
        (if (:oid result)
          result
          (recur (rest files)
                 nil))))))
