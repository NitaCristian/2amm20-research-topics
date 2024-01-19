/*
 * This file is part of letsip project (https://bitbucket.org/wxd/letsip/src/master/)
 *
 * Copyright (c) 2017, KU Leuven, Belgium
 */
package be.kuleuven.scd

/* 
 * @author Vladimir Dzyuba
 * @author Arnold Hien
 */

//final case class ScdParameters(iterations: IterationCalculator, lambda: Double = 0.000001)
final case class ScdParameters(iterations: IterationCalculator, features_update: Boolean = false, lambda: Double = 0.000001)
