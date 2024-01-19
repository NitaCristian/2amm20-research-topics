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

/* 
 * @author Arnold Hien
 */
package object feature {
    def compose(features: Features*) = CompositeFeatures(features)

    @inline def concat(features: Features*) = compose(features : _*)
}
