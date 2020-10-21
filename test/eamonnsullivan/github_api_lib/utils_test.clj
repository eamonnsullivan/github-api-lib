(ns eamonnsullivan.github-api-lib.utils-test
  (:require [clojure.test :refer :all]
            [eamonnsullivan.github-api-lib.utils :as sut]))


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
  (testing "Returns nil when the url is incomplete or unrecognised"
    (is (= nil (sut/parse-repo "something else")))
    (is (= nil (sut/parse-repo "https://github.com/bbc/")))))

(deftest test-pull-request-number
  (testing "Finds the pull request number when given a url"
    (is (= 1278 (sut/pull-request-number "https://github.com/owner/name/pull/1278")))
    (is (= 8 (sut/pull-request-number "https://github.com/owner/name/pull/8")))
    (is (= 1278456 (sut/pull-request-number "https://github.com/owner/name/pull/1278456"))))
  (testing "Returns nil if no pull request number is found"
    (is (= nil (sut/pull-request-number "something else")))
    (is (= nil (sut/pull-request-number "https://github/owner/name/pull")))))


(deftest test-get-pr-from-comment-url
  (testing "parses a pull request url from a comment url"
    (is (= "https://github.com/eamonnsullivan/github-pr-lib/pull/4"
           (:pullRequestUrl (sut/parse-comment-url "https://github.com/eamonnsullivan/github-pr-lib/pull/4#issuecomment-702092682"))))
    (is (= "https://github.com/eamonnsullivan/github-pr-lib/pull/4"
           (:pullRequestUrl (sut/parse-comment-url "eamonnsullivan/github-pr-lib/pull/4#issuecomment-702092682"))))
    (is (= "702092682"
           (:issueComment (sut/parse-comment-url "eamonnsullivan/github-pr-lib/pull/4#issuecomment-702092682")))))
  (testing "return nil when a pull request url can't be found"
    (is (= nil
           (:pullRequestUrl (sut/parse-comment-url "https://news.bbc.co.uk"))))))
