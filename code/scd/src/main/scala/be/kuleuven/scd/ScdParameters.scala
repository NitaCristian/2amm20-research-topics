package be.kuleuven.scd

//final case class ScdParameters(iterations: IterationCalculator, lambda: Double = 0.000001)
final case class ScdParameters(iterations: IterationCalculator, features_update: Boolean = false, lambda: Double = 0.000001)
