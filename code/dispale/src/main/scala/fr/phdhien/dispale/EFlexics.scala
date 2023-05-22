package fr.phdhien.dispale

import be.kuleuven.pmlib.itemsets.Itemset
import be.kuleuven.weightgen.{WeightFunction, WeightGen, WeightGenWrapper, WeightMC}
import be.kuleuven.weightgen.leapfrogging.LeapfroggingWeightGen
import be.kuleuven.weightgen.logging.BasicSlf4jWeightGenLogger
import be.kuleuven.wg4ps.specialised.Eclat.EclatConfiguration
import be.kuleuven.wg4ps.specialised.{EclatBoundedOracle, EclatProblem, EclatWeightMC}
//import be.kuleuven.flexics.eflexics.Eclat.EclatConfiguration
//import be.kuleuven.flexics.eflexics.{EclatBoundedOracle, EclatProblem, EclatWeightMC}

import scala.util.Random

trait EFlexics extends LeapfroggingWeightGen[Itemset, EclatProblem] {
  override protected final val logger = new BasicSlf4jWeightGenLogger(s"EFlexics")

  protected final val oracle = EclatBoundedOracle(EclatConfiguration())
  override protected final def defaultSolver(problem: EclatProblem) = oracle

  @inline
  override protected final def initialExtraXors(p: EclatProblem,
                                                w: WeightFunction[Itemset],
                                                lt: Double,
                                                ht: Double,
                                                xors0: Int) =
    1
}

final case class EFlexicsWrapper(override val sampler: EFlexics) extends WeightGenWrapper[Itemset, EclatProblem] {
  override protected val counter = EclatWeightMC
  override def toString = sampler.toString
}

object VanillaEFlexics extends EFlexics {
  override def toString: String = "EFlexics(Vanilla)"
}

object TakeBestEFlexics extends EFlexics {
  override protected def sampleFromCell[C <: Traversable[Itemset]](cell: C,
                                                                   cellWeight: Double,
                                                                   weight: (Itemset) => Double)
                                                                  (rnd: Random): Iterator[Itemset] =
    Iterator.single(cell.maxBy(weight))


  override def toString: String = "EFlexics(TakeBest)"
}

final case class ResampleEFlexics(k: Int) extends EFlexics {
  require(k >= 2)

  override protected def sampleFromCell[C <: Traversable[Itemset]](cell: C,
                                                                   cellWeight: Double,
                                                                   weight: (Itemset) => Double)
                                                                  (rnd: Random): Iterator[Itemset] =
    Iterator.continually(super.sampleFromCell(cell, cellWeight, weight)(rnd)).flatten.take(k)


  override def toString: String = s"EFlexics(Resample$k)"
}

final case class TakeTopEFlexics(k: Int) extends EFlexics {
  require(k >= 2)

  override protected def sampleFromCell[C <: Traversable[Itemset]](cell: C,
                                                                   cellWeight: Double,
                                                                   weight: (Itemset) => Double)
                                                                  (rnd: Random): Iterator[Itemset] =
    cell.toSeq.sortBy(-weight(_)).take(k).iterator


  override def toString: String = s"EFlexics(TakeTop$k)"
}

final case class SampleTopEFlexics(n: Int) extends EFlexics {
  require(n >= 2)

  override protected def sampleFromCell[C <: Traversable[Itemset]](cell: C,
                                                                   cellWeight: Double,
                                                                   weight: (Itemset) => Double)
                                                                  (rnd: Random): Iterator[Itemset] = {
    val topItemsets = cell.toSeq.sortBy(-weight(_)).take(n)
    val topItemsetsWeight = topItemsets.view.map(weight).sum
    super.sampleFromCell(topItemsets, topItemsetsWeight, weight)(rnd)
  }


  override def toString: String = s"EFlexics(SampleTop$n)"
}

final case class ResampleTopEFlexics(k: Int, n: Int) extends EFlexics {
  require(k >= 2 && n >= 2)

  override protected def sampleFromCell[C <: Traversable[Itemset]](cell: C,
                                                                   cellWeight: Double,
                                                                   weight: (Itemset) => Double)
                                                                  (rnd: Random): Iterator[Itemset] = {
    val topItemsets = cell.toSeq.sortBy(-weight(_)).take(n)
    val topItemsetsWeight = topItemsets.view.map(weight).sum
    Iterator.continually(super.sampleFromCell(topItemsets, topItemsetsWeight, weight)(rnd)).flatten.take(k)
  }


  override def toString: String = s"EFlexics(ResampleTop$k:$n)"
}
