(ns eamonnsullivan.github-api-lib.repos
  (:require [clojure.string :as string]
            [eamonnsullivan.github-api-lib.core :as core]))

(def ^:dynamic *default-page-size* 10)

(defn parse-repo
  "Parse a repository url (a full url or just the owner/name part) and
  return a map with :owner and :name keys."
  [url]
  (let [matches (re-matches #"(https://github.com/|git@github.com:)?([^/]*)/([^/]*)(.git)?.*$" url)
        [_ _ owner name _] matches]
    (if (and owner name (not-empty owner) (not-empty name))
      {:owner owner
       :name (if (string/ends-with? name ".git")
               (string/replace name #".git$" "")
               name)}
      (throw (ex-info (format "Could not parse repository from url: %s" url) {})))))

(defn get-repo-id
  "Get the unique ID value for a repository."
  ([access-token url]
   (let [repo (parse-repo url)
         owner (:owner repo)
         name (:name repo)]
     (when repo
       (get-repo-id access-token owner name))))
  ([access-token owner repo-name]
   (let [variables {:owner owner :name repo-name}]
     (-> (core/make-graphql-post
          access-token
          (core/get-graphql "get-repo-id-query")
          variables)
         :data
         :repository
         :id))))

(defn get-topics
  [page]
  (into [] (map #(str (-> % :topic :name))
                (-> page :data :node :repositoryTopics :nodes))))

(defn get-page-of-topics
  "Get a page of topics on a repo"
  [access-token repo-id page-size cursor]
  (-> (core/make-graphql-post
       access-token
       (core/get-graphql "repo-topic-query")
       {:repoId repo-id :first page-size :after cursor})))

(defn get-repo-topics
  "Get all of the topics attached to a repo."
  ([access-token url]
   (get-repo-topics access-token url *default-page-size*))
  ([access-token url page-size]
   (let [repo-id (get-repo-id access-token url)
         get-page (partial get-page-of-topics access-token repo-id page-size)
         results? (fn [page] (some? (get-topics page)))
         get-next (fn [ret] (if (-> ret :data :node :repositoryTopics :pageInfo :hasNextPage)
                              (-> ret :data :node :repositoryTopics :pageInfo :endCursor)
                              nil))]
     (core/get-all-pages get-page results? get-topics get-next))))
