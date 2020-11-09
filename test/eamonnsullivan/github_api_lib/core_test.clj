(ns eamonnsullivan.github-api-lib.core-test
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [eamonnsullivan.github-api-lib.core :as sut]))

(def repo-id-response-success (slurp "./test/eamonnsullivan/fixtures/repo-response-success.json"))
(def repo-id-response-failure (slurp "./test/eamonnsullivan/fixtures/repo-response-failure.json"))
(def repo-topic-response-success (slurp "./test/eamonnsullivan/fixtures/repo-topic-response.json"))

(defn test-args-to-get
  [url opts]
  (is (= "access-token" (:username opts)))
  (is (= "application/vnd.github.v3+json" (:accept opts)))
  (is (= "https://github.com/someone/somewhere" url))
  "{}");

(deftest test-http-get
  (testing "Get requests add the :username to opts"
    (with-redefs [client/get test-args-to-get]
      (is (= "{}" (sut/http-get
                   "access-token"
                   "https://github.com/someone/somewhere"
                   {:accept "application/vnd.github.v3+json"}))))))

(defn test-args-to-post
  [_ opts]
  (is (= :json (:content-type opts)))
  "{}")

(deftest test-http-post
  (testing "Post requests add content type"
    (with-redefs [client/post test-args-to-post]
      (is (= "{}" (sut/http-post
                   "https://github.com"
                   {}
                   {}))))))


(deftest test-parse-repo
  (testing "Finds owner and repo name from github url"
    (is (= {:owner "eamonnsullivan" :name "emacs.d"} (sut/parse-repo "https://github.com/eamonnsullivan/emacs.d")))
    (is (= {:owner "eamonnsullivan" :name "github-search"} (sut/parse-repo "https://github.com/eamonnsullivan/github-search/blob/master/src/eamonnsullivan/github_search.clj")))
    (is (= {:owner "eamonnsullivan" :name "github-pr-lib"} (sut/parse-repo "https://github.com/eamonnsullivan/github-pr-lib/pull/1")))
    (is (= {:owner "bbc" :name "optimo"} (sut/parse-repo "https://github.com/bbc/optimo/pull/1277"))))
  (testing "github hostname is optional"
    (is (= {:owner "eamonnsullivan" :name "emacs.d"} (sut/parse-repo "eamonnsullivan/emacs.d")))
    (is (= {:owner "eamonnsullivan" :name "github-search"} (sut/parse-repo "eamonnsullivan/github-search/blob/master/src/eamonnsullivan/github_search.clj")))
    (is (= {:owner "eamonnsullivan" :name "github-pr-lib"} (sut/parse-repo "eamonnsullivan/github-pr-lib/pull/1")))
    (is (= {:owner "bbc" :name "optimo"} (sut/parse-repo "bbc/optimo/pull/1277"))))
  (testing "Throws an exception when the url is incomplete or unrecognised"
    (is (thrown-with-msg? RuntimeException
                          #"Could not parse repository from url: something else"
                          (sut/parse-repo "something else")))
    (is (thrown-with-msg? RuntimeException
                          #"Could not parse repository from url: https://github.com/bbc"
                          (sut/parse-repo "https://github.com/bbc")))))

(deftest test-pull-request-number
  (testing "Finds the pull request number when given a url"
    (is (= 1278 (sut/pull-request-number "https://github.com/owner/name/pull/1278")))
    (is (= 8 (sut/pull-request-number "https://github.com/owner/name/pull/8")))
    (is (= 1278456 (sut/pull-request-number "https://github.com/owner/name/pull/1278456"))))
  (testing "Throws if no pull request number is found"
    (is (thrown-with-msg? RuntimeException
                          #"Could not parse pull request number from url: something else"
                          (sut/pull-request-number "something else")))
    (is (thrown-with-msg? RuntimeException
                          #"Could not parse pull request number from url: https://github.com/owner/name/pull"
                          (sut/pull-request-number "https://github.com/owner/name/pull")))))


(deftest test-get-pr-from-comment-url
  (testing "parses a pull request url from a comment url"
    (is (= "https://github.com/eamonnsullivan/github-pr-lib/pull/4"
           (:pullRequestUrl (sut/parse-comment-url "https://github.com/eamonnsullivan/github-pr-lib/pull/4#issuecomment-702092682"))))
    (is (= "https://github.com/eamonnsullivan/github-pr-lib/pull/4"
           (:pullRequestUrl (sut/parse-comment-url "eamonnsullivan/github-pr-lib/pull/4#issuecomment-702092682"))))
    (is (= "702092682"
           (:issueComment (sut/parse-comment-url "eamonnsullivan/github-pr-lib/pull/4#issuecomment-702092682")))))
  (testing "Throws when a pull request url can't be found"
    (is (thrown-with-msg? RuntimeException
                          #"Could not parse comment from url: https://news.bbc.co.uk"
                          (sut/parse-comment-url "https://news.bbc.co.uk")))))

