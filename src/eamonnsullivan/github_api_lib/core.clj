(ns eamonnsullivan.github-api-lib.core
  (:require [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.java.io :as io]))

(def github-url "https://api.github.com/graphql")

(defn request-opts
  "Add the authorization header to the http request options."
  [access-token]
  {:ssl? true :headers {"Authorization" (str "bearer " access-token)}})

(defn http-post
  "Make a POST request to a url with payload and request options."
  [url payload opts]
  (client/post url (merge {:content-type :json :body payload} opts)))

(defn http-get
  "Make a GET request to a url, with options"
  [access-token url opts]
  (client/get url (merge {:username access-token} opts)))

(defn get-graphql
  "Retrieve the GraphQL as a text blob"
  [name]
  (slurp (io/resource (format "graphql/%s.graphql" name))))

(defn make-graphql-post
  "Make a GraphQL request to Github using the provided query/mutation
  and variables. If there are any errors, throw a RuntimeException,
  with the message set to the first error and the rest of the response
  as the cause/additional information."
  [access-token graphql variables]
  (let [payload (json/write-str {:query graphql :variables variables})
        response (http-post github-url payload (request-opts access-token))
        body (json/read-str (response :body) :key-fn keyword)
        errors (:errors body)]
    (if errors
      (throw (ex-info (:message (first errors)) response))
      body)))

(defn parse-repo
  "Parse a repository url (a full url or just the owner/name part) and
  return a map with :owner and :name keys."
  [url]
  (let [matches (re-matches #"(https://github.com/)?([^/]*)/([^/]*).*$" url)
        [_ _ owner name] matches]
    (if (and owner name (not-empty owner) (not-empty name))
      {:owner owner :name name}
      (throw (ex-info (format "Could not parse repository from url: %s" url) {})))))

(defn pull-request-number
  "Get the pull request number from a full or partial URL."
  [pull-request-url]
  (let [matches (re-matches #"(https://github.com/)?[^/]*/[^/]*/pull/([0-9]*)" pull-request-url)
        [_ _ number] matches]
    (if (not-empty number)
      (Integer/parseInt number)
      (throw (ex-info (format "Could not parse pull request number from url: %s" pull-request-url) {})))))

(defn parse-comment-url
  "Get the comment number and pull request url from an issue comment URL."
  [comment-url]
  (let [matches (re-matches #"(https://github.com/)?([^/]*)/([^/]*)/pull/([0-9]*)#issuecomment-([0-9]*)" comment-url)
        [_ _ owner name number comment] matches]
    (if (and (not-empty owner)
             (not-empty name)
             (not-empty number)
             (not-empty comment))
      {:pullRequestUrl (format "https://github.com/%s/%s/pull/%s" owner name number)
       :issueComment comment}
      (throw (ex-info (format "Could not parse comment from url: %s" comment-url) {})))))

(defn iterate-pages
  [page-getter results? value-getter next-getter]
  (reify
    clojure.lang.Seqable
    (seq [_]
      ((fn next [ret]
         (when (results? ret)
           (concat (value-getter ret)
                   (when-some [k (next-getter ret)]
                     (lazy-seq (next (page-getter k)))))))
       (page-getter nil)))))

(defn get-all-pages
  [getter results? valuesfn]
  (let [get-next (fn [ret] (if (-> ret :data :search :pageInfo :hasNextPage)
                             (-> ret :data :search :pageInfo :endCursor)
                             nil))]
    (into [] (map identity (iterate-pages getter results? valuesfn get-next)))))
