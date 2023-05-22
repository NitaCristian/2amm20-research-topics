package fr.phdhien.dispale

package object feature {
  def compose(features: Features*) = CompositeFeatures(features)

  @inline def concat(features: Features*) = compose(features : _*)
}
