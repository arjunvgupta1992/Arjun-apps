package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CategorySpendAggregate
import com.example.data.model.ExpenseCategory
import com.example.ui.theme.CoralExpense
import com.example.ui.theme.EmeraldLight
import com.example.ui.viewmodel.DaySpend
import com.example.util.CurrencyFormatter

@Composable
fun SpendingBarChart(
    spendingList: List<DaySpend>,
    modifier: Modifier = Modifier,
    onDayClick: (DaySpend) -> Unit = {}
) {
    if (spendingList.isEmpty()) return

    val maxSpend = spendingList.maxOfOrNull { it.amount }?.coerceAtLeast(100.0) ?: 100.0
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val chartWidth = maxWidth
            val chartHeight = maxHeight
            val barCount = spendingList.size
            val barWidth = (chartWidth - 16.dp * 2) / (barCount * 1.8f)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
            ) {
                val availableHeight = size.height - 30.dp.toPx()
                val stepX = size.width / barCount

                // Subtle baseline
                drawLine(
                    color = Color.Gray.copy(alpha = 0.2f),
                    start = Offset(0f, availableHeight),
                    end = Offset(size.width, availableHeight),
                    strokeWidth = 1.dp.toPx()
                )

                spendingList.forEachIndexed { index, day ->
                    val barHeightFraction = (day.amount / maxSpend).toFloat().coerceIn(0.04f, 1f)
                    val barH = barHeightFraction * (availableHeight - 12.dp.toPx())
                    val x = index * stepX + (stepX - barWidth.toPx()) / 2f
                    val y = availableHeight - barH

                    val isSelected = selectedIndex == index
                    val barColor = when {
                        isSelected -> EmeraldLight
                        day.isToday -> CoralExpense
                        day.amount > 0 -> Color(0xFF00B686)
                        else -> Color.Gray.copy(alpha = 0.25f)
                    }

                    // Background track
                    drawRoundRect(
                        color = Color.Gray.copy(alpha = 0.08f),
                        topLeft = Offset(x, 10.dp.toPx()),
                        size = Size(barWidth.toPx(), availableHeight - 10.dp.toPx()),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )

                    // Active spending bar
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth.toPx(), barH),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                }
            }
        }

        // Labels row below canvas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            spendingList.forEachIndexed { index, day ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedIndex = index
                            onDayClick(day)
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = day.dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (day.isToday) CoralExpense else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal
                    )
                    if (day.amount > 0) {
                        Text(
                            text = CurrencyFormatter.formatCompactInr(day.amount),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryDonutChart(
    categoryBreakdown: List<CategorySpendAggregate>,
    totalExpense: Double,
    modifier: Modifier = Modifier
) {
    if (categoryBreakdown.isEmpty()) return

    val animatedProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800),
        label = "donut_progress"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Donut canvas
        Box(
            modifier = Modifier
                .size(130.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 18.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                val centerOffset = Offset(size.width / 2f, size.height / 2f)

                var startAngle = -90f

                if (totalExpense <= 0.0) {
                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.2f),
                        radius = radius,
                        center = centerOffset,
                        style = Stroke(strokeWidth)
                    )
                } else {
                    categoryBreakdown.take(6).forEach { cat ->
                        val sweep = ((cat.totalAmount / totalExpense) * 360f).toFloat() * animatedProgress
                        val color = Color(ExpenseCategory.fromString(cat.category).colorHex)

                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweep - 2f, // subtle spacing gap
                            useCenter = false,
                            topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                            size = Size(radius * 2, radius * 2),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                        startAngle += sweep
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatCompactInr(totalExpense),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Top categories legend
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categoryBreakdown.take(4).forEach { item ->
                val categoryEnum = ExpenseCategory.fromString(item.category)
                val percent = if (totalExpense > 0) (item.totalAmount / totalExpense * 100).toInt() else 0

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(categoryEnum.colorHex), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.category,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = CurrencyFormatter.formatCompactInr(item.totalAmount),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
