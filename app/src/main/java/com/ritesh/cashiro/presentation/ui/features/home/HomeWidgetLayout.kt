package com.ritesh.cashiro.presentation.ui.features.home

import com.ritesh.cashiro.data.preferences.HomeWidget

/** Turns the saved order and hidden set into the list Home shows and the edit sheet edits. */
object HomeWidgetLayout {

    /**
     * - Cards in [saved] keep their saved order; names that no longer exist were dropped on read.
     * - A card missing from [saved] (new in this version, or never reordered) is slotted in after
     *   the card that comes before it in default order, so an upgrade adds it in a sensible place
     *   without moving anything else. With nothing saved, the result is the default order.
     * - Everything is visible unless it is in [hidden]. That includes cards new to the layout.
     */
    fun resolve(saved: List<HomeWidget>, hidden: Set<HomeWidget>): List<HomeWidgetUiModel> {
        val ordered = saved.distinct().toMutableList()
        HomeWidget.entries
            .filter { it !in ordered }
            .sortedBy { it.defaultOrder }
            .forEach { widget ->
                val before = ordered
                    .filter { it.defaultOrder < widget.defaultOrder }
                    .maxByOrNull { it.defaultOrder }
                ordered.add(if (before == null) 0 else ordered.indexOf(before) + 1, widget)
            }
        return ordered.map { HomeWidgetUiModel(it, it !in hidden) }
    }
}
