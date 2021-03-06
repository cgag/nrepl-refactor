(ns io.curtis.nrepl-refactor
  (:require [clojure.pprint :refer [pprint]]
            [clojure.walk :as walk]
            [clojure.tools.nrepl.transport :as t]
            [clojure.tools.nrepl.misc :refer [response-for]]
            [clojure.tools.nrepl.middleware :refer [set-descriptor!]]
            [clojure.tools.nrepl.server :refer [start-server 
                                                stop-server
                                                default-handler]]
            [clojure.tools.nrepl :as repl]))

(defn thread-last [form]
  (letfn [(thread-last-forms* [form] 
            (if-not (list? form) 
              (list form)
              (cons (butlast form) (thread-last-forms* (last form)))))]
    (pr-str 
      (apply list '->> (reverse (thread-last-forms* form))))))

(defn thread-first [form]
  (letfn [(thread-first-forms* [form]
            (if-not (seq? (second form))
              (list form)
              (cons (cons (first form) (drop 2 form)) 
                    (thread-first-forms* (second form)))))]
    (pr-str 
      (apply list '-> (reverse (thread-first-forms* form))))))


;; TODO: just pass in the symbol once, can't figure out how to get from ->>
;; to clojure.core/->> without literal syntax quote
(defn macroexpand-only [symbols form]
  (pr-str
    (walk/prewalk (fn [x] 
                    (if (and (seq? x) 
                             (contains? symbols (first x)))
                      (macroexpand x)
                      x))
                  form)))

;; TODO: goddamn it, if you unthread something with
;; a ' in it, the reader gives you (quote ...)
(defn unthread-last [form]
  (macroexpand-only #{'->> `->>} form))

(defn unthread-first [form]
  (macroexpand-only #{'-> `->} form))

(defn cycle-collection-type [coll]
  (let [class->pair {clojure.lang.PersistentList     [1 "{" "}"]
                     clojure.lang.PersistentArrayMap [1 "[" "]"]
                     clojure.lang.PersistentVector   [1 "#{" "}"]
                     clojure.lang.PersistentHashSet  [2 "(" ")"]}
        [to-drop open close] (class->pair (class coll))
        coll-str (pr-str coll)
        s (str open (subs coll-str 
                          to-drop 
                          (- (count coll-str) 1)) 
               close)]
    s))

;; TODO: is evaling this a bad idea?
(defn cycle-privacy [fn-form]
  (let [f (first fn-form)
        new-fn-form (condp = f
                      'defn (cons 'defn- (drop 1 fn-form))
                      'defn- (cons 'defn (drop 1 fn-form))
                      'def (if (:private (meta (eval fn-form)))
                             (pr-str fn-form)
                             (apply str 
                                    (concat "(def ^:private"
                                            (drop (count "(def") (pr-str fn-form)))))
                      )]
    new-fn-form))

(defn cycle-str-keyword [s-or-k]
  (pr-str
    (condp = (class s-or-k)
      clojure.lang.Keyword (subs (str s-or-k) 1)
      java.lang.String (keyword s-or-k))))

;; TODO: maybe op should just be refactor and another key should specify which?
(def refactor-fns {"identity"       identity
                   "thread-last"    thread-last
                   "thread-first"   thread-first
                   "unthread-last"  unthread-last
                   "unthread-first" unthread-first
                   "cycle-str-keyword" cycle-str-keyword
                   "cycle-privacy" cycle-privacy
                   "cycle-collection-type" cycle-collection-type})

(defn refactor [h]
  (fn [{:keys [op refactor code transport] :as msg}]
    (if-not (and (= op "nrepl.refactor")
                 code 
                 (contains? refactor-fns refactor))
      (h msg)
      (let [refactor-f (refactor-fns refactor)]
        (t/send transport (response-for msg 
                                        :status :done
                                        :value (-> code
                                                   read-string
                                                   refactor-f)))))))

(set-descriptor! #'refactor
                 {:expects #{}
                  :handles {}})

(comment
  "(def ^:private port 10000)"
  (def server (start-server :port port
                            :handler (default-handler #'refactor)))

  (comment (stop-server server))
  
  ;; TODO: real tests, generative tests that these are inverses of each other
  ;(unthread-last '(->> [1 2 3 4 5] (filter even?) (map square) (map f)))
  ;(unthread-first '(-> [1 2] b c d))
  ;(thread-first '(d (b (c a))))
  ;(thread-last '(map f (map square (filter even? [1 2 3 4 5]))))

  (with-open [conn (repl/connect :port port)]
    (-> (repl/client conn 1000)                                                 
        (repl/message {:op :eval                                
                       :code "(+ 1 ())"
                       ;:code "(map f (map square (filter even? [1 2 3 4 5])))"
                       }
                       )
        doall
        pprint
        ;repl/response-values
        )))

(comment (a (b (c [1 2 3]))))
(comment (* 1 2 3))
(comment (defn square [x] (* x x)))
(comment (defn double-even-squares [n]
           (->> (range n) 
                (filter even?) 
                (map square) 
                (map (partial * 2)))))
