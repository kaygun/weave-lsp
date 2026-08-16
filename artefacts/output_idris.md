# My First Idris Proof

Adopted from Atabey Kaygun's blog post: [My First Idris Proof](https://kaygun.github.io/clean/2017-03-14-my_first_idris_proof.html).

## Tools vs Conveniences

Most of what I do are affected by the tools I use. In writing math my two options are paper and [LaTeX](https://www.latex-project.org/). I am a total slob when it comes to paper: loose paper are all over the place and my notebooks are like a homeless graphomaniac’s ramblings. That leaves typing in LaTeX, and when it comes to editors I am an editor-agnostic: I’ll do [emacs](https://www.gnu.org/software/emacs/), [vi](https://en.wikipedia.org/wiki/Vi), [nano](https://www.nano-editor.org/), [gedit](https://wiki.gnome.org/Apps/Gedit) or even ed. If you observe me long enough, you can even see me doing straight `cat` into a file.

When it comes to programming, I am IDE-averse. Apart from emacs, the last IDE I used was probably [Turbo C](https://en.wikipedia.org/wiki/Borland_Turbo_C) back in 90’s. Because, if my IDE’s raison d'etre is to do the boring stuff for me, maybe I am using a wrong language to start with. You don’t want me to pull a [Kant](http://www.columbia.edu/acis/ets/CCREAD/etscc/kant.html) on you:

> *If I have a book that thinks for me, a pastor who acts as my conscience, a physician who prescribes my diet, and so on–then I have no need to exert myself.*

True tools are languages. Editors and frameworks are conveniences.

## Diversity, Objectives and Constraints

[Georges Perec](https://en.wikipedia.org/wiki/Georges_Perec) once wrote [a whole novel](https://en.wikipedia.org/wiki/A_Void) on a typewriter whose e-key was removed which was in part inspired by [another novel](https://en.wikipedia.org/wiki/Gadsby_(novel)) written by an obscure American writer [Ernest Wright](https://en.wikipedia.org/wiki/Ernest_Vincent_Wright). In the same vein, if I’d like to write programs that severely restrict my use of side effects then I’d use Haskell. If I’d want to do this on the JVM platform then I’d switch to [Frege](https://en.wikipedia.org/wiki/Frege_%28programming_language%29), probably. Different objectives and constraints require different tools, and in turn, these constraints might bring out novel ideas you wouldn’t normally think of.

In short, learn as much as you can and as diversely as you can. Make choices and write, or `cat` if appropriate.

## Idris Programming Language and Proof Assistants

[Idris](http://www.idris-lang.org/) has been on my todo list for a long time. A strongly and dependently typed functional programming language similar to [Haskell](https://www.haskell.org/). I am mainly interested in Idris’ interactive theorem proving capabilities.

I know that [Coq](https://coq.inria.fr/) is the de facto standard and it does a more comprehensive job than Idris, but I like its Haskell-like syntax. If you are interested, check [Homotopy Type Theory](https://golem.ph.utexas.edu/category/2015/06/whats_so_hott_about_formalizat.html), a machine assisted proof of [Feit-Thompson Odd Order Theorem in Coq](https://hal.inria.fr/hal-00816699/file/main.pdf), and a Coq proof of the [Four Color Theorem](https://www.microsoft.com/en-us/download/details.aspx?id=52574).

There are many old, and new and experimental formal proof assistants out there. Here are a few in case you’d want to check out:
- [Agda](http://wiki.portal.chalmers.se/agda/pmwiki.php)
- [Isabelle](http://isabelle.in.tum.de/)
- [ACL2](http://www.cs.utexas.edu/users/moore/acl2/)
- [Lean](https://leanprover.github.io/)

Every journey starts with a few small steps. So, here are mine: I am going to verify that addition in the set of natural numbers is commutative. This proof is different than the commutativity proof given in the official documentation of Idris.

The set of natural numbers is given as a recursive type which has a bottom element `Z` and is recursively defined as:

```
data Nat = Z | S Nat
```


Unlike Haskell, type definitions are preceded by `:` instead of `::`.

My first lemma is the **unitality** of addition: $\text{plus } a \text{ Z} = a$. The proof is a simple recursion/induction argument. I think of `Refl` as the bottom type in proofs which is $x=x$ reflexivity: if you can reduce your proof to that, you are done.

The next lemma is **unloop**, about the fact that $(n+1)+m = n+(m+1)$, or $\text{plus } a (\text{S } b) = \text{S } (\text{plus } a\ b)$.

Then the rest is recursion on the second argument to prove **commutativity**: $\text{plus } a\ b = \text{plus } b\ a$.

```idris
module Main

-- Unitality Lemma: plus a Z = a
unit : (a : Nat) -> plus a Z = a
unit Z = Refl
unit (S k) = rewrite unit k in Refl

-- Unloop Lemma: plus a (S b) = S (plus a b)
unloop : (a : Nat) -> (b : Nat) -> plus a (S b) = S (plus a b)
unloop Z b = Refl
unloop (S k) b = rewrite unloop k b in Refl

-- Commutativity Theorem: plus a b = plus b a
comm : (a : Nat) -> (b : Nat) -> plus a b = plus b a
comm Z b = rewrite unit b in Refl
comm (S k) b = rewrite comm k b in rewrite unloop b k in Refl

main : IO ()
main = putStrLn "Proof verified: comm (a: Nat) (b: Nat) -> plus a b = plus b a"
```


## Proof Execution Result

### Verified Commutativity Result
```plaintext
Proof verified: comm (a: Nat) (b: Nat) -> plus a b = plus b a
```
