package com.laplog.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.laplog.app.R
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.viewmodel.ChartsViewModel
import com.laplog.app.viewmodel.ChartsViewModelFactory
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.Fill
import android.graphics.DashPathEffect
import java.text.SimpleDateFormat
import java.util.*

// Dashed line class for average/median lines
class DashedLine(fill: LineCartesianLayer.LineFill) : LineCartesianLayer.Line(fill) {
    init {
        linePaint.pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    sessionDao: SessionDao,
    preferencesManager: PreferencesManager
) {
    val viewModel: ChartsViewModel = viewModel(
        factory = ChartsViewModelFactory(sessionDao, preferencesManager)
    )

    val availableNames by viewModel.availableNames.collectAsState()
    val selectedName by viewModel.selectedName.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val chartData by viewModel.chartData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentLanguage = preferencesManager.appLanguage

    // Get all sessions for name localization
    val allSessions by sessionDao.getAllSessions().collectAsState(initial = emptyList())

    // Map original names to localized names
    val nameMapping = remember(availableNames, allSessions, currentLanguage) {
        availableNames.associateWith { originalName ->
            allSessions.find { it.name == originalName }
                ?.getLocalizedName(currentLanguage)
                ?: originalName
        }
    }

    // Get localized name for selected name
    val localizedSelectedName = remember(selectedName, nameMapping) {
        selectedName?.let { nameMapping[it] }
    }

    var showNameSelector by remember { mutableStateOf(false) }
    var showPeriodSelector by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.charts)) },
                actions = {
                    // Period selector dropdown
                    TextButton(
                        onClick = { showPeriodSelector = true },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = when (selectedPeriod) {
                                com.laplog.app.model.TimePeriod.ALL_TIME -> stringResource(R.string.period_all_time)
                                com.laplog.app.model.TimePeriod.LAST_7_DAYS -> stringResource(R.string.period_last_7_days)
                                com.laplog.app.model.TimePeriod.LAST_30_DAYS -> stringResource(R.string.period_last_30_days)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null
                        )
                    }

                    // Name selector dropdown
                    TextButton(
                        onClick = { showNameSelector = true },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = localizedSelectedName ?: stringResource(R.string.all_names),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (chartData?.statistics?.isEmpty() != false) {
                item {
                    Text(
                        text = stringResource(R.string.no_data),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                // Summary statistics card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.summary_statistics),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            HorizontalDivider()

                            // Total sessions (always shown)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.total_sessions),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = chartData?.statistics?.size.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Average duration (always shown)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.average_duration),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = formatTime(chartData?.overallAverageDuration ?: 0),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Overall average (only if there are laps)
                            if ((chartData?.overallAverageLapTime ?: 0) > 0) {
                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.overall_average),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = formatTime(chartData?.overallAverageLapTime ?: 0),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Overall median (only if there are laps)
                            if ((chartData?.overallMedianLapTime ?: 0) > 0) {
                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(R.string.overall_median),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = formatTime(chartData?.overallMedianLapTime ?: 0),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Total Duration Chart
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.total_duration),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TotalDurationChart(
                                statistics = chartData?.statistics ?: emptyList(),
                                dateFormat = SimpleDateFormat("dd.MM", Locale.getDefault()),
                                formatTime = ::formatTime,
                                overallAverage = chartData?.overallAverageDuration ?: 0
                            )
                        }
                    }
                }

                // Average Lap Chart (only if there are sessions with laps)
                if (chartData?.statistics?.any { it.averageLapTime > 0 } == true) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.average_lap),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                AverageLapChart(
                                    statistics = chartData?.statistics ?: emptyList(),
                                    dateFormat = SimpleDateFormat("dd.MM", Locale.getDefault()),
                                    formatTime = ::formatTime,
                                    overallAverage = chartData?.overallAverageLapTime ?: 0
                                )
                            }
                        }
                    }
                }

                // Median Lap Chart (only if there are sessions with laps)
                if (chartData?.statistics?.any { it.medianLapTime > 0 } == true) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.median_lap),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                MedianLapChart(
                                    statistics = chartData?.statistics ?: emptyList(),
                                    dateFormat = SimpleDateFormat("dd.MM", Locale.getDefault()),
                                    formatTime = ::formatTime,
                                    overallMedian = chartData?.overallMedianLapTime ?: 0
                                )
                            }
                        }
                    }
                }
            }
        }

        // Period selector dropdown menu
        DropdownMenu(
            expanded = showPeriodSelector,
            onDismissRequest = { showPeriodSelector = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.period_all_time)) },
                onClick = {
                    viewModel.selectPeriod(com.laplog.app.model.TimePeriod.ALL_TIME)
                    showPeriodSelector = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.period_last_7_days)) },
                onClick = {
                    viewModel.selectPeriod(com.laplog.app.model.TimePeriod.LAST_7_DAYS)
                    showPeriodSelector = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.period_last_30_days)) },
                onClick = {
                    viewModel.selectPeriod(com.laplog.app.model.TimePeriod.LAST_30_DAYS)
                    showPeriodSelector = false
                }
            )
        }

        // Name selector dropdown menu
        DropdownMenu(
            expanded = showNameSelector,
            onDismissRequest = { showNameSelector = false }
        ) {
            // "All names" option
            DropdownMenuItem(
                text = { Text(stringResource(R.string.all_names)) },
                onClick = {
                    viewModel.selectName(null)
                    showNameSelector = false
                }
            )
            HorizontalDivider()
            // Individual names
            availableNames.toList().forEach { name ->
                DropdownMenuItem(
                    text = { Text(nameMapping[name] ?: name) },
                    onClick = {
                        viewModel.selectName(name)
                        showNameSelector = false
                    }
                )
            }
        }
    }
}

