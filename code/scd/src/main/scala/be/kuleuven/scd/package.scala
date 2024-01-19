/*
 * This file is part of letsip project (https://bitbucket.org/wxd/letsip/src/master/)
 *
 * Copyright (c) 2017, KU Leuven, Belgium
 */
package be.kuleuven

/* 
 * @author Vladimir Dzyuba
 */
package object scd {
    trait Vector {
        def length: Int
        def apply(j: Int): Double
        def hasValueAt(j: Int): Boolean = true

        final def view = (0 until length).view.map(this(_))
    }

    trait MutableVector extends Vector {
        def update(j: Int, v: Double): Unit
    }

    trait Example extends Vector {
        def label: Double
    }

    object Example {
        def unapply(ex: Example): Option[(Vector, Double)] = Some((ex, ex.label))
    }

    type Weights = MutableVector

    final class ArrayVector(val array: Array[Double], override val length: Int) extends MutableVector {
        def this(array: Array[Double]) = this(array, array.length)

        @inline override def apply(j: Int): Double = array(j)
        @inline override def update(j: Int, v: Double): Unit = { array(j) = v }
    }

    final class ArrayExample(
                                private val array: Array[Double],
                                override val length: Int,
                                override val label: Double
                            ) extends Example {
        //
        def this(array: Array[Double], label: Double) = this(array, array.length, label)
        @inline override def apply(j: Int): Double = array(j)
        //
    }

    object ArrayVector {
        def zeros(length: Int) = new ArrayVector(Array.ofDim[Double](length), length)
        def ones(length:Int) = {
            val arrayOnes = Array.ofDim[Double](length)
            val i=0
            for(i<-1 to length){
                arrayOnes(i-1) = 1
            }
            new ArrayVector(arrayOnes, length)
        }
    }

    def innerProduct(v1: Vector, v2: Vector, d: Option[Int] = None): Double = {
        val d0 = d.getOrElse(v1.length)

        var i = 0
        var s = 0.0
        while (i < d0) {
            s += v1(i) * v2(i)
            i += 1
        }
        s
    }

    def l1Norm(v: Vector, d: Option[Int] = None): Double = {
        val n0 = d.getOrElse(v.length)

        var i = 0
        var s = 0.0
        while (i < n0) {
            s += Math.abs(v(i))
            i += 1
        }
        s
    }
}
