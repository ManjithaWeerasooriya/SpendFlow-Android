package com.example.spendflow.data.remote.mapper

import com.example.spendflow.data.remote.FirestoreFields
import com.example.spendflow.domain.model.Category
import com.example.spendflow.domain.model.CategoryType

private const val NAME = "name"
private const val TYPE = "type"
private const val ICON_NAME = "iconName"
private const val COLOR_ARGB = "colorArgb"

fun Category.toFirestore(): Map<String, Any?> = mapOf(
    NAME to name,
    TYPE to type.name,
    ICON_NAME to iconName,
    COLOR_ARGB to colorArgb?.toLong(),
    FirestoreFields.IS_ARCHIVED to isArchived,
)

fun categoryFromFirestore(id: String, data: Map<String, Any?>): Category = FirestoreData(id, data).build {
    Category(
        id = id,
        name = string(NAME),
        type = enum<CategoryType>(TYPE),
        iconName = optString(ICON_NAME),
        colorArgb = optLong(COLOR_ARGB)?.toInt(),
        isArchived = boolean(FirestoreFields.IS_ARCHIVED, default = false),
    )
}
