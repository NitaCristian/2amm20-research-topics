/*
 * This file is part of the DiSPaLe project (https://gitlab.com/phdhien/dispale)
 *
 * Copyright (c) 2022, Normandie Université, France
 *
 * Licensed under the MIT license.
 *
 * See LICENSE file in the project root for full license information.
 */
package fr.phdhien.dispale

import be.kuleuven.pmlib.data.Dataset

/* 
 * @author Arnold Hien
 */
package object tools {
  final case class MinSupport(minsup: Int) extends be.kuleuven.flexics.MinSupport
  object MinSupport {
    def apply(minfreq: Double)(implicit dataset: Dataset[Set[Int]]): MinSupport =
      MinSupport((minfreq * dataset.size).toInt)
  }

  object Closed extends be.kuleuven.flexics.Closed { override def toString = s"Closed" }
  final case class MinLength(minlen: Int) extends be.kuleuven.flexics.MinLength
  final case class MaxLength(maxlen: Int) extends be.kuleuven.flexics.MaxLength
}


