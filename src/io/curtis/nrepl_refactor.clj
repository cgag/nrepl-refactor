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

;; TODO: bug? Why is  (= (reverse forms) (reverse (reverse forms))) false?
(defn thread-last [form]
  (letfn [(thread-last-forms* [form] 
            (if-not (list? form) 
              (list form)
              (cons (butlast form) (thread-last-forms* (last form)))))]
    (apply list '->> (reverse (thread-last-forms* form)))))

(defn thread-first [form]
  (letfn [(thread-first-forms* [form]
            (if-not (seq? (second form))
              (list form)
              (cons (cons (first form) (drop 2 form)) 
                    (thread-first-forms* (second form)))))]
    (apply list '-> (reverse (thread-first-forms* form)))))


;; TODO: just pass in the symbol once, can't figure out how to get from ->>
;; to clojure.core/->> without literal syntax quote
(defn macroexpand-only [symbols form]
  (walk/prewalk (fn [x] 
                  (if (and (seq? x) 
                           (contains? symbols (first x)))
                      (macroexpand x)
                    x))
                form))

(defn unthread-last [form]
  (macroexpand-only #{'->> `->>} form))

(defn unthread-first [form]
  (macroexpand-only #{'-> `->} form))


(def refactor-fns {"refactor.identity"       identity
                   "refactor.thread-last"    thread-last
                   "refactor.thread-first"   thread-first
                   "refactor.unthread-last"  unthread-last
                   "refactor.unthread-first" unthread-first})

(defn refactor [h]
  (fn [{:keys [op code transport] :as msg}]
    (if-not (and code (contains? refactor-fns op))
      (h msg)
      (let [refactor-f (refactor-fns op)]
        (t/send transport (response-for msg 
                                        :status :done
                                        :value (-> code
                                                   read-string
                                                   refactor-f
                                                   str)))))))

(set-descriptor! #'refactor
                 {:expects #{}
                  :handles {}})

(comment
  (def port 10000)
  (def server (start-server :port port
                            :handler (default-handler #'refactor)))

  (comment (stop-server server))
  
  ;; TODO: real tests, generative tests that these are inverses of each other
  (unthread-last '(->> [1 2 3 4 5] (filter even?) (map square) (map f)))
  (unthread-first '(-> [1 2] b c d))
  (thread-first '(d (b (c a))))
  (thread-last '(map f (map square (filter even? [1 2 3 4 5]))))

  (with-open [conn (repl/connect :port port)]
    (-> (repl/client conn 1000)                                                 
        (repl/message {:op :refactor.thread-last                                
                       :code "(map f (map square (filter even? [1 2 3 4 5])))"})
        repl/response-values)))