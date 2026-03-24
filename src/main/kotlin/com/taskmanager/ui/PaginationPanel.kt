package com.taskmanager.ui

import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JPanel

class PaginationPanel(
    private val onPageChanged: (page: Int, pageSize: Int) -> Unit
) : JPanel(FlowLayout(FlowLayout.CENTER, 8, 4)) {

    var currentPage: Int = 0
        private set
    var totalPages: Int = 1
        private set
    var pageSize: Int = 10
        private set

    private val prevButton = JButton("\u25C0").apply {
        toolTipText = "Previous page"
        addActionListener { goToPreviousPage() }
    }
    private val nextButton = JButton("\u25B6").apply {
        toolTipText = "Next page"
        addActionListener { goToNextPage() }
    }
    private val pageLabel = JBLabel("1 / 1")

    private val pageSizeCombo = ComboBox(DefaultComboBoxModel(arrayOf(5, 10, 15, 25))).apply {
        selectedItem = 10
        addActionListener {
            pageSize = selectedItem as Int
            currentPage = 0
            onPageChanged(currentPage, pageSize)
        }
    }

    init {
        border = JBUI.Borders.emptyTop(4)
        add(prevButton)
        add(pageLabel)
        add(nextButton)
        add(JBLabel("  Per page:"))
        add(pageSizeCombo)
        updateButtons()
    }

    fun update(totalItems: Int) {
        totalPages = maxOf(1, (totalItems + pageSize - 1) / pageSize)
        if (currentPage >= totalPages) currentPage = totalPages - 1
        updateButtons()
    }

    private fun goToPreviousPage() {
        if (currentPage > 0) {
            currentPage--
            onPageChanged(currentPage, pageSize)
            updateButtons()
        }
    }

    private fun goToNextPage() {
        if (currentPage < totalPages - 1) {
            currentPage++
            onPageChanged(currentPage, pageSize)
            updateButtons()
        }
    }

    private fun updateButtons() {
        prevButton.isEnabled = currentPage > 0
        nextButton.isEnabled = currentPage < totalPages - 1
        pageLabel.text = "${currentPage + 1} / $totalPages"
    }
}
