package fr.phdhien.dispale.feature

import be.kuleuven.pmlib.data.Dataset
import be.kuleuven.pmlib.labels.binary._
import be.kuleuven.pmlib.labels.{BinaryLabel, _}

final class BinaryMeasureFeature(override val measure: BinaryMeasure,
                                 override val dataset: Dataset[Set[Int]] with BinaryLabel,
                                 override val measureState: BinaryDistribution,
                                 override val scalingConstant: Double)
  extends SupervisedMeasureFeature[BinaryLabel]

final class BinaryMeasureFeatureGenerator(override val measure: BinaryMeasure, val label: String)
  extends SupervisedMeasureFeatureGenerator[BinaryLabel] {

  override protected def labelled(dataset: Dataset[Set[Int]]): Dataset[Set[Int]] with BinaryLabel =
    dataset withBinaryLabel label

  def scalingConstant(datasetModel: BinaryDistribution): Double = {
    val allPositives = datasetModel.copy(size = datasetModel.positives)
    val allNegatives = datasetModel.copy(positives = 0, size = datasetModel.size - datasetModel.positives)
    Iterator(allPositives, allNegatives).map(em => measure.compareModels(em, datasetModel)).max
  }

  override protected def build(labelledDataset: Dataset[Set[Int]] with BinaryLabel,
                               state: BinaryDistribution): BinaryMeasureFeature = {
    val datasetModel = measure.computeState(labelledDataset)
    new BinaryMeasureFeature(measure, labelledDataset, datasetModel, scalingConstant(datasetModel))
  }
}

object BinaryMeasureFeature {
  def apply(measure: BinaryMeasure, label: String) = new BinaryMeasureFeatureGenerator(measure, label)

  def accuracy(label: String)    = apply(Accuracy,    label)
  def purity(label: String)      = apply(Purity,      label)
  def sensitivity(label: String) = apply(Sensitivity, label)
  def specificity(label: String) = apply(Specificity, label)
  def wracc(label: String)       = apply(WRAcc,       label)
}
