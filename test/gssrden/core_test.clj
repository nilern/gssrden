(ns gssrden.core-test
  (:require [clojure.test :refer :all]
            [gssrden.core :refer [constraints]]))

;;; * TODO: center-in and fill tests

(deftest garden-keys
  (testing "keys can be"
    (testing ":keywords"
      (is (= (constraints
               (== :width (- (:window :width) :$emptiness)))
             {:width "== (::window[width] - [$emptiness])"})))
    (testing "\"strings\""
      (is (= (constraints
               (== "width" (- ("window" "width") "$emptiness")))
               {"width" "== (::window[width] - [$emptiness])"})))))
    ;; symbols won't work, but using them is stupid anyway...
    ;(testing "symbols"
    ;  (is (= (constraints
    ;           (== width (- (window width) $emptiness)))
    ;         {'width "== (::window[width] - [$emptiness])"})))))

(deftest inequalities
  (testing "inequality operators"
    (is (= (constraints
             (== :width (:body :width)))
           {:width "== body[width]"}))))

(deftest indexing
  (testing "get element property"
    (is (= (constraints
             (== :width (:body :width)))
           {:width "== body[width]"}))))

(deftest custom-vars
  (testing "custom constraint variable"
    (is (= (constraints
             (<= :line-height :$base-line-height))
           {:line-height "<= [$base-line-height]"})))
  (testing "custom element constraint variable"
    (is (= (constraints
             (>= :width (:body :$col-width)))
           {:width ">= body[$col-width]"}))))

(deftest special-pseudos
  (testing "special pseudo selectors"
    (testing ":window"
      (is (= (constraints
               (<= :line-height (/ (:window :height) 12)))
             {:line-height "<= ::window[height] / 12"})))
    (testing ":this"
      (is (= (constraints
               (== :height (:this :intrinsic-height)))
             {:height "== ::this[intrinsic-height]"})))
    (testing ":parent"
      (is (= (constraints
               (== :width (:parent :width)))
             {:width "== ::parent[width]"})))))

(deftest intrinsic-
  (testing "intrinsic properties"
    (is (= (constraints
             (== :height (:this :intrinsic-height)))
           {:height "== ::this[intrinsic-height]"}))))

(deftest strengths&weights
  (testing "strengths and weights"
    (testing "long keys"
      (testing "strength"
        (is (= (constraints
                 (>= :width 200 :strength :strong))
               {:width ">= 200 !strong"})))
      (testing "strength and weight"
        (is (= (constraints
                 (>= :width 200 :strength :strong :weight 300))
               {:width ">= 200 !strong300"}))))
    (testing "short keys"
      (testing "strength"
        (is (= (constraints
                 (>= :width 200 :s :strong))
               {:width ">= 200 !strong"})))
      (testing "strength and weight"
        (is (= (constraints
                 (>= :width 200 :s :strong :w 300))
               {:width ">= 200 !strong300"}))))))

(deftest arithmetic
  (testing "linear arithmetic"
    (testing "addition"
      (is (= (constraints
               (== :width (+ (:this :intrinsic-width) 20)))
             {:width "== (::this[intrinsic-width] + 20)"})))
    (testing "substraction"
      (is (= (constraints
               (== :width (- (:this :intrinsic-width) 20)))
             {:width "== (::this[intrinsic-width] - 20)"})))
    (testing "multiplication"
      (is (= (constraints
               (== :height (* (:#divvy :height) 3)))
             {:height "== #divvy[height] * 3"})))
    (testing "division"
      (is (= (constraints
               (== :height (/ (:#divvy :height) 3)))
             {:height "== #divvy[height] / 3"})))
    (testing "combined operations"
      (is (= (constraints
               (== :width (/ (* (- (:window :width) (:aside :width))
                                (+ :$magnification :$compensation))
                             2)))
             {:width (str "== (::window[width] - aside[width]) "
                          "* ([$magnification] + [$compensation]) "
                          "/ 2")})))))