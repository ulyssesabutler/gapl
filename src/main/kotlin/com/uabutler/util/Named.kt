package com.uabutler.util

import com.uabutler.util.StringGenerator.genToStringFromProperties

data class Named<T>(
    val name: String,
    val item: T,
) {
    override fun toString() = genToStringFromProperties(this, Named<T>::name, Named<T>::item)
}