package com.roadside.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadside.agent.VehicleContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosisScreen(
    context: VehicleContext,
    onGuideMeClicked: () -> Unit,
    onBackClicked: () -> Unit
) {
    val diagnosis = context.diagnosisCandidate

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vehicle Assessment") },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (diagnosis == null || diagnosis.issueKey == "unknown") "Assessment" else "Possible issue",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = diagnosis?.title ?: "Assessment in progress",
                style = MaterialTheme.typography.headlineMedium,
                fontSize = 26.sp
            )

            Text(
                text = diagnosis?.summary ?: "Collecting and analyzing sensory evidence...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            // Evidence section
            Text(text = "Evidence:", style = MaterialTheme.typography.titleMedium)

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "• Audio evidence:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = diagnosis?.audioEvidenceSummary ?: "No audio recording submitted.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "• Visual evidence:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = diagnosis?.visionEvidenceSummary ?: "No visual inspection photo submitted.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Required Tools
            Text(text = "Tools:", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tools = diagnosis?.requiredTools.orEmpty()
                    if (tools.isEmpty()) {
                        Text(text = "No specialized tools required for preliminary check.")
                    } else {
                        tools.forEach { tool ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(text = tool, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Safety Warning Callout
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Safety Alert",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = diagnosis?.safetyAdvisory ?: "Always turn off the motorcycle engine and place on flat ground before inspecting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onGuideMeClicked,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Guide Me")
            }
        }
    }
}
