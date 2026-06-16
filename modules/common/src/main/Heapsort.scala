package lidraughts.common

import scala.collection.generic.{ CanBuildFrom, Growable }
import scala.collection.mutable.PriorityQueue
import scala.math.Ordering

/*
 * Sorts elements in priority order: higher first, lower last.
 * It's the opposite of what scala .sort does.
 */
object Heapsort {

  private[this] def moveN[T](p: PriorityQueue[T], g: Growable[T], n: Int): Unit = {
    // Only the dequeue and dequeueAll methods will return elements in priority order (while removing elements from the heap).
    var k = p.length atMost n
    while (k > 0) {
      g += p.dequeue()
      k -= 1
    }
  }

  /* selects maximum nb elements from n size collection
   * should be used for small nb and large n
   * Complexity: O(n + nb * log(n))
   */
  def topN[T, Coll, To](xs: Coll, nb: Int, ord: Ordering[T])(implicit
    ev: Coll => TraversableOnce[T],
    cbf: CanBuildFrom[Coll, T, To]
  ): To = {
    val b = cbf(xs)
    val p = PriorityQueue.empty[T](ord)
    p ++= ev(xs)

    b.sizeHint(p.length atMost nb)
    moveN(p, b, nb)
    b.result()
  }

  def topNToList[T](xs: TraversableOnce[T], nb: Int, ord: Ordering[T]): List[T] = {
    val b = List.newBuilder[T]
    val p = PriorityQueue.empty[T](ord)
    p ++= xs

    moveN(p, b, nb)
    b.result()
  }

  object implicits {

    implicit final class ListOps[A](private val l: List[A]) extends AnyVal {

      def topN(nb: Int)(implicit ord: Ordering[A]): List[A] =
        Heapsort.topNToList(l, nb, ord)

      def botN(nb: Int)(implicit ord: Ordering[A]): List[A] =
        Heapsort.topNToList(l, nb, ord.reverse)
    }
  }
}
