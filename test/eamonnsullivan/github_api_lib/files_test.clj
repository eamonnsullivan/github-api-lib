(ns eamonnsullivan.github-api-lib.files-test
  (:require [eamonnsullivan.github-api-lib.files :as sut]
            [eamonnsullivan.github-api-lib.core :as core]
            [clojure.test :refer :all]))

(def file-success (slurp "./test/eamonnsullivan/fixtures/get-file-text-success.json"))
(def file-failure (slurp "./test/eamonnsullivan/fixtures/get-file-text-failure.json"))
(def ^:dynamic test-responses)

(deftest testing-get-file-text
  (testing "get-file-text returns the text of file or nil"
    (with-redefs [core/http-post (fn [_ _ _] {:body file-success})]
      (is (= "File contents." (:text (sut/get-file "secret-token" "owner" "some-repo" "HEAD" "a-file.txt")))))
    (with-redefs [core/http-post (fn [_ _ _] {:body file-failure})]
      (is (= nil (sut/get-file "secret-token" "owner" "some-repo" "HEAD" "does-not-exist.txt"))))))

(defn fake-post
  [_ _ _]
  (if (not= 1 (count @test-responses))
    (first (swap! test-responses #(pop %)))
    {:body "{}"}))

(deftest testing-find-first-file
  (testing "finds the first file to match and returns its content"
    (binding [test-responses (atom '({:body "{\"data\": { \"repository\": { \"object\": null}}}"}
                                     {:body "{\"data\": { \"repository\": { \"object\": null}}}"}
                                     {:body "{\"data\": { \"repository\": { \"object\": null}}}"}
                                     {:body "{\"data\": { \"repository\": { \"object\": { \"text\": \"Found something.\", \"oid\": \"something\"}}}}"}))]
      (with-redefs [core/http-post fake-post]
      (is (= "Found something."
             (:text (sut/get-first-file "secret-token" "owner" "some-repo" "HEAD" ["file1" "file2" "file3"]))))
      (is (= nil
             (sut/get-first-file "secret-token" "owner" "some-repo" "HEAD" ["file1" "file2"])))))))
