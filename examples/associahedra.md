# Integer Coordinates for Associahedra

Adopted from Atabey Kaygun's blog post: [Integer Coordinates for Associahedra](https://kaygun.github.io/clean/2025-04-22-associahedra.html).

## Description of the Problem

The number of different parenthesizations of $n+1$ terms is given by the $n$-th [Catalan number](https://en.wikipedia.org/wiki/Catalan_number). There is also a convex polytope $K_n$ called [Associahedron](https://en.wikipedia.org/wiki/Associahedron) whose extremal points are decorated by distinct parenthesizations of $n+1$-terms. Associahedra were originally defined by [Jim Stasheff](https://en.wikipedia.org/wiki/Jim_Stasheff) as CW-complexes to parametrize $n+1$-fold compositions in the fundamental group of a space, but they were later [realized as convex polytopes](https://arxiv.org/abs/math/0212126) embedded in an affine space with integer coordinates by [Jean-Louis Loday](https://en.wikipedia.org/wiki/Jean-Louis_Loday). Today, I am going to describe the way Loday did this using Scala code.

## Implementation

Let us start with implementing binary trees:

```name:scala_associahedra lang:scala code:visible output:hidden
sealed trait Expr
case class Leaf(label: Int) extends Expr
case class Node(left: Expr, right: Expr) extends Expr

def generateParenthesizations(n: Int): List[Expr] = {
  if (n == 1) return List(Leaf(0))  // dummy leaf
  (1 until n).flatMap { i =>
    val lefts = generateParenthesizations(i)
    val rights = generateParenthesizations(n - i)
    for (l <- lefts; r <- rights) yield Node(l, r)
  }.toList
}

generateParenthesizations(4).foreach(println)
```

Next, we generate all full binary trees as fully parenthesized expressions and print each tree for $n=4$ terms.

## Results & Display Blocks

### Associahedra Parenthesizations Output
```render:scala_associahedra
```
