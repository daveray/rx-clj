(ns rx-clj.core
  (:refer-clojure :exclude [concat cons drop drop-while empty
                            filter first future
                            interpose into keep keep-indexed last
                            map mapcat map-indexed
                            merge next partition reduce reductions
                            rest second seq some split-with
                            take take-while])
  (:import [rx Observable Observer Subscription]
           [rx.util AtomicObservableSubscription]))

(declare map map-indexed reduce take take-while zip)

(defn predicate
  "Turn f into a predicate that returns true/false like Rx predicates should"
  [f]
  (comp boolean f))

;################################################################################

(defn emit
  "Call onNext on the given observer. I think emit is clearer, but am willing to be
  persuaded otherwise."
  [^Observer o value]
  (.onNext o value))

(defn done
  "Call onCompleted on the given observer. I think done is clearer, but am willing to be
  persuaded otherwise."
  [^Observer o]
  (.onCompleted o))

(defn error
  "Call onError on the given observer. I think error is clearer, but am willing to be
  persuaded otherwise."
  [^Observer o e]
  (.onError o e))

(defn wrap-on-completed
  "Wrap handler with code that automaticaly calls (done)"
  [handler]
  (fn [observer]
    (handler observer)
    (done observer)))

(defn wrap-on-error
  "Wrap handler with code that automaticaly calls (error) if an exception is thrown"
  [handler]
  (fn [observer]
    (try
      (handler observer)
      (catch Exception e
        (error observer e)))))

(defn on-error-return
  [^Observer o f]
  (.onErrorReturn o f))

(defn ^Observable observable
  "Create an observable from the given handler. When subscribed to, (handler observer)
  is called at which point, handler can start emitting values, etc."
  [handler]
  (Observable/create handler))

;################################################################################

(defn ^Subscription subscribe*
  ([^Observable o on-next-fn]
    (subscribe* o on-next-fn nil))
  ([^Observable o on-next-fn on-error-fn]
    (subscribe* o on-next-fn on-error-fn nil))
  ([^Observable o on-next-fn on-error-fn on-completed-fn]
    (AtomicObservableSubscription. (.subscribe o on-next-fn on-error-fn on-completed-fn))))

; I don't think this is necessary really.
(defmacro subscribe
  [o bindings & body]
  `(subscribe* ~o (fn ~bindings ~@body)))

(defn unsubscribe
  "Unsubscribe from Subscription s and return it."
  [^Subscription s]
  (.unsubscribe s)
  s)

(defn ^Subscription subscription
  "Create a new subscription that calls the given no-arg handler function"
  [handler]
  (Observable/createSubscription handler))

(defn chain
  "Like subscribe*, but any omitted handlers pass-through to the observable next."
  ([from to]
    (chain from to #(emit to %)))
  ([from to on-next-fn]
    (chain from to on-next-fn #(error to %)))
  ([from to on-next-fn on-error-fn]
    (chain from to on-next-fn on-error-fn #(done to)))
  ([from to on-next-fn on-error-fn on-completed-fn]
    (subscribe* from on-next-fn on-error-fn on-completed-fn)))

;################################################################################

(defn seq
  "Make an observable out of some seq-able thing. The rx equivalent of clojure.core/seq."
  [xs]
  (Observable/from xs))

(defn ^Observable never [] (Observable/never))
(defn ^Observable empty [] (Observable/empty))
; TODO call this return like in .net, or only?
(defn ^Observable return [value] (Observable/just value))

(defn cons
  "cons x to the beginning of xs"
  [x xs]
  (observable (fn [target]
                (emit target x)
                (chain xs target))))

(defn ^Observable concat
  [& xs]
  (Observable/concat (into-array Observable xs)))

; TODO concat* that takes an observable sequence of observables and concatentates them.
; I think this may be more useful than the built-in concat

(defn ^Observable drop
  [n ^Observable xs]
  (.skip xs n))

(defn ^Observable drop-while
  [p xs]
  (let [p (predicate p)]
    (observable (fn [target]
                  (let [dropping (atom true)]
                    (chain xs
                           target
                           (fn [v]
                             (when (or (not @dropping)
                                       (not (reset! dropping (p v))))
                               (emit target v)))))))))

(defn ^Observable filter
  [p ^Observable xs]
  (.filter xs (predicate p)))

(def first #(take 1 %))

(defn future*
  "Execute (f & args) in a separate thread and pass the result to onNext.
  If an exception is thrown, onError is called with the exception.

  Returns an Observable.
  "
  [f & args]
  (observable (fn [observer]
                (let [wrapped (-> #(emit % (apply f args))
                                wrap-on-completed
                                wrap-on-error)
                      fu      (clojure.core/future (wrapped observer))]
                  (subscription #(future-cancel fu))))))

(defmacro future
  "Executes body in a separate thread and passes the single result to onNext.
  If an exception occurs, onError is called.

  Returns an Observable

  Examples:

    (subscribe (rx/future (slurp \"input.txt\"))
               (fn [v] (println \"Got: \" v)))
    ; eventually outputs content of input.txt
  "
  [& body]
  `(future* (fn [] ~@body)))

(defn interpose
  [sep xs]
  (observable (fn [target]
                (let [first? (atom true)]
                  (chain xs
                         target
                         (fn [v]
                           (if @first?
                             (reset! first? false)
                             (emit target sep))
                           (emit target v)))))))

(defn into
  [to ^Observable from-observable]
  (->> from-observable
   .toList
   (map (partial clojure.core/into to))))

(defn keep
  [f xs]
  (filter (complement nil?) (map xs f)))

(defn keep
  [f xs]
  (filter (complement nil?) (map f xs)))

(defn keep-indexed
  [f xs]
  (filter (complement nil?) (map-indexed f xs)))

