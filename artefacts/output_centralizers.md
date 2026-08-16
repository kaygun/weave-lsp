# Number of Isomorphism Classes of Simple Graphs (Continued)

Adopted from Atabey Kaygun's blog post: [Centralizers & Permutation Cycles](https://kaygun.github.io/clean/2024-12-24-centralizers.html).

## Description of the Problem

Yesterday, I did a computation on the number of unlabeled simple graphs on $n$ vertices. The correct mathematical formulation is *the number of isomorphism classes of simple graphs on $n$ vertices.* The computation I gave yesterday has a horrendous time complexity (approximately $\mathcal{O}(n!)$). Today, I am going to improve on it.

## A Recap

The number I wanted to compute was:

$$\frac{1}{n!} \sum_{g\in S_n} 2^{\ell(g)}$$

where $\ell(g)$ was the number of disjoint orbits of the action of $g$ on the set of edges of the complete graph $K_n$. One observation I made was that $\ell(g)$ depends on the shape of $g$ in terms of cycle decomposition of $g$.

## Some Group Theory

There are many different ways of representing permutations on $n$-objects. Mathematicians view them as one-to-one and onto functions. In my count yesterday, I was writing a permutation as a specific ordering of the numbers from $1$ to $n$. Mathematicians usually write it as a matrix. Here is an example of a permutation of $5$:

$$\left(\begin{matrix} 1 & 2 & 3 & 4 & 5 \\ 2 & 3 & 1 & 5 & 4 \end{matrix}\right)$$

where we read them as "1 is mapped to 2, 2 is mapped to 3..." etc.

## Cycles

There is another way of representing permutations by objects called [cycles](https://mathworld.wolfram.com/PermutationCycle.html). A cycle is a sequence of numbers connected via repeated action of a permutation. For example in the example above, if we start with $1$ and use the permutation, we obtain $2$. If we apply the permutation again on $2$ we get $3$, and if we apply again we get $1$, completing the cycle $(123)$. If we take a remaining element, say $4$, and apply the permutation repeatedly we get the cycle $(45)$. Combining them together we get a cycle representation $(123)(45)$ of the same permutation. Every permutation has a representation in terms of disjoint cycles.

Note that the cycle $(123)$ can also be represented as $(231)$ or $(312)$ depending on where we start applying. The usual convention is to start with the smallest number in the cycle. Moreover, $(123)(45)$ and $(45)(123)$ also represent the same permutation. We usually order cycles using a suitable version of the lexicographical ordering.

So, here is a simple way of writing a unique representative permutation corresponding to a specific shape:

```lisp
(defun cycle (n &optional (shift 1))
  (append (loop for i from (1+ shift) below (+ n shift) collect i)
          (list shift)))

(defun permutation (xs &optional (shift 1) ret)
  (if (null xs)
      ret
      (permutation (cdr xs)
                   (+ shift (car xs))
                   (append ret (cycle (car xs) shift)))))

(format t "~a~%" (permutation '(3 3 2 1 1)))
```


Note that I am not using the cycle representation, but the original representation of a permutation as a specific ordering of numbers from $1$ to $n$.

## Conjugation

Given two permutations $\sigma$ and $\mu$, there is an operation called conjugation $\sigma\mu\sigma^{-1}$. Conjugating a permutation $\mu$ via another permutation $\sigma$ does not change its cycle decomposition but it relabels the numbers in $\mu$ using the order defined by $\sigma$. Thus one can see that we can conjugate any permutation into something like:

$$(1\cdots a_1)((a_1+1)\cdots a_2)\cdots((a_{m-1}+1)\cdots a_m)((a_m+1)\cdots n)$$

where numbers satisfy $1 < a_1 < \cdots < a_2 < \cdots < a_m < \cdots < n$ as in our example. Here are all of the permutations that have the same shape as our example:

$$(123)(45), (132)(45), (124)(35), (142)(35), (125)(34), (152)(34), (134)(25), (143)(25), (135)(24), (153)(24)$$
$$(145)(23), (154)(23), (234)(15), (243)(15), (235)(14), (253)(14), (245)(13), (254)(13), (345)(12), (354)(12)$$

The shape is dictated by a partition of $5$. In this case $5=3+2$. Now, we have two problems:
1. Given an $n$, how do we get all partitions of $n$ to get an index set for permutations?
2. How do we count the number of all permutations that fit into a specific shape defined by a partition?

## All Partitions of an Integer

Luckily, I wrote about partitions before. The function below returns all possible partitions of an integer unbound by its size:

```lisp
(defun partitions (n &optional k)
  (cond
    ((null k) (loop for i from 1 to n append (partitions n i)))
    ((< n k) nil)
    ((= k 1) (list (list n)))
    ((= n k) (list (loop repeat n collect 1)))
    (t (append (mapcar (lambda (xs) (append xs (list 1))) (partitions (1- n) (1- k)))
               (mapcar (lambda (xs) (mapcar #'1+ xs)) (partitions (- n k) k))))))

(format t "~a~%" (partitions 5))
```


The partitions above correspond to the following permutations:

$$(12345), (1234)(5), (123)(45), (123)(4)(5), (12)(34)(5), (12)(3)(4)(5), (1)(2)(3)(4)(5)$$

## Results & Display Blocks

### Permutation Cycles Output
```plaintext
(2 3 1 5 6 4 8 7 9 10)
```


### Integer Partitions Output
```plaintext
((5) (4 1) (3 2) (3 1 1) (2 2 1) (2 1 1 1) (1 1 1 1 1))
```
