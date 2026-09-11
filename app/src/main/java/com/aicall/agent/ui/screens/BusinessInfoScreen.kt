package com.aicall.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicall.agent.data.BusinessKnowledgeManager
import com.aicall.agent.ui.theme.BgTop
import com.aicall.agent.ui.theme.GreenDeep
import com.aicall.agent.ui.theme.GreenPrimary
import com.aicall.agent.ui.theme.LineLight
import com.aicall.agent.ui.theme.SurfaceCard
import com.aicall.agent.ui.theme.SurfaceOverlay
import com.aicall.agent.ui.theme.TextMuted
import com.aicall.agent.ui.theme.TextPrimary
import com.aicall.agent.ui.theme.TextSecondary

@Composable
fun BusinessInfoScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val kb = BusinessKnowledgeManager.getInstance(context)

    var businessName by remember { mutableStateOf(kb.businessName) }
    var businessHours by remember { mutableStateOf(kb.businessHours) }
    var policiesFaqs by remember { mutableStateOf(kb.policiesAndFaqs) }
    var customGreeting by remember { mutableStateOf(kb.customGreeting) }
    var closingInstructions by remember { mutableStateOf(kb.callEndingInstructions) }

    var services by remember { mutableStateOf(kb.getServices()) }
    var newServiceName by remember { mutableStateOf("") }
    var newServicePrice by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgTop)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Back Row
        Row(
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = GreenDeep, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Setup", style = MaterialTheme.typography.bodyMedium, color = GreenDeep, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Business Knowledge Base",
            style = MaterialTheme.typography.displaySmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Stored 100% on-device with zero cloud upload. Injected dynamically into OpenRouter per call.",
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )

        // General Business Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "IDENTITY & HOURS",
                    style = MaterialTheme.typography.labelSmall,
                    color = GreenDeep,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Business / Desk Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = businessHours,
                    onValueChange = { businessHours = it },
                    label = { Text("Operating Hours") },
                    placeholder = { Text("e.g. Mon-Fri 8am-6pm, Sat 9am-3pm") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Services & Pricing Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SERVICES & PRICING",
                    style = MaterialTheme.typography.labelSmall,
                    color = GreenDeep,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                services.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                            Text(text = item.price, style = MaterialTheme.typography.bodySmall, color = GreenDeep, fontWeight = FontWeight.Medium)
                        }
                        IconButton(onClick = {
                            services = services.toMutableList().apply { removeAt(index) }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newServiceName,
                        onValueChange = { newServiceName = it },
                        label = { Text("Service Name") },
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = newServicePrice,
                        onValueChange = { newServicePrice = it },
                        label = { Text("Price") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    IconButton(
                        onClick = {
                            if (newServiceName.isNotBlank() && newServicePrice.isNotBlank()) {
                                services = services + BusinessKnowledgeManager.ServiceItem(
                                    id = System.currentTimeMillis().toString(),
                                    name = newServiceName.trim(),
                                    price = newServicePrice.trim()
                                )
                                newServiceName = ""
                                newServicePrice = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = GreenDeep)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Policies & FAQs Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "POLICIES, FAQS & LOCATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = GreenDeep,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = policiesFaqs,
                    onValueChange = { policiesFaqs = it },
                    label = { Text("Parking, location, cancellation policy...") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = customGreeting,
                    onValueChange = { customGreeting = it },
                    label = { Text("Opening Greeting Style") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = closingInstructions,
                    onValueChange = { closingInstructions = it },
                    label = { Text("Closing Guidance") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Save Button
        ElevatedButton(
            onClick = {
                kb.businessName = businessName
                kb.businessHours = businessHours
                kb.policiesAndFaqs = policiesFaqs
                kb.customGreeting = customGreeting
                kb.callEndingInstructions = closingInstructions
                kb.saveServices(services)
                onBack()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.elevatedButtonColors(containerColor = GreenDeep, contentColor = Color.White)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Business Knowledge Base", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(modifier = Modifier.height(90.dp))
    }
}
