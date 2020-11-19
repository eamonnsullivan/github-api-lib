(ns eamonnsullivan.github-api-lib.repos-test
  (:require [eamonnsullivan.github-api-lib.repos :as sut]
            [eamonnsullivan.github-api-lib.core :as core]
            [clojure.test :refer :all]))


(def repo-id-response-success (slurp "./test/eamonnsullivan/fixtures/repo-response-success.json"))
(def repo-id-response-failure (slurp "./test/eamonnsullivan/fixtures/repo-response-failure.json"))
(def repo-topic-response-success (slurp "./test/eamonnsullivan/fixtures/repo-topic-response.json"))
(def repo-info-response-success (slurp "./test/eamonnsullivan/fixtures/repo-info-response.json"))
(def repo-info-response-failure (slurp "./test/eamonnsullivan/fixtures/repo-info-response-failure.json"))

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
  (testing "handles ssh urls as well"
    (is (= {:owner "eamonnsullivan" :name "emacs.d"} (sut/parse-repo "git@github.com:eamonnsullivan/emacs.d.git")))
    (is (= {:owner "eamonnsullivan" :name "github-search"} (sut/parse-repo "git@github.com:eamonnsullivan/github-search.git")))
    (is (= {:owner "eamonnsullivan" :name "github-pr-lib"} (sut/parse-repo "git@github.com:eamonnsullivan/github-pr-lib.git"))))
  (testing "Throws an exception when the url is incomplete or unrecognised"
    (is (thrown-with-msg? RuntimeException
                          #"Could not parse repository from url: something else"
                          (sut/parse-repo "something else")))
    (is (thrown-with-msg? RuntimeException
                          #"Could not parse repository from url: https://github.com/bbc"
                          (sut/parse-repo "https://github.com/bbc")))))

(deftest test-get-repo-id
  (with-redefs [core/http-post (fn [_ _ _] {:body repo-id-response-success})]
    (testing "parses id from successful response"
      (is (= "MDEwOlJlcG9zaXRvcnkyOTczNzY1NTc=" (sut/get-repo-id "secret-token" "owner" "repo-name"))))
    (testing "handles url instead of separate owner/repo-name"
      (is (= "MDEwOlJlcG9zaXRvcnkyOTczNzY1NTc=" (sut/get-repo-id "secret-token" "owner/repo-name")))
      (is (= "MDEwOlJlcG9zaXRvcnkyOTczNzY1NTc=" (sut/get-repo-id "secret-token" "https://github.com/owner/repo-name")))))
  (with-redefs [core/http-post (fn [_ _ _] {:body repo-id-response-failure})]
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
  (with-redefs [core/http-post (fn [_ _ _] {:body repo-topic-response-success})]
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

(deftest test-get-repo-info
  (with-redefs [core/http-post (fn [_ _ _] {:body repo-info-response-success})]
    (testing "Returns information about a repository"
      (is (= "something"
             (:name (sut/get-repo-info "secret-token" "owner/something"))))
      (is (= "https://github.com/owner/something"
             (:url (sut/get-repo-info "secret-token" "owner" "something"))))
      (is (= "Something is a really cool microservice."
             (:description (sut/get-repo-info "secret-token" "owner" "something"))))
      (is (= "MDEwOlJlcG9zaXRvcnkxMTU3MTY1MzU="
             (:id (sut/get-repo-info "secret-token" "owner" "something"))))
      (is (= ["JavaScript" "Scala"]
             (:languages (sut/get-repo-info "secret-token" "owner/something"))))))
  (with-redefs [core/http-post (fn [_ _ _] {:body repo-info-response-failure})]
    (testing "Throws with appropriate message on error"
      (is (thrown-with-msg? RuntimeException
                            #"Could not resolve to a Repository with the name 'owner/something-random'."
                            (sut/get-repo-info "secret-token" "whatever/whatever"))))))
