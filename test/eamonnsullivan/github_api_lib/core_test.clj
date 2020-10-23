(ns eamonnsullivan.github-api-lib.core-test
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [eamonnsullivan.github-api-lib.core :as sut]))

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
  [url opts]
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