package com.vasilevyuv.common.api

import kotlinx.serialization.Serializable

@Serializable
data class ApiError(val code: String, val message: String)
