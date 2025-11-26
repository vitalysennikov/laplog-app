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
                    if (availableNames.isNotEmpty()) {
                        TextButton(
                            onClick = { showNameSelector = true },
                            modifier = Modifier.padding(horizontal = 4.dp)
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
                        localizedName = localizedSelectedName ?: chartData!!.sessionName,
                        formatTime = { viewModel.formatTime(it) }
                    )
                }
            }
        }

        // Period selector dialog
        if (showPeriodSelector) {
            AlertDialog(
                onDismissRequest = { showPeriodSelector = false },
                title = { Text(stringResource(R.string.select_name)) },
                text = {
                    Column {
                        com.laplog.app.model.TimePeriod.values().forEach { period ->
                            TextButton(
                                onClick = {
                                    viewModel.selectPeriod(period)
                                    showPeriodSelector = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = when (period) {
                                        com.laplog.app.model.TimePeriod.ALL_TIME -> stringResource(R.string.period_all_time)
                                        com.laplog.app.model.TimePeriod.LAST_7_DAYS -> stringResource(R.string.period_last_7_days)
                                        com.laplog.app.model.TimePeriod.LAST_30_DAYS -> stringResource(R.string.period_last_30_days)
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (period == selectedPeriod) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPeriodSelector = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            )
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
    localizedName: String,
    formatTime: (Long) -> String
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title
        item {
            Text(
                text = localizedName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Average chart (only show if there are sessions with laps)
        val hasLaps = chartData.statistics.any { it.averageLapTime > 0 }
        if (hasLaps) {
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

                        AverageLapChart(
                            statistics = chartData.statistics,
                            dateFormat = dateFormat,
                            formatTime = formatTime
                        )
                    }
                }
            }
        }

        // Median chart (only show if there are sessions with laps)
        if (hasLaps) {
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

                        MedianLapChart(
                            statistics = chartData.statistics,
                            dateFormat = dateFormat,
                            formatTime = formatTime
                        )
                    }
                }
            }
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
                chart = lineChart(
                    lines = listOf(
                        com.patrykandpatrick.vico.core.chart.line.LineChart.LineSpec(
                            lineColor = androidx.compose.ui.graphics.Color.Blue.hashCode(),
                            lineBackgroundShader = com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders.fromBrush(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(
                                        androidx.compose.ui.graphics.Color.Blue.copy(alpha = 0.5f),
                                        androidx.compose.ui.graphics.Color.Blue.copy(alpha = 0.0f)
                                    )
                                )
                            )
                        )
                    )
                ),
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
    // Filter out sessions with no laps (averageLapTime = 0)
    val filteredStats = remember(statistics) {
        statistics.filter { it.averageLapTime > 0 }
    }

    val entries = remember(filteredStats) {
        filteredStats.mapIndexed { index, stat ->
            FloatEntry(
                x = index.toFloat(),
                y = (stat.averageLapTime / 1000f) // Convert to seconds for better readability
            )
        }
    }

    val chartEntryModelProducer = remember(entries) {
        ChartEntryModelProducer(entries)
    }

    val xAxisValueFormatter = remember(filteredStats) {
        AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
            val index = value.toInt()
            if (index in filteredStats.indices) {
                dateFormat.format(Date(filteredStats[index].startTime))
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
                chart = lineChart(
                    lines = listOf(
                        com.patrykandpatrick.vico.core.chart.line.LineChart.LineSpec(
                            lineColor = androidx.compose.ui.graphics.Color.Green.hashCode(),
                            lineBackgroundShader = com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders.fromBrush(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(
                                        androidx.compose.ui.graphics.Color.Green.copy(alpha = 0.5f),
                                        androidx.compose.ui.graphics.Color.Green.copy(alpha = 0.0f)
                                    )
                                )
                            )
                        )
                    )
                ),
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
    // Filter out sessions with no laps (medianLapTime = 0)
    val filteredStats = remember(statistics) {
        statistics.filter { it.medianLapTime > 0 }
    }

    val entries = remember(filteredStats) {
        filteredStats.mapIndexed { index, stat ->
            FloatEntry(
                x = index.toFloat(),
                y = (stat.medianLapTime / 1000f) // Convert to seconds for better readability
            )
        }
    }

    val chartEntryModelProducer = remember(entries) {
        ChartEntryModelProducer(entries)
    }

    val xAxisValueFormatter = remember(filteredStats) {
        AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
            val index = value.toInt()
            if (index in filteredStats.indices) {
                dateFormat.format(Date(filteredStats[index].startTime))
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
                chart = lineChart(
                    lines = listOf(
                        com.patrykandpatrick.vico.core.chart.line.LineChart.LineSpec(
                            lineColor = androidx.compose.ui.graphics.Color.Yellow.hashCode(),
                            lineBackgroundShader = com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders.fromBrush(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(
                                        androidx.compose.ui.graphics.Color.Yellow.copy(alpha = 0.5f),
                                        androidx.compose.ui.graphics.Color.Yellow.copy(alpha = 0.0f)
                                    )
                                )
                            )
                        )
                    )
                ),
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
