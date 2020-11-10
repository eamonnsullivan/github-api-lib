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
