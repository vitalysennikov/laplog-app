package com.laplog.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.laplog.app.R
import com.laplog.app.data.PreferencesManager
import com.laplog.app.data.database.dao.SessionDao
import com.laplog.app.viewmodel.ChartsViewModel
import com.laplog.app.viewmodel.ChartsViewModelFactory
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.values.AxisValuesOverrider
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.FloatEntry
import java.text.SimpleDateFormat
import java.util.*

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
    val chartData by viewModel.chartData.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentLanguage = preferencesManager.appLanguage

    // Get all sessions for name localization
    val allSessions by sessionDao.getAllSessionsWithLaps().collectAsState(initial = emptyList())

    // Map original names to localized names
    val nameMapping = remember(availableNames, allSessions, currentLanguage) {
        availableNames.associateWith { originalName ->
            allSessions.find { it.session.name == originalName }
                ?.session
                ?.getLocalizedName(currentLanguage)
                ?: originalName
        }
    }

    // Get localized name for selected name
    val localizedSelectedName = remember(selectedName, nameMapping) {
        selectedName?.let { nameMapping[it] }
    }

    var showNameSelector by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.charts)) },
                actions = {
                    // Name selector dropdown
                    if (availableNames.isNotEmpty()) {
                        TextButton(
                            onClick = { showNameSelector = true },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = localizedSelectedName ?: stringResource(R.string.select_name),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                availableNames.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.no_sessions_with_names),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                chartData == null || chartData!!.statistics.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.no_data_for_chart),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    ChartContent(
                        chartData = chartData!!,
                        formatTime = { viewModel.formatTime(it) }
                    )
                }
            }
        }

        // Name selector dialog
        if (showNameSelector) {
            AlertDialog(
                onDismissRequest = { showNameSelector = false },
                title = { Text(stringResource(R.string.select_session_name)) },
                text = {
                    LazyColumn {
                        items(availableNames) { originalName ->
                            val localizedName = nameMapping[originalName] ?: originalName
                            TextButton(
                                onClick = {
                                    viewModel.selectName(originalName)
                                    showNameSelector = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = localizedName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (originalName == selectedName) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNameSelector = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
        }
    }
}

@Composable
fun ChartContent(
    chartData: com.laplog.app.model.ChartData,
    formatTime: (Long) -> String
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        item {
            Text(
                text = chartData.sessionName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Total duration chart
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.duration),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (chartData.statistics.isNotEmpty()) {
                        TotalDurationChart(
                            statistics = chartData.statistics,
                            dateFormat = dateFormat,
                            formatTime = formatTime
                        )
                    }
                }
            }
        }

        // Average chart
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.average_lap_time),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (chartData.statistics.isNotEmpty()) {
                        AverageLapChart(
                            statistics = chartData.statistics,
                            dateFormat = dateFormat,
                            formatTime = formatTime
                        )
                    }
                }
            }
        }

        // Median chart
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.median_lap_time),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (chartData.statistics.isNotEmpty()) {
                        MedianLapChart(
                            statistics = chartData.statistics,
                            dateFormat = dateFormat,
                            formatTime = formatTime
                        )
                    }
                }
            }
        }

        // Statistics summary
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
                        text = stringResource(R.string.statistics_summary),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.total_sessions),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = chartData.statistics.size.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val averages = chartData.statistics.map { it.averageLapTime }
                    val overallAvg = averages.average().toLong()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.overall_average),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatTime(overallAvg),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val medians = chartData.statistics.map { it.medianLapTime }
                    val overallMedian = medians.sorted()[medians.size / 2]

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.overall_median),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = formatTime(overallMedian),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TotalDurationChart(
    statistics: List<com.laplog.app.model.SessionStatistics>,
    dateFormat: SimpleDateFormat,
    formatTime: (Long) -> String
) {
    val entries = remember(statistics) {
        statistics.mapIndexed { index, stat ->
            FloatEntry(
                x = index.toFloat(),
                y = (stat.totalDuration / 1000f) // Convert to seconds for better readability
            )
        }
    }

    val chartEntryModelProducer = remember(entries) {
        ChartEntryModelProducer(entries)
    }

    val xAxisValueFormatter = remember(statistics) {
        AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
            val index = value.toInt()
            if (index in statistics.indices) {
                dateFormat.format(Date(statistics[index].startTime))
            } else {
                ""
            }
        }
    }

    val yAxisValueFormatter = remember {
        AxisValueFormatter<AxisPosition.Vertical.Start> { value, _ ->
            val millis = (value * 1000).toLong()
            formatTime(millis)
        }
    }

    val model = chartEntryModelProducer.getModel()
    if (model != null) {
        ProvideChartStyle {
            Chart(
                chart = lineChart(),
                model = model,
                startAxis = rememberStartAxis(
                    valueFormatter = yAxisValueFormatter
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = xAxisValueFormatter
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}

@Composable
fun AverageLapChart(
    statistics: List<com.laplog.app.model.SessionStatistics>,
    dateFormat: SimpleDateFormat,
    formatTime: (Long) -> String
) {
    val entries = remember(statistics) {
        statistics.mapIndexed { index, stat ->
            FloatEntry(
                x = index.toFloat(),
                y = (stat.averageLapTime / 1000f) // Convert to seconds for better readability
            )
        }
    }

    val chartEntryModelProducer = remember(entries) {
        ChartEntryModelProducer(entries)
    }

    val xAxisValueFormatter = remember(statistics) {
        AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
            val index = value.toInt()
            if (index in statistics.indices) {
                dateFormat.format(Date(statistics[index].startTime))
            } else {
                ""
            }
        }
    }

    val yAxisValueFormatter = remember {
        AxisValueFormatter<AxisPosition.Vertical.Start> { value, _ ->
            val millis = (value * 1000).toLong()
            formatTime(millis)
        }
    }

    val model = chartEntryModelProducer.getModel()
    if (model != null) {
        ProvideChartStyle {
            Chart(
                chart = lineChart(),
                model = model,
                startAxis = rememberStartAxis(
                    valueFormatter = yAxisValueFormatter
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = xAxisValueFormatter
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}

@Composable
fun MedianLapChart(
    statistics: List<com.laplog.app.model.SessionStatistics>,
    dateFormat: SimpleDateFormat,
    formatTime: (Long) -> String
) {
    val entries = remember(statistics) {
        statistics.mapIndexed { index, stat ->
            FloatEntry(
                x = index.toFloat(),
                y = (stat.medianLapTime / 1000f) // Convert to seconds for better readability
            )
        }
    }

    val chartEntryModelProducer = remember(entries) {
        ChartEntryModelProducer(entries)
    }

    val xAxisValueFormatter = remember(statistics) {
        AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
            val index = value.toInt()
            if (index in statistics.indices) {
                dateFormat.format(Date(statistics[index].startTime))
            } else {
                ""
            }
        }
    }

    val yAxisValueFormatter = remember {
        AxisValueFormatter<AxisPosition.Vertical.Start> { value, _ ->
            val millis = (value * 1000).toLong()
            formatTime(millis)
        }
    }

    val model = chartEntryModelProducer.getModel()
    if (model != null) {
        ProvideChartStyle {
            Chart(
                chart = lineChart(),
                model = model,
                startAxis = rememberStartAxis(
                    valueFormatter = yAxisValueFormatter
                ),
                bottomAxis = rememberBottomAxis(
                    valueFormatter = xAxisValueFormatter
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
        }
    }
}
