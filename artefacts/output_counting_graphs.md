# Counting Graphs with a Prescribed Degree Sequence (Scala Version)

Adopted from Atabey Kaygun's blog post: [Counting Graphs with a Prescribed Degree Sequence (Scala Version)](https://kaygun.github.io/clean/2026-04-12-counting-graphs-scala.html).

## Description of the Problem

Earlier I explained the mathematical theory behind counting isomorphism classes of graphs that have a prescribed degree sequence. In that post, I also implemented a Lisp version of the counting algorithm. Today, I am going to write a Scala version of the same code.

## Implementation

Scala does not have a framework/library to implement general memoization out-of-the-box, but you can hand-roll one using a memoized fixed-point combinator:

```scala
import scala.collection.mutable

def memoFix[A, B](f: (A => B) => A => B): A => B = {
  val cache = mutable.HashMap[A, B]()
  lazy val g: A => B = a => cache.getOrElseUpdate(a, f(g)(a))
  g
}

val graphCount: List[Int] => BigInt = memoFix { self => ds =>
  ds match {
    case Nil                  => BigInt(0)
    case List(1, 1)           => BigInt(1)
    case _ if ds.length < 3   => BigInt(0)
    case _ if ds.sum % 2 != 0 => BigInt(0)
    case first :: rest =>
      rest.indices.toList.combinations(first).map { is =>
        val idxSet = is.toSet
        self(rest.zipWithIndex
          .map((d, i) => if (idxSet(i)) d - 1 else d)
          .filter(_ > 0).sorted)
      }.sum
  }
}

println(graphCount(List(4, 4, 4, 4, 4)))
println(graphCount(List(1, 1, 2, 2, 2)))
println(graphCount(List(2, 2, 2, 2, 2)))
```


## Large Examples & Regular Graph Sequences

Now, for the large interesting examples:

### OEIS A001205: Number of 2-regular graphs on $n$ vertices ($n=3..7$)

```scala
val seq2 = (3 to 7).map(n => graphCount(List.fill(n)(2)))
println(seq2.toVector)
```


### OEIS A002829: Number of 3-regular graphs on $2n$ vertices ($n=2..5$)

```scala
val seq3 = (2 to 5).map(n => graphCount(List.fill(2 * n)(3)))
println(seq3.toVector)
```


### OEIS A005815: Number of 4-regular graphs on $n$ vertices ($n=5..8$)

```scala
val seq4 = (5 to 8).map(n => graphCount(List.fill(n)(4)))
println(seq4.toVector)
```


### OEIS A338978: Number of 5-regular graphs on $2n$ vertices ($n=3..5$)

```scala
val seq5 = (3 to 5).map(n => graphCount(List.fill(2 * n)(5)))
println(seq5.toVector)
```


## Results & Display Blocks

### Base Degree Sequences Output
```plaintext
1
7
12
```


### 2-Regular Graphs Sequence (A001205)
```plaintext
Vector(1, 3, 12, 70, 465)
```


### 3-Regular Graphs Sequence (A002829)
```plaintext
Vector(1, 70, 19355, 11180820)
```


### 4-Regular Graphs Sequence (A005815)
```plaintext
Vector(1, 15, 465, 19355)
```


### 5-Regular Graphs Sequence (A338978)
```plaintext
Vector(1, 3507, 66462606)
```
