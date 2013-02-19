(ns rx-clj.core-test
  (:require [rx-clj.core :as rx]
            [clojure.test :refer [deftest is testing]]))
(comment
  (rx/subscribe*
    (->>
      (rx/map (fn [a b] (hash-map :a a :b b)) (rx/seq [1 2 3 4 5 6])
              (rx/seq ["x" "y" 9 10 "z"]))
      (rx/map    #(update-in % [:a] (partial * 2)))
      (rx/filter (comp not number? :b))
      (rx/concat (rx/seq (range 3)))
      (rx/drop 1)
      (rx/take 6)
      (rx/into []))
    println
    println))

(deftest test-generator
  (testing "calls on-completed automatically"
    (let [o (rx/generator [o])
          called (atom nil)]
      (rx/subscribe* o (fn [v]) nil #(reset! called "YES"))
      (is (= "YES" @called))))

  (testing "exceptions automatically go to on-error"
    (let [expected (IllegalArgumentException. "hi")
          actual   (atom nil)]
      (rx/subscribe* (rx/generator [o] (throw expected))
                     (fn [v])
                     #(reset! actual %))
      (is (identical? expected @actual)))))

(deftest test-seq
  (is (= [0 1 2 3] (rx/-first (rx/into [] (rx/seq (range 4))))))
  (is (= #{0 1 2 3} (rx/-first (rx/into #{} (rx/seq (range 4))))))
  (is (= {:a 1 :b 2 :c 3} (rx/-first (rx/into {} (rx/seq [[:a 1] [:b 2] [:c 3]]))))))

(deftest test-cons
  (is (= [1] (rx/-to [] (rx/cons 1 (rx/empty)))))
  (is (= [1 2 3 4] (rx/-to [] (rx/cons 1 (rx/seq [2 3 4]))))))

(deftest test-concat
  (is (= [:q :r 1 2 3] (rx/-to [] (rx/concat (rx/seq [:q :r])
                                               (rx/seq [1 2 3]))))))

(deftest test-drop-while
  (is (= (into [] (drop-while even? [2 4 6 8 1 2 3]))
         (rx/-to [] (rx/drop-while even? (rx/seq [2 4 6 8 1 2 3])))))
  (is (= (into [] (drop-while even? [2 4 6 8 1 2 3]))
         (rx/-to [] (rx/drop-while even? (rx/seq [2 4 6 8 1 2 3]))))))

(deftest test-filter
  (is (= (into [] (->> [:a :b :c :d :e :f :G :e]
                    (filter #{:b :e :G})))
         (rx/-to [] (->> (rx/seq [:a :b :c :d :e :f :G :e])
                        (rx/filter #{:b :e :G}))))))

(deftest test-first
  (is (= [:q] (rx/-to [] (rx/first (rx/seq [:q :r]))))))

(deftest test-future
  (is (= [15] (rx/-to [] (rx/future* + 1 2 3 4 5))))
  (is (= [15] (rx/-to [] (rx/future (println "HI") (+ 1 2 3 4 5))))) )

(deftest test-interpose
  (is (= (interpose \, [1 2 3])
         (rx/-to [] (rx/interpose \, (rx/seq [1 2 3]))))))

(deftest test-keep
  (is (= (into [] (keep identity [true true false]))
         (rx/-to [] (rx/keep identity (rx/seq [true true false])))))

  (is (= (into [] (keep #(if (even? %) (* 2 %)) (range 9)))
         (rx/-to [] (rx/keep #(if (even? %) (* 2 %)) (rx/seq (range 9)))))))

(deftest test-keep-indexed
  (is (= (into [] (keep-indexed (fn [i v]
                                  (if (even? i) v))
                                [true true false]))
         (rx/-to [] (rx/keep-indexed (fn [i v]
                                         (if (even? i) v))
                                       (rx/seq [true true false]))))))

(deftest test-last
  (is (= [:r] (rx/-to [] (rx/last (rx/seq [:q :r]))))))

(deftest test-map
  (is (= (into {} (map (juxt identity name)
                       [:q :r :s :t :u]))
         (rx/-to {} (rx/map (juxt identity name)
                              (rx/seq [:q :r :s :t :u])))))
  (is (= (into [] (map vector
                       [:q :r :s :t :u]
                       (range 10)
                       ["a" "b" "c" "d" "e"] ))
         (rx/-to [] (rx/map vector
                              (rx/seq [:q :r :s :t :u])
                              (rx/seq (range 10) )
                              (rx/seq ["a" "b" "c" "d" "e"] ))))))

(deftest test-map-indexed
  (is (= (map-indexed vector [:a :b :c])
         (rx/-to [] (rx/map-indexed vector (rx/seq [:a :b :c]))))))

(deftest test-merge
  (is (= [{:a 1 :b 2 :c 3 :d 4}]
         (rx/-to [] (rx/merge (rx/seq [{:a 1 :d 0} {:b 2} {:c 3} {:d 4} ]))))))

(deftest test-mapcat
  (let [f  (fn [v] [v (* v v)])
        xs (range 10)]
    (is (= (mapcat f xs)
           (rx/-to [] (rx/mapcat (comp rx/seq f) (rx/seq xs))))))
  (comment
    (is (= (into [] (mapcat vector
                              [:q :r :s :t :u]
                              (range 10)
                              ["a" "b" "c" "d" "e"] ))
        (rx/-to [] (rx/mapcat vector
                              (rx/seq [:q :r :s :t :u])
                              (rx/seq (range 10) )
                              (rx/seq ["a" "b" "c" "d" "e"] )))))))

(deftest test-next
  (let [in [:q :r :s :t :u]]
    (is (= (next in) (rx/-to [] (rx/next (rx/seq in)))))))

(deftest test-rest
  (let [in [:q :r :s :t :u]]
    (is (= (rest in) (rx/-to [] (rx/rest (rx/seq in)))))))

(deftest test-reduce
  (is (= (reduce + 0 (range 4))
         (rx/-first (rx/reduce + 0 (rx/seq (range 4)))))))

(deftest test-reductions
  (is (= (into [] (reductions + 0 (range 4)))
         (rx/-to [] (rx/reductions + 0 (rx/seq (range 4)))))))

(deftest test-second
  (is (= [:r] (rx/-to [] (rx/second (rx/seq [:q :r :s]))))))


(deftest test-some
  (is (= [:r] (rx/-to [] (rx/some #{:r :s :t} (rx/seq [:q :v :r])))))
  (is (= [] (rx/-to [] (rx/some #{:r :s :t} (rx/seq [:q :v]))))))

(deftest test-split-with
  (is (= (split-with (partial >= 3) (range 6))
         (->> (rx/seq (range 6))
              (rx/split-with (partial >= 3))
              rx/-first
              (map (partial rx/-to []))))))

(deftest test-take-while
  (is (= (into [] (take-while even? [2 4 6 8 1 2 3]))
         (rx/-to [] (rx/take-while even? (rx/seq [2 4 6 8 1 2 3]))))))

(deftest test-weave
  (is (= [[1 3 5] [2 4 6]]
         (let [r (rx/-to []
                   (rx/weave [(rx/future-generator [o]
                                                   (doseq [x [1 3 5]]
                                                     (Thread/sleep 10)
                                                     (rx/emit o x)))
                              (rx/future-generator [o]
                                                   (doseq [x [2 4 6]]
                                                     (Thread/sleep 10)
                                                     (rx/emit o x)))]))]
           ; make sure each sequence maintained original order
           [(keep #{1 3 5} r)
            (keep #{2 4 6} r) ]))))

(comment (rx/subscribe* (rx/future* + 1 2 3 4 5)
                        (fn [v] (println "RESULT: " v))
                        (fn [e] (println "ERROR: " e))
                        #(println "COMPLETED")))

(comment (rx/subscribe* (rx/future
                          (Thread/sleep 5000)
                          (+ 100 200))
                        (fn [v] (println "RESULT: " v))
                        (fn [e] (println "ERROR: " e))
                        #(println "COMPLETED")))

(comment (rx/subscribe* (rx/future
                          (Thread/sleep 2000)
                          (throw (Exception. "Failed future")))
                        (fn [v] (println "RESULT: " v))
                        (fn [e] (println "ERROR: " e))
                        #(println "COMPLETED")))

