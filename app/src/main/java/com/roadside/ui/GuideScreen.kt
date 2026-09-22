package com.roadside.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadside.agent.VehicleContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    context: VehicleContext,
    onStepChanged: (Int) -> Unit,
    onFinishGuide: () -> Unit,
    onBackClicked: () -> Unit
) {
    val diagnosis = context.diagnosisCandidate
    val steps = diagnosis?.guideSteps ?: listOf(
        "Park the motorcycle safely away from traffic on a level surface.",
        "Turn off the ignition switch completely and remove the key.",
        "Check external fasteners, fluids, and drive components visually.",
        "If unsure or experiencing irregular operation, consult a qualified motorcycle mechanic."
    )
    val totalSteps = steps.size
    val currentStep = context.guideStepIndex.coerceIn(0, (totalSteps - 1).coerceAtLeast(0))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(diagnosis?.title ?: "Procedure Guide") },
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
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "${diagnosis?.title ?: "Vehicle"} inspection",
                style = MaterialTheme.typography.titleLarge,
                fontSize = 22.sp
            )

            Text(
                text = "Step ${currentStep + 1} of $totalSteps",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            LinearProgressIndicator(
                progress = { (currentStep + 1).toFloat() / totalSteps.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = steps[currentStep],
                        style = MaterialTheme.typography.headlineSmall,
                        fontSize = 20.sp,
                        lineHeight = 28.sp
                    )
                }
            }

            // Safety Warning callout for the current step
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = diagnosis?.safetyAdvisory ?: "Ensure motorcycle is switched off before inspecting moving parts.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))

            // Navigation Buttons: [ Back ]   [ Next ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (currentStep > 0) {
                            onStepChanged(currentStep - 1)
                        } else {
                            onBackClicked()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back")
                }

                Button(
                    onClick = {
                        if (currentStep < totalSteps - 1) {
                            onStepChanged(currentStep + 1)
                        } else {
                            onFinishGuide()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    if (currentStep < totalSteps - 1) {
                        Text("Next")
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Finish")
                        }
                    }
                }
            }
        }
    }
}