@Composable
fun TotalDurationChart(
    statistics: List<com.laplog.app.model.SessionStatistics>,
    dateFormat: SimpleDateFormat,
    formatTime: (Long) -> String,
    overallAverage: Long
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(statistics, overallAverage) {
        modelProducer.runTransaction {
            // Data series
            lineSeries {
                series(
                    x = statistics.indices.map { it },
                    y = statistics.map { it.totalDuration / 1000.0 }
                )
            }
            // Average line series
            lineSeries {
                series(
                    x = statistics.indices.map { it },
                    y = List(statistics.size) { overallAverage / 1000.0 }
                )
            }
        }
    }

    val darkBlue = Color(0xFF0000CC)

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    // Main data line (blue with gradient)
                    object : LineCartesianLayer.Line(
                        LineCartesianLayer.LineFill.single(Fill(Color.Blue.copy(alpha = 0.5f).toArgb()))
                    ) {
                        override val areaFill = LineCartesianLayer.AreaFill.single(
                            Fill(Color.Blue.copy(alpha = 0.3f).toArgb())
                        )
                    },
                    // Average line (darker blue, dashed)
                    DashedLine(LineCartesianLayer.LineFill.single(Fill(darkBlue.toArgb())))
                )
            ),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = { value, _, _ ->
                    formatTime((value * 1000).toLong())
                }
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = { value, _, _ ->
                    val index = value.toInt()
                    if (index >= 0 && index < statistics.size) {
                        dateFormat.format(Date(statistics[index].startTime))
                    } else {
                        ""
                    }
                }
            ),
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    )
}

@Composable
fun AverageLapChart(
    statistics: List<com.laplog.app.model.SessionStatistics>,
    dateFormat: SimpleDateFormat,
    formatTime: (Long) -> String,
    overallAverage: Long
) {
    // Filter out sessions with no laps (averageLapTime = 0)
    val filteredStats = remember(statistics) {
        statistics.filter { it.averageLapTime > 0 }
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(filteredStats, overallAverage) {
        modelProducer.runTransaction {
            // Data series
            lineSeries {
                series(
                    x = filteredStats.indices.map { it },
                    y = filteredStats.map { it.averageLapTime / 1000.0 }
                )
            }
            // Average line series
            lineSeries {
                series(
                    x = filteredStats.indices.map { it },
                    y = List(filteredStats.size) { overallAverage / 1000.0 }
                )
            }
        }
    }

    val darkGreen = Color(0xFF006600)

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    // Main data line (green with gradient)
                    object : LineCartesianLayer.Line(
                        LineCartesianLayer.LineFill.single(Fill(Color.Green.copy(alpha = 0.5f).toArgb()))
                    ) {
                        override val areaFill = LineCartesianLayer.AreaFill.single(
                            Fill(Color.Green.copy(alpha = 0.3f).toArgb())
                        )
                    },
                    // Average line (darker green, dashed)
                    DashedLine(LineCartesianLayer.LineFill.single(Fill(darkGreen.toArgb())))
                )
            ),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = { value, _, _ ->
                    formatTime((value * 1000).toLong())
                }
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = { value, _, _ ->
                    val index = value.toInt()
                    if (index >= 0 && index < filteredStats.size) {
                        dateFormat.format(Date(filteredStats[index].startTime))
                    } else {
                        ""
                    }
                }
            ),
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    )
}

@Composable
fun MedianLapChart(
    statistics: List<com.laplog.app.model.SessionStatistics>,
    dateFormat: SimpleDateFormat,
    formatTime: (Long) -> String,
    overallMedian: Long
) {
    // Filter out sessions with no laps (medianLapTime = 0)
    val filteredStats = remember(statistics) {
        statistics.filter { it.medianLapTime > 0 }
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(filteredStats, overallMedian) {
        modelProducer.runTransaction {
            // Data series
            lineSeries {
                series(
                    x = filteredStats.indices.map { it },
                    y = filteredStats.map { it.medianLapTime / 1000.0 }
                )
            }
            // Median line series
            lineSeries {
                series(
                    x = filteredStats.indices.map { it },
                    y = List(filteredStats.size) { overallMedian / 1000.0 }
                )
            }
        }
    }

    val darkOrange = Color(0xFFCC8800)

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    // Main data line (yellow/orange with gradient)
                    object : LineCartesianLayer.Line(
                        LineCartesianLayer.LineFill.single(Fill(Color.Yellow.copy(alpha = 0.5f).toArgb()))
                    ) {
                        override val areaFill = LineCartesianLayer.AreaFill.single(
                            Fill(Color.Yellow.copy(alpha = 0.3f).toArgb())
                        )
                    },
                    // Median line (darker orange, dashed)
                    DashedLine(LineCartesianLayer.LineFill.single(Fill(darkOrange.toArgb())))
                )
            ),
            startAxis = VerticalAxis.rememberStart(
                valueFormatter = { value, _, _ ->
                    formatTime((value * 1000).toLong())
                }
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                valueFormatter = { value, _, _ ->
                    val index = value.toInt()
                    if (index >= 0 && index < filteredStats.size) {
                        dateFormat.format(Date(filteredStats[index].startTime))
                    } else {
                        ""
                    }
                }
            ),
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    )
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format("%d:%02d", minutes, seconds)
    }
}
