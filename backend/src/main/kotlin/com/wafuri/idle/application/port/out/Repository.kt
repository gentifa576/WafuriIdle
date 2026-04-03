package com.wafuri.idle.application.port.out

import com.wafuri.idle.application.exception.ResourceNotFoundException

interface Repository<T, ID> {
  val resourceName: String
    get() = "Resource"

  fun save(domain: T): T

  fun findById(id: ID): T?

  fun require(id: ID): T = findById(id) ?: throw ResourceNotFoundException("$resourceName $id was not found.")
}
