package com.biosecure.app.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.biosecure.app.data.network.DjangoEmployee
import com.biosecure.app.data.network.RetrofitInstance
import com.biosecure.app.ui.navigation.Screen
import com.biosecure.app.ui.theme.ErrorRed
import com.biosecure.app.ui.viewmodel.BioSecureViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeListScreen(navController: NavController, viewModel: BioSecureViewModel? = null) {
    var employees by remember { mutableStateOf<List<DjangoEmployee>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var employeeToDelete by remember { mutableStateOf<DjangoEmployee?>(null) }

    LaunchedEffect(Unit) {
        try {
            employees = RetrofitInstance.api.getDjangoEmployees()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    employeeToDelete?.let { emp ->
        AlertDialog(
            onDismissRequest = { employeeToDelete = null },
            title = { Text("Eliminar empleado", fontWeight = FontWeight.Bold) },
            text = { Text("¿Eliminar a ${emp.first_name} ${emp.last_name}?") },
            confirmButton = {
                TextButton(onClick = {
                    kotlinx.coroutines.MainScope().launch {
                        try {
                            RetrofitInstance.api.deleteDjangoEmployee(emp.id)
                            employees = employees.filter { it.id != emp.id }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    employeeToDelete = null
                }) {
                    Text("Eliminar", color = ErrorRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { employeeToDelete = null }) { Text("Cancelar") }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Lista de Empleados",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00B4A6))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Text("${employees.size} empleados registrados",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                items(employees, key = { it.id }) { emp ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "👤", fontSize = 36.sp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${emp.first_name} ${emp.last_name}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface)
                                Text(emp.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                Text(emp.department,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF00B4A6))
                            }
                            IconButton(onClick = { navController.navigate(Screen.AdminEditEmployee.route(emp.id)) }) {
                                Icon(Icons.Default.Edit, null, tint = Color(0xFF00B4A6), modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { employeeToDelete = emp }) {
                                Icon(Icons.Default.Delete, null, tint = ErrorRed, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}