package com.aibuilt.wantwhereany

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WantWhereAnyApp()
        }
    }
}

data class PotholeReport(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val imageUri: String?,
    val timestamp: Long
)

data class Officials(
    val contractor: String,
    val legislativeHead: String,
    val chiefMinister: String
)

private object OfficialsRepository {
    fun forState(state: String): Officials = when (state.lowercase()) {
        "maharashtra" -> Officials(
            "contractor.mh@example.com",
            "mla.mh@example.com",
            "cm.mh@example.com"
        )
        "delhi" -> Officials(
            "contractor.dl@example.com",
            "mla.dl@example.com",
            "cm.dl@example.com"
        )
        else -> Officials(
            "contractor@example.com",
            "legislative.head@example.com",
            "chief.minister@example.com"
        )
    }
}

@Composable
fun WantWhereAnyApp() {
    val navController = rememberNavController()
    var permissionsGranted by remember { mutableStateOf(false) }
    var currentLocation by remember { mutableStateOf<Location?>(null) }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var report by remember { mutableStateOf<PotholeReport?>(null) }

    val context = LocalContext.current
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        permissionsGranted = granted.values.all { it }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        imageUri = uri
    }

    fun requestPermissions() {
        permissionLauncher.launch(
            listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        )
    }

    fun pickImage() {
        pickLauncher.launch(
            ActivityResultContracts.PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                .build()
        )
    }

    @SuppressLint("MissingPermission")
    fun fetchLocation() {
        var loc: Location? = null
        try {
            loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            loc = null
        }
        if (loc == null) {
            try {
                loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (_: Exception) {
                loc = null
            }
        }
        currentLocation = loc
    }

    MaterialTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        permissionsGranted = permissionsGranted,
                        onRequestPermissions = { requestPermissions() },
                        currentLocation = currentLocation,
                        onFetchLocation = { fetchLocation() },
                        onReportPothole = {
                            if (permissionsGranted && currentLocation != null) {
                                navController.navigate("report")
                            } else if (!permissionsGranted) {
                                requestPermissions()
                            }
                        }
                    )
                }
                composable("report") {
                    val lat = currentLocation?.latitude ?: 0.0
                    val long = currentLocation?.longitude ?: 0.0
                    ReportScreen(
                        latitude = lat,
                        longitude = long,
                        imageUri = imageUri,
                        onImageChanged = { imageUri = it },
                        onPickImage = { pickImage() },
                        onSubmit = { description ->
                            val id = "POT-${System.currentTimeMillis()}"
                            report = PotholeReport(
                                id = id,
                                latitude = lat,
                                longitude = long,
                                description = description,
                                imageUri = imageUri?.toString(),
                                timestamp = System.currentTimeMillis()
                            )
                            navController.navigate("confirmation")
                        }
                    )
                }
                composable("confirmation") {
                    val currentReport = report
                    if (currentReport != null) {
                        ConfirmationScreen(
                            report = currentReport,
                            onSend = { sendReport(currentReport, context) },
                            onBack = { navController.popBackStack() }
                        )
                    } else {
                        Text(text = "No report data available")
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    currentLocation: Location?,
    onFetchLocation: () -> Unit,
    onReportPothole: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Want Where Any",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Report a pothole on any road",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        val locText = if (currentLocation != null) {
            "Lat: ${currentLocation.latitude}\nLng: ${currentLocation.longitude}"
        } else {
            "Location not available"
        }
        Text(text = locText, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { onFetchLocation() }, enabled = permissionsGranted) {
            Text("Detect Location")
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (!permissionsGranted) {
            Button(onClick = onRequestPermissions) {
                Text("Grant Camera & Location Permissions")
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onReportPothole,
            enabled = permissionsGranted && currentLocation != null
        ) {
            Text("Report Pothole")
        }
    }
}

@Composable
fun ReportScreen(
    latitude: Double,
    longitude: Double,
    imageUri: Uri?,
    onImageChanged: (Uri?) -> Unit,
    onPickImage: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var description by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Pothole Details", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Location: $latitude, $longitude",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Describe the road condition") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            maxLines = 4
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onPickImage) {
            Text(if (imageUri == null) "Attach Photo" else "Change Photo")
        }
        if (imageUri != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Photo selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { if (description.isNotBlank()) onSubmit(description) },
            enabled = description.isNotBlank()
        ) {
            Text("Submit Complaint")
        }
    }
}

@Composable
fun ConfirmationScreen(
    report: PotholeReport,
    onSend: () -> Unit,
    onBack: () -> Unit
) {
    val officials = OfficialsRepository.forState("maharashtra")

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Complaint Registered",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Complaint ID: ${report.id}")
        Text(text = "Location: ${report.latitude}, ${report.longitude}")
        Text(text = "Description: ${report.description}")
        Text(
            text = "Image: ${report.imageUri ?: "None"}",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Send to:")
        Text(text = officials.contractor)
        Text(text = officials.legislativeHead)
        Text(text = officials.chiefMinister)
        Spacer(modifier = Modifier.height(24.dp))

        Button(onClick = onSend) {
            Text("Send to Officials via Email")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBack) {
            Text("Back to Home")
        }
    }
}

private fun sendReport(report: PotholeReport, context: Context) {
    val officials = OfficialsRepository.forState("maharashtra")
    val recipients = listOf(officials.contractor, officials.legislativeHead, officials.chiefMinister)

    val subject = "Pothole Complaint - ${report.id}"
    val body = buildString {
        appendLine("Pothole Complaint Registered")
        appendLine("Complaint ID: ${report.id}")
        appendLine("Location: ${report.latitude}, ${report.longitude}")
        appendLine("Description: ${report.description}")
        appendLine("Photo: ${report.imageUri ?: "None"}")
        appendLine(
            "Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(report.timestamp))}"
        )
        appendLine()
        appendLine("Please take immediate action to repair the road.")
    }

    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, recipients.toTypedArray())
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }

    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(Intent.createChooser(intent, "Send complaint via"))
    } else {
        Toast.makeText(context, "No email app found to send complaint", Toast.LENGTH_LONG).show()
    }
}
