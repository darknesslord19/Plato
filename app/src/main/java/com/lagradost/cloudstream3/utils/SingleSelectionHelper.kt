package com.lagradost.cloudstream3.utils

import android.app.Activity

object SingleSelectionHelper {
    @JvmStatic
    fun showDialog(
        activity: Activity?,
        items: List<String>?,
        selectedIndex: Int,
        name: String?,
        showCancelButton: Boolean,
        cancelCallback: () -> Unit,
        callback: (Int) -> Unit,
    ) {
        if (items != null && selectedIndex in items.indices) {
            callback(selectedIndex)
        }
    }
}