(defn fake-get-pages
  [cursor]
  (let [first-page {:data
                    {:search
                     {:repositoryCount 3
                      :nodes [{:name "one", :size 25}
                              {:name "two", :size 25}]
                      :pageInfo {:hasNextPage true, :endCursor "cursor"}}}}
        last-page {:data
                   {:search
                    {:repositoryCount 3
                     :nodes [{:name "three", :size 50}]
                     :pageInfo {:hasNextPage false, :endCursor "cursor2"}}}}]
    (if-not cursor
      first-page
      last-page)))

(deftest test-iteration
  (testing "gets the next page"
    (is (= "three"
           (-> (sut/iteration
                fake-get-pages
                :some? #(some? (-> % :data :search :nodes))
                :vf #(-> % :data :search :nodes)
                :kf #(if (-> % :data :search :pageInfo :hasNextPage)
                       (-> % :data :search :pageInfo :endCursor)
                       nil))
               last ; last page
               last ; last value on that page
               :name)))))

(deftest test-iteration-reduced
  (let [answer (sut/iteration
                fake-get-pages
                :some? #(some? (-> % :data :search :nodes))
                :vf #(-> % :data :search :nodes)
                :kf #(if (-> % :data :search :pageInfo :hasNextPage)
                       (-> % :data :search :pageInfo :endCursor)
                       nil))]
    (testing "sums the values on pages"
      (is (= 100
             (reduce
              (fn [acc page] (apply + acc (map :size page)))
              0
              answer))))
    (testing "uses reduced to short circuit some results"
      (is (= 40
             (reduce
              (fn [acc page] (let [size (apply + acc (map :size page))]
                               (if (> size 40)
                                 (reduced 40)
                                 size)))
              0
              answer)))))
  (testing "handles no results correctly"
    (is (= 42
           (reduce
            (fn [acc page] (apply + acc (map :size page)))
            42
            (sut/iteration
             fake-get-pages
             :some? #(some? (-> % :data :search :not-there))
             :vf #(-> % :data :search :nodes)
             :kf #(if (-> % :data :search :pageInfo :hasNextPage)
                    (-> % :data :search :pageInfo :endCursor)
                    nil)))))))

(deftest test-get-repo-id
  (with-redefs [sut/http-post (fn [_ _ _] {:body repo-id-response-success})]
    (testing "parses id from successful response"
      (is (= "MDEwOlJlcG9zaXRvcnkyOTczNzY1NTc=" (sut/get-repo-id "secret-token" "owner" "repo-name"))))
    (testing "handles url instead of separate owner/repo-name"
      (is (= "MDEwOlJlcG9zaXRvcnkyOTczNzY1NTc=" (sut/get-repo-id "secret-token" "owner/repo-name")))
      (is (= "MDEwOlJlcG9zaXRvcnkyOTczNzY1NTc=" (sut/get-repo-id "secret-token" "https://github.com/owner/repo-name")))))
  (with-redefs [sut/http-post (fn [_ _ _] {:body repo-id-response-failure})]
    (testing "throws exception on error"
      (is (thrown-with-msg? RuntimeException
                            #"Could not resolve to a Repository with the name 'eamonnsullivan/not-there'."
                            (sut/get-repo-id "secret-token" "owner" "repo-name")))))
  (with-redefs [sut/parse-repo (fn [_] (throw (ex-info "error" {})))]
    (testing "passes on thrown exceptions from parse-repo"
      (is (thrown-with-msg? RuntimeException
                            #"error"
                            (sut/get-repo-id "secret-token" "whatever/whatever"))))))

(deftest test-get-page-of-topics
  (with-redefs [sut/http-post (fn [_ _ _] {:body repo-topic-response-success})]
    (testing "gets topics from a page"
      (is (= {:data
              {:node
               {:repositoryTopics
                {:nodes
                 [{:topic {:name "cps"}}
                  {:topic {:name "cam"}}
                  {:topic {:name "dpub"}}
                  {:topic {:name "node"}}
                  {:topic {:name "frontend"}}
                  {:topic {:name "optimo"}}],
                 :pageInfo
                 {:hasNextPage false, :endCursor "Y3Vyc29yOnYyOpHOARzyUg=="}}}}}
             (sut/get-page-of-topics "secret-token" "repo-id" 10 nil))))))

(defn fake-paging-response
  [_ _ _ cursor]
  (let [first-page {:data
                    {:node
                     {:repositoryTopics
                      {:nodes
                       [ {:topic {:name "tag1"}} {:topic {:name "tag2"}}]
                       :pageInfo {:hasNextPage true, :endCursor "cursor"}}}}}
        last-page {:data
                   {:node
                    {:repositoryTopics
                     {:nodes
                      [ {:topic {:name "tag3"}} {:topic {:name "tag4"}}]
                      :pageInfo {:hasNextPage false, :endCursor "cursor2"}}}}}]
    (if-not cursor
      first-page
      last-page)))

(deftest test-get-repo-topics
  (with-redefs [sut/get-repo-id (fn[_ _] "some-id")
                sut/get-page-of-topics fake-paging-response]
    (testing "follows pages"
      (is (= ["tag1" "tag2" "tag3" "tag4"]
             (sut/get-repo-topics "secret-token" "eamonnsullivan/github-api-lib"))))))
