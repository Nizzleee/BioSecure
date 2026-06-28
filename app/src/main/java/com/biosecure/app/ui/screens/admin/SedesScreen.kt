package com.biosecure.app.ui.screens.admin

import android.annotation.SuppressLint
import android.location.Geocoder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.biosecure.app.data.model.Sede
import com.biosecure.app.ui.navigation.Screen
import com.biosecure.app.ui.screens.scan.BioSecureBottomBar
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SedesScreen(navController: NavController, viewModel: BioSecureViewModel? = null) {
    val sedes by (viewModel?.sedes ?: MutableStateFlow(emptyList<Sede>())).collectAsState()
    val isLoading by (viewModel?.isLoading ?: MutableStateFlow(false)).collectAsState()
    val companyId by (viewModel?.currentCompanyId ?: MutableStateFlow("")).collectAsState()

    var showSheet by remember { mutableStateOf(false) }
    var selectedSede by remember { mutableStateOf<Sede?>(null) }

    LaunchedEffect(companyId) {
        if (companyId.isNotEmpty() && sedes.isEmpty()) {
            viewModel?.loadCompanyConfig(companyId)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Gestión de Sedes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        bottomBar = {
            BioSecureBottomBar(
                navController = navController,
                currentRoute = Screen.AdminSedes.route,
                isAdmin = true
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { selectedSede = null; showSheet = true },
                icon = { Icon(Icons.Default.Add, null, tint = Color.White) },
                text = {
                    Text(
                        "Nueva Sede",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            )
        }
    ) { padding ->
        if (isLoading && sedes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(sedes, key = { it.id }) { sede ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically()
                    ) {
                        SedeCard(
                            sede = sede,
                            onEdit = {
                                selectedSede = sede
                                showSheet = true
                            },
                            onClick = {
                                viewModel?.setSelectedSede(sede.id)
                                navController.navigate(Screen.Dashboard.route(sede.id)) {
                                    popUpTo(Screen.Dashboard.route(null)) { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSheet) {
        SedeEditorBottomSheet(
            sede = selectedSede,
            onDismiss = { showSheet = false },
            onSave = { nombre, lat, lng, radio ->
                viewModel?.saveSede(nombre, lat, lng, radio)
                showSheet = false
            }
        )
    }
}

@Composable
fun SedeCard(sede: Sede, onEdit: (() -> Unit)? = null, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.secondary)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        sede.nombre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (sede.id.isNotEmpty()) {
                        Text(
                            "Radio: ${sede.radioMetros}m • Geofence Activo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (onEdit != null && sede.id.isNotEmpty()) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (sede.id.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text(
                        "VER DASHBOARD DE LA SEDE",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SedeEditorBottomSheet(
    sede: Sede?,
    onDismiss: () -> Unit,
    onSave: (String, Double, Double, Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nombre by remember { mutableStateOf(sede?.nombre ?: "") }
    var location by remember { mutableStateOf(LatLng(sede?.lat ?: -12.046, sede?.lng ?: -77.042)) }
    var radio by remember { mutableFloatStateOf(sede?.radioMetros?.toFloat() ?: 100f) }

    var searchQuery by remember { mutableStateOf("") }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(location, 16f)
    }

    LaunchedEffect(location) {
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(location, 17f)
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (sede == null) "Nueva Sede" else "Editar Sede",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = nombre,
                onValueChange = { nombre = it },
                label = { Text("Nombre de la sede", color = Color.White.copy(alpha = 0.8f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar dirección o zona", color = Color.White.copy(alpha = 0.8f)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = {
                        if (searchQuery.isNotBlank()) {
                            scope.launch {
                                try {
                                    val geocoder = Geocoder(context, Locale.getDefault())
                                    val addresses = withContext(Dispatchers.IO) {
                                        geocoder.getFromLocationName(searchQuery, 1)
                                    }
                                    if (!addresses.isNullOrEmpty()) {
                                        val addr = addresses[0]
                                        location = LatLng(addr.latitude, addr.longitude)
                                    }
                                } catch (_: Exception) { }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                },
                placeholder = { Text("Ej: Av. Larco 123, Lima", color = Color.White.copy(alpha = 0.6f)) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface)
            )

            Text(
                "Ubicación en el mapa (Toca para cambiar)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box(modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    onMapClick = { location = it },
                    uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = true)
                ) {
                    Marker(state = MarkerState(position = location), draggable = true, title = nombre.ifBlank { "Nueva Sede" })
                    Circle(
                        center = location,
                        radius = radio.toDouble(),
                        fillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        strokeColor = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3f
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Radio: ${radio.toInt()}m",
                    modifier = Modifier.width(100.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Slider(
                    value = radio,
                    onValueChange = { radio = it },
                    valueRange = 20f..500f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Button(
                onClick = { onSave(nombre, location.latitude, location.longitude, radio.toInt()) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = nombre.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Guardar Configuración",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