(defn ^Observable last
  [^Observable o]
  (.last o))

(defn ^Observable map
  "Map a function over an observable sequence. Unlike clojure.core/map, only supports up
  to 4 simultaneous source sequences at the moment."
  ([f ^Observable xs] (.map xs f))
  ([f xs & observables] (apply zip f xs observables)))

(defn ^Observable mapcat
  "For each value x in xs, calls (f x), which must return an Observable. The resulting
  observables are concatentated together into one observable.
  "
  ([f ^Observable xs] (.mapMany xs f))
  ; TODO multi-arg version?
  )

(defn ^Observable map-indexed
  [f xs]
  (observable (fn [target]
                (let [n (atom -1)]
                  (chain xs
                         target
                         (fn [v] (emit target (f (swap! n inc) v))))))))

(defn merge
  "
  Emits a map that consists of the rest of the maps conj-ed onto
  the first.  If a key occurs in more than one map, the mapping from
  the latter (left-to-right) will be the mapping in the result.

  See:
    clojure.core/merge
  "
  [maps]
  (reduce clojure.core/merge {} maps))

(def next (partial drop 1))

; TODO partition. Use Buffer whenever it's implemented.

(defn ^Observable reduce
  ([f ^Observable xs] (.reduce xs f))
  ([f val ^Observable xs] (.reduce xs val f)))

(defn ^Observable reductions
  ([f ^Observable xs] (.scan xs f))
  ([f val ^Observable xs] (.scan xs val f)))

(def rest next)

(def second (comp first rest))

(defn some
  "
  Emits the first logical true value of (pred x) for any x in xs,
  else completes immediately.

  See:
    clojure.core/some
  "
  [p ^Observable xs]
  (observable (fn [target]
                (chain xs
                       target
                       (fn [v]
                         (when-let [result (p v)]
                           (emit target result)
                           (done target)))))))

(defn split-with
  [p coll]
  (return [(take-while p coll) (drop-while p coll)]))

(defn ^Observable take
  [n ^Observable xs]
  (.take xs n))

(defn take-while
  [p xs]
  (observable (fn [target]
                (chain xs
                       target
                       (fn [v]
                         (if (p v)
                           (emit target v)
                           (done target)))))))

;################################################################################;
; Operators that don't correspond directly to any Clojure seq fn

(defn ^Observable weave
  "Observable.merge, renamed because merge means something else in Clojure"
  [os]
  (cond
    (instance? java.util.List os)
      (Observable/merge ^java.util.List os)
    (instance? Observable os)
      (Observable/merge ^Observable os)
    :else
      (throw (IllegalArgumentException. (str "Don't know how to weave " (type os))))))

(defn ^Observable weave-delay-error
  "Observable.mergeDelayError, renamed because merge means something else in Clojure"
  [os]
  (cond
    (instance? java.util.List os)
      (Observable/mergeDelayError ^java.util.List os)
    (instance? Observable os)
      (Observable/mergeDelayError ^Observable os)
    :else
      (throw (IllegalArgumentException. (str "Don't know how to weave " (type os))))))

(defn ^Observable zip
  "Observable.zip. You want map."
  ([f ^Observable a ^Observable b] (Observable/zip a b f))
  ([f ^Observable a ^Observable b ^Observable c] (Observable/zip a b c f))
  ([f ^Observable a ^Observable b ^Observable c ^Observable d] (Observable/zip a b c d f)))

(defmacro zip-let
  [bindings & body]
  (let [pairs  (clojure.core/partition 2 bindings)
        names  (clojure.core/mapv clojure.core/first pairs)
        values (clojure.core/map second pairs)]
    `(zip (fn ~names ~@body) ~@values)))

;################################################################################;

(defn generator*
  "Creates an observable that call (f observable & args) which should emit a sequence.

  Automatically calls on-completed on return, or on-error if any exception is thrown.

  Subscribers will block.

  Examples:

    ; An observable that emit just 99
    (generator* emit 99)
  "
  [f & args]
  (Observable/create (-> (fn [observer]
                           (apply f observer args)
                           (Observable/noOpSubscription))
                       wrap-on-completed
                       wrap-on-error)))

(defmacro generator
  "Create an observable that executes body which should emit a sequence.

  Automatically calls on-completed on return, or on-error if any exception is thrown.

  Subscribe will block.

  Examples:

    ; make an observer that emits [0 1 2 3 4]
    (generator [observer]
      (dotimes [i 5]
        (emit observer i)))

  "
  [bindings & body]
  `(generator* (fn ~bindings ~@body)))

(defn future-generator*
  "Same as generator* except f is invoked in a separate thread.

  subscribe will not block.
  "
  [f & args]
  (observable (fn [observer]
                (let [wrapped (-> (fn [o]
                                    (apply f o args))
                                wrap-on-completed
                                wrap-on-error)
                      fu      (clojure.core/future (wrapped observer))]
                  (subscription #(future-cancel fu))))))

(defmacro future-generator
  "Same as generator macro except body is invoked in a separate thread.

  subscribe will not block.
  "
  [bindings & body]
  `(future-generator* (fn ~bindings ~@body)))

;################################################################################;
; Helpers
; These are helpers that are occasionally useful for testing, but should not be
; used in production code since they introduce synchronous behavior.

(defn -first
  [observable]
  (let [result (clojure.core/promise)]
    (subscribe* (->> observable (take 1))
                #(clojure.core/deliver result [:value %])
                #(clojure.core/deliver result [:error %])
                #(clojure.core/deliver result nil))
    (if-let [[type v] @result]
      (case type
        :value v
        :error (throw v)))))

(defn -to
  [to from-observable]
  (-first (into to from-observable)))


;################################################################################;








