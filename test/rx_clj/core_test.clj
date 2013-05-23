(ns rx-clj.core-test
  (:require [rx-clj.core :as rx]
            [clojure.test :refer [deftest is testing]]))
(comment
  (rx/subscribe*
    (->>
      (rx/map (fn [a b] (hash-map :a a :b b)) (rx/seq->o [1 2 3 4 5 6])
              (rx/seq->o ["x" "y" 9 10 "z"]))
      (rx/map    #(update-in % [:a] (partial * 2)))
      (rx/filter (comp not number? :b))
      (rx/concat (rx/seq->o (range 3)))
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
  (is (= [] (rx/-into [] (rx/seq->o []))))
  (is (= [] (rx/-into [] (rx/seq->o nil))))
  (is (= [0 1 2 3] (rx/-first (rx/into [] (rx/seq->o (range 4))))))
  (is (= #{0 1 2 3} (rx/-first (rx/into #{} (rx/seq->o (range 4))))))
  (is (= {:a 1 :b 2 :c 3} (rx/-first (rx/into {} (rx/seq->o [[:a 1] [:b 2] [:c 3]]))))))

(deftest test-cons
  (is (= [1] (rx/-into [] (rx/cons 1 (rx/empty)))))
  (is (= [1 2 3 4] (rx/-into [] (rx/cons 1 (rx/seq->o [2 3 4]))))))

(deftest test-concat
  (is (= [:q :r 1 2 3] (rx/-into [] (rx/concat (rx/seq->o [:q :r])
                                               (rx/seq->o [1 2 3]))))))

(deftest test-drop-while
  (is (= (into [] (drop-while even? [2 4 6 8 1 2 3]))
         (rx/-into [] (rx/drop-while even? (rx/seq->o [2 4 6 8 1 2 3])))))
  (is (= (into [] (drop-while even? [2 4 6 8 1 2 3]))
         (rx/-into [] (rx/drop-while even? (rx/seq->o [2 4 6 8 1 2 3]))))))

(deftest test-filter
  (is (= (into [] (->> [:a :b :c :d :e :f :G :e]
                    (filter #{:b :e :G})))
         (rx/-into [] (->> (rx/seq->o [:a :b :c :d :e :f :G :e])
                        (rx/filter #{:b :e :G}))))))

(deftest test-first
  (is (= [:q] (rx/-into [] (rx/first (rx/seq->o [:q :r]))))))

(deftest test-future
  (is (= [15] (rx/-into [] (rx/future* + 1 2 3 4 5))))
  (is (= [15] (rx/-into [] (rx/future (println "HI") (+ 1 2 3 4 5))))) )

(deftest test-interpose
  (is (= (interpose \, [1 2 3])
         (rx/-into [] (rx/interpose \, (rx/seq->o [1 2 3]))))))

(deftest test-keep
  (is (= (into [] (keep identity [true true false]))
         (rx/-into [] (rx/keep identity (rx/seq->o [true true false])))))

  (is (= (into [] (keep #(if (even? %) (* 2 %)) (range 9)))
         (rx/-into [] (rx/keep #(if (even? %) (* 2 %)) (rx/seq->o (range 9)))))))

(deftest test-keep-indexed
  (is (= (into [] (keep-indexed (fn [i v]
                                  (if (even? i) v))
                                [true true false]))
         (rx/-into [] (rx/keep-indexed (fn [i v]
                                         (if (even? i) v))
                                       (rx/seq->o [true true false]))))))

(deftest test-zip
  (testing "is happy with less than 4 args"
    (is (= [[1 2 3]] (rx/-into [] (rx/zip vector
                                        (rx/seq->o [1]) (rx/seq->o [2]) (rx/seq->o [3]))))))
  (testing "is happy with more than 4 args"
    (is (= [[1 2 3 4 5 6 7 8]]
           (rx/-into [] (rx/zip vector
                              (rx/seq->o [1])
                              (rx/seq->o [2])
                              (rx/seq->o [3])
                              (rx/seq->o [4])
                              (rx/seq->o [5])
                              (rx/seq->o [6])
                              (rx/seq->o [7])
                              (rx/seq->o [8])))))))

(deftest test-map
  (is (= (into {} (map (juxt identity name)
                       [:q :r :s :t :u]))
         (rx/-into {} (rx/map (juxt identity name)
                              (rx/seq->o [:q :r :s :t :u])))))
  (is (= (into [] (map vector
                       [:q :r :s :t :u]
                       (range 10)
                       ["a" "b" "c" "d" "e"] ))
         (rx/-into [] (rx/map vector
                              (rx/seq->o [:q :r :s :t :u])
                              (rx/seq->o (range 10) )
                              (rx/seq->o ["a" "b" "c" "d" "e"] )))))
  ; check > 4 arg case
  (is (= (into [] (map vector
                       [:q :r :s :t :u]
                       [:q :r :s :t :u]
                       [:q :r :s :t :u]
                       (range 10)
                       (range 10)
                       (range 10)
                       ["a" "b" "c" "d" "e"]
                       ["a" "b" "c" "d" "e"]
                       ["a" "b" "c" "d" "e"]))
         (rx/-into [] (rx/map vector
                            (rx/seq->o [:q :r :s :t :u])
                            (rx/seq->o [:q :r :s :t :u])
                            (rx/seq->o [:q :r :s :t :u])
                            (rx/seq->o (range 10))
                            (rx/seq->o (range 10))
                            (rx/seq->o (range 10))
                            (rx/seq->o ["a" "b" "c" "d" "e"])
                            (rx/seq->o ["a" "b" "c" "d" "e"])
                            (rx/seq->o ["a" "b" "c" "d" "e"]))))))

(deftest test-map-indexed
  (is (= (map-indexed vector [:a :b :c])
         (rx/-into [] (rx/map-indexed vector (rx/seq->o [:a :b :c]))))))

(deftest test-merge
  (is (= [{:a 1 :b 2 :c 3 :d 4}]
         (rx/-into [] (rx/merge (rx/seq->o [{:a 1 :d 0} {:b 2} {:c 3} {:d 4} ]))))))

(deftest test-mapcat
  (let [f  (fn [v] [v (* v v)])
        xs (range 10)]
    (is (= (mapcat f xs)
           (rx/-into [] (rx/mapcat (comp rx/seq->o f) (rx/seq->o xs))))))
  (comment
    (is (= (into [] (mapcat vector
                              [:q :r :s :t :u]
                              (range 10)
                              ["a" "b" "c" "d" "e"] ))
        (rx/-into [] (rx/mapcat vector
                              (rx/seq->o [:q :r :s :t :u])
                              (rx/seq->o (range 10) )
                              (rx/seq->o ["a" "b" "c" "d" "e"] )))))))

(deftest test-next
  (let [in [:q :r :s :t :u]]
    (is (= (next in) (rx/-into [] (rx/next (rx/seq->o in)))))))

(deftest test-rest
  (let [in [:q :r :s :t :u]]
    (is (= (rest in) (rx/-into [] (rx/rest (rx/seq->o in)))))))

(deftest test-reduce
  (is (= (reduce + 0 (range 4))
         (rx/-first (rx/reduce + 0 (rx/seq->o (range 4)))))))

(deftest test-reductions
  (is (= (into [] (reductions + 0 (range 4)))
         (rx/-into [] (rx/reductions + 0 (rx/seq->o (range 4)))))))

(deftest test-second
  (is (= [:r] (rx/-into [] (rx/second (rx/seq->o [:q :r :s]))))))


(deftest test-some
  (is (= [:r] (rx/-into [] (rx/some #{:r :s :t} (rx/seq->o [:q :v :r])))))
  (is (= [] (rx/-into [] (rx/some #{:r :s :t} (rx/seq->o [:q :v]))))))

(deftest test-split-with
  (is (= (split-with (partial >= 3) (range 6))
         (->> (rx/seq->o (range 6))
              (rx/split-with (partial >= 3))
              rx/-first
              (map (partial rx/-into []))))))

(deftest test-take-while
  (is (= (into [] (take-while even? [2 4 6 8 1 2 3]))
         (rx/-into [] (rx/take-while even? (rx/seq->o [2 4 6 8 1 2 3]))))))

(deftest test-weave
  (is (= [[1 3 5] [2 4 6]]
         (let [r (rx/-into []
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

