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

(defn iteration
  "Taken from https://clojure.atlassian.net/browse/CLJ-2555.
   This function can just be removed when we start using 1.11 of Clojure.

   creates a seqable/reducible given step!,
   a function of some (opaque continuation data) k

   step! - fn of k/nil to (opaque) 'ret'

   :some? - fn of ret -> truthy, indicating there is a value
            will not call vf/kf nor continue when false
   :vf - fn of ret -> v, the values produced by the iteration
   :kf - fn of ret -> next-k or nil (will not continue)
   :initk - the first value passed to step!

   vf, kf default to identity, some? defaults to some?, initk defaults to nil

   it is presumed that step! with non-initk is unreproducible/non-idempotent
   if step! with initk is unreproducible, it is on the consumer to not consume twice"
  [step! & {:keys [vf kf some? initk]
            :or {vf identity
                 kf identity
                 some? some?
                 initk nil}}]
  (reify
    clojure.lang.Seqable
    (seq [_]
      ((fn next [ret]
         (when (some? ret)
           (cons (vf ret)
                 (when-some [k (kf ret)]
                   (lazy-seq (next (step! k)))))))
       (step! initk)))
    clojure.lang.IReduceInit
    (reduce [_ rf init]
      (loop [acc init
             ret (step! initk)]
        (if (some? ret)
          (let [acc (rf acc (vf ret))]
            (if (reduced? acc)
              @acc
              (if-some [k (kf ret)]
                (recur acc (step! k))
                acc)))
          acc)))))

(defn get-all-pages
  "Convenience function for getting all of the results from a paged search.

   getter: function that returns a single page, given a cursor string.
   results?: function that returns a boolean indicating whether the current page contains values.
   valuesfn: function to extract the values from a page.
   get-nextfn: function to get the cursor for the next page. Optional. Defaults to one that looks
               for the values in :data -> :search -> :pageInfo.

   Returns a flattened, realised sequence with all of the result. Don't
   use this on an infinite or enormous sequence."
  ([getter results? valuesfn]
   (let [get-next (fn [ret] (if (-> ret :data :search :pageInfo :hasNextPage)
                              (-> ret :data :search :pageInfo :endCursor)
                              nil))]
     (get-all-pages getter results? valuesfn get-next)))
  ([getter results? valuesfn get-nextfn]
   (vec (reduce
         (fn [acc page] (concat acc page))
         []
         (iteration getter :vf valuesfn :kf get-nextfn :some? results?)))))
