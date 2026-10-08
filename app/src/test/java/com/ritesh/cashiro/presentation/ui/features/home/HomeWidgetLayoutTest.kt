package com.ritesh.cashiro.presentation.ui.features.home

import com.ritesh.cashiro.data.preferences.HomeWidget
import com.ritesh.cashiro.domain.service.MonthlySavings
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class HomeWidgetLayoutTest {

    private fun names(models: List<HomeWidgetUiModel>) = models.map { it.widget }

    @Test
    fun nothingSavedGivesTheDefaultOrderAllVisible() {
        val result = HomeWidgetLayout.resolve(emptyList(), emptySet())

        assertEquals(HomeWidget.entries.sortedBy { it.defaultOrder }, names(result))
        assertTrue(result.all { it.isVisible })
    }

    @Test
    fun aSavedOrderIsKeptAndTheNewCardsAreSlottedInNextToTheirNeighbours() {
        // An order saved before Net Worth became movable and before the new cards existed
        val saved = listOf(
            HomeWidget.LOANS, HomeWidget.ACCOUNT_CAROUSEL, HomeWidget.UPCOMING_SUBSCRIPTIONS,
            HomeWidget.RECENT_TRANSACTIONS, HomeWidget.BUDGET_CAROUSEL, HomeWidget.TRANSACTION_HEATMAP
        )

        val result = names(HomeWidgetLayout.resolve(saved, emptySet()))

        assertEquals(
            listOf(
                HomeWidget.NETWORTH_SUMMARY, HomeWidget.MONTHLY_SUMMARY, HomeWidget.LOANS,
                HomeWidget.ACCOUNT_CAROUSEL, HomeWidget.UPCOMING_SUBSCRIPTIONS, HomeWidget.RECURRING,
                HomeWidget.RECENT_TRANSACTIONS, HomeWidget.BUDGET_CAROUSEL, HomeWidget.TRANSACTION_HEATMAP
            ),
            result
        )
    }

    @Test
    fun aUserOrderIsNotDisturbedByNewCards() {
        val saved = listOf(
            HomeWidget.BUDGET_CAROUSEL, HomeWidget.NETWORTH_SUMMARY, HomeWidget.RECENT_TRANSACTIONS
        )

        val result = names(HomeWidgetLayout.resolve(saved, emptySet()))

        // The three the user placed keep their relative order
        assertEquals(
            saved,
            result.filter { it in saved }
        )
    }

    @Test
    fun hiddenCardsStayHiddenAndNewOnesAreVisible() {
        val saved = listOf(HomeWidget.LOANS, HomeWidget.RECENT_TRANSACTIONS)
        val hidden = setOf(HomeWidget.LOANS)

        val result = HomeWidgetLayout.resolve(saved, hidden).associate { it.widget to it.isVisible }

        assertEquals(false, result[HomeWidget.LOANS])
        assertEquals(true, result[HomeWidget.RECENT_TRANSACTIONS])
        assertEquals(true, result[HomeWidget.MONTHLY_SUMMARY])
        assertEquals(true, result[HomeWidget.RECURRING])
    }

    @Test
    fun netWorthCanBeHiddenLikeAnyOtherCard() {
        val result = HomeWidgetLayout.resolve(emptyList(), setOf(HomeWidget.NETWORTH_SUMMARY))
        assertEquals(false, result.first { it.widget == HomeWidget.NETWORTH_SUMMARY }.isVisible)
    }

    @Test
    fun aRepeatedNameInTheSavedOrderIsListedOnce() {
        val saved = listOf(HomeWidget.LOANS, HomeWidget.LOANS, HomeWidget.BUDGET_CAROUSEL)
        val result = names(HomeWidgetLayout.resolve(saved, emptySet()))
        assertEquals(1, result.count { it == HomeWidget.LOANS })
        assertEquals(HomeWidget.entries.size, result.size)
    }
}

class MonthlySavingsTest {

    @Test
    fun savingsAndRate() {
        val result = MonthlySavings.of(BigDecimal("50000"), BigDecimal("32000"))
        assertEquals(BigDecimal("18000"), result.savings)
        assertEquals(BigDecimal("36.0"), result.ratePercent)
    }

    @Test
    fun spendingMoreThanEarnedGivesNegativeSavings() {
        val result = MonthlySavings.of(BigDecimal("1000"), BigDecimal("1500"))
        assertEquals(BigDecimal("-500"), result.savings)
        assertEquals(BigDecimal("-50.0"), result.ratePercent)
    }

    @Test
    fun noIncomeGivesAZeroRateInsteadOfDividingByZero() {
        val result = MonthlySavings.of(BigDecimal.ZERO, BigDecimal("800"))
        assertEquals(BigDecimal("-800"), result.savings)
        assertEquals(0, result.ratePercent.compareTo(BigDecimal.ZERO))
    }
}
