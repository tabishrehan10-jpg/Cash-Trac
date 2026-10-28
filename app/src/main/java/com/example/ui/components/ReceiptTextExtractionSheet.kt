package com.example.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.Categories
import com.example.data.PaymentMode
import com.example.data.TransactionType
import com.example.ui.theme.*
import com.example.util.CurrencyFormatter
import com.example.util.ExtractedLineItem
import com.example.util.ExtractedReceiptDetails
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptTextExtractionSheet(
    isExtracting: Boolean,
    extractedDetails: ExtractedReceiptDetails?,
    errorMessage: String? = null,
    onExtract: (text: String, isReceipt: Boolean) -> Unit,
    onExtractImage: (Uri) -> Unit = {},
    onConfirm: (ExtractedReceiptDetails) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Receipt Text, 1: Transaction Log, 2: Written Photo
    var inputText by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            onExtractImage(uri)
        }
    }

    val sampleReceipt = """
        CAFE COFFEE DAY #104
        MG Road, Bengaluru
        Date: 14/05/2026  Time: 18:30
        ------------------------------
        1x Cappuccino Large    Rs. 240.00
        1x Chocolate Brownie   Rs. 180.00
        1x Bottled Water       Rs.  40.00
        ------------------------------
        SUBTOTAL:             Rs. 460.00
        CGST 2.5%:            Rs.  11.50
        SGST 2.5%:            Rs.  11.50
        GRAND TOTAL:          Rs. 483.00
        Payment: Google Pay UPI (Success)
    """.trimIndent()

    val sampleLog = """
        HDFC Bank Alert: Rs 1,499.00 debited from A/C **8912 on 15-05-2026 at AMAZON INDIA via UPI ref 413592019482. Avail Bal: Rs 42,350.00.
    """.trimIndent()

    val sampleSupermarket = """
        D-MART SUPERMARKET
        Receipt No: INV-9821
        Date: 2026-05-12
        Aashirvaad Atta 5kg   420.00
        Amul Butter 500g      275.00
        Tata Tea Gold 1kg     510.00
        TOTAL AMOUNT: 1205.00
        PAID VIA: DEBIT CARD
    """.trimIndent()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(EmeraldPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Gemini AI",
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gemini Receipt & Log Extractor",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Extract vendor, amount, date from text, logs, or photos",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3-Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selectedTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { selectedTab = 0 }
                        .testTag("tab_receipt_text"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Receipt Text",
                        fontSize = 11.5.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selectedTab == 1) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { selectedTab = 1 }
                        .testTag("tab_transaction_log"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SMS / Log",
                        fontSize = 11.5.sp,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selectedTab == 2) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { selectedTab = 2 }
                        .testTag("tab_written_photo"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Written Photo",
                        fontSize = 11.5.sp,
                        fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedTab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (selectedTab == 0 || selectedTab == 1) {
                // Quick Samples Row
                Text(
                    text = "Quick Sample Templates:",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            inputText = sampleReceipt
                            selectedTab = 0
                        },
                        label = { Text("CCD Receipt", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            inputText = sampleLog
                            selectedTab = 1
                        },
                        label = { Text("Bank Debit Alert", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            inputText = sampleSupermarket
                            selectedTab = 0
                        },
                        label = { Text("Supermarket POS", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Text Input Box
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = {
                        Text(
                            if (selectedTab == 0) "Paste Captured Receipt Text / OCR Output"
                            else "Paste Bank SMS or Transaction Log"
                        )
                    },
                    placeholder = {
                        Text(
                            if (selectedTab == 0) "e.g., Starbucks Coffee, Subtotal 450, Total 480, Date 2026-09-09..."
                            else "e.g., Sent Rs 450 to Swiggy UPI on 09-09-2026 from HDFC Bank..."
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 200.dp)
                        .testTag("input_receipt_extraction_text"),
                    trailingIcon = {
                        if (inputText.isNotEmpty()) {
                            IconButton(onClick = { inputText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Button(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onExtract(inputText, selectedTab == 0)
                        }
                    },
                    enabled = inputText.isNotBlank() && !isExtracting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_extract_receipt_text"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    if (isExtracting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Extracting with Gemini AI...", fontSize = 14.sp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Extract Key Details", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Tab 2: Upload Written Photo / Paper Register / Khata Note
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "Handwritten Transaction Note",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Upload Written / Paper Transaction",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Capture or select photo of handwritten daily expenses, paper receipts, or khata register notes.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        val noteBitmap = remember(selectedImageUri) {
                            if (selectedImageUri != null) {
                                try {
                                    context.contentResolver.openInputStream(selectedImageUri!!)?.use {
                                        BitmapFactory.decodeStream(it)
                                    }
                                } catch (_: Exception) { null }
                            } else null
                        }

                        if (noteBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, EmeraldPrimary, RoundedCornerShape(12.dp))
                            ) {
                                androidx.compose.foundation.Image(
                                    bitmap = noteBitmap.asImageBitmap(),
                                    contentDescription = "Uploaded note",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("btn_upload_written_photo"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedImageUri == null) "Select Photo / Image" else "Choose Different Photo",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (selectedImageUri != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FilledTonalButton(
                                onClick = { onExtractImage(selectedImageUri!!) },
                                enabled = !isExtracting,
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) {
                                if (isExtracting) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Analyzing image with Gemini...")
                                } else {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Extract with Gemini AI")
                                }
                            }
                        }
                    }
                }
            }

            // Error message if any
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Results Section with interactive completion of missing details
            AnimatedVisibility(
                visible = extractedDetails != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                extractedDetails?.let { details ->
                    ExtractedDetailsVerificationCard(
                        details = details,
                        onConfirm = onConfirm
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtractedDetailsVerificationCard(
    details: ExtractedReceiptDetails,
    onConfirm: (ExtractedReceiptDetails) -> Unit
) {
    var vendorInput by remember(details) { mutableStateOf(details.vendor) }
    var amountInput by remember(details) {
        mutableStateOf(if (details.totalAmount > 0) String.format(Locale.US, "%.2f", details.totalAmount) else "")
    }
    var dateInput by remember(details) {
        mutableStateOf(details.date.ifBlank { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) })
    }
    var selectedCategory by remember(details) { mutableStateOf(details.category) }
    var selectedMode by remember(details) { mutableStateOf(details.paymentMode) }
    var selectedType by remember(details) { mutableStateOf(details.type) }
    var categoryDropdownOpen by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val isMissingDetails = vendorInput.isBlank() || (amountInput.toDoubleOrNull() ?: 0.0) <= 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
    ) {
        // Attention banner if details are missing
        if (isMissingDetails || details.hasMissingFields) {
            Surface(
                color = GoldAmber.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GoldAmber.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = GoldAmber, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Some details could not be detected. Please fill in the missing fields below before recording.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, EmeraldPrimary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = EmeraldPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Verify & Complete Details",
                                color = EmeraldPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Toggle Expense vs Income
                    Row {
                        FilterChip(
                            selected = selectedType == TransactionType.EXPENSE,
                            onClick = { selectedType = TransactionType.EXPENSE },
                            label = { Text("Expense", fontSize = 11.sp) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilterChip(
                            selected = selectedType == TransactionType.INCOME,
                            onClick = { selectedType = TransactionType.INCOME },
                            label = { Text("Income", fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Vendor Input (Editable)
                Text(
                    text = "VENDOR / PURPOSE ${if (vendorInput.isBlank()) "(Required)" else ""}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (vendorInput.isBlank()) GoldAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = vendorInput,
                    onValueChange = {
                        vendorInput = it
                        validationError = null
                    },
                    placeholder = { Text("e.g. Swiggy, Petrol Pump, Fruit stall") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_extracted_vendor"),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = if (vendorInput.isBlank()) GoldAmber else MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Amount Input (Editable)
                Text(
                    text = "TOTAL AMOUNT (₹) ${if ((amountInput.toDoubleOrNull() ?: 0.0) <= 0.0) "(Required)" else ""}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if ((amountInput.toDoubleOrNull() ?: 0.0) <= 0.0) GoldAmber else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = {
                        amountInput = it
                        validationError = null
                    },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("edit_extracted_amount"),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = if ((amountInput.toDoubleOrNull() ?: 0.0) <= 0.0) GoldAmber else MaterialTheme.colorScheme.outlineVariant
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Date Input (Editable)
                Text(
                    text = "DATE (YYYY-MM-DD)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = dateInput,
                    onValueChange = { dateInput = it },
                    placeholder = { Text("2026-09-09") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_extracted_date"),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = EmeraldPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Category & Payment Mode Dropdowns
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Column {
                            Text(text = "CATEGORY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clickable { categoryDropdownOpen = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = selectedCategory,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(
                                expanded = categoryDropdownOpen,
                                onDismissRequest = { categoryDropdownOpen = false }
                            ) {
                                val catList = if (selectedType == TransactionType.INCOME) Categories.incomeCategories else Categories.expenseCategories
                                catList.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name, fontSize = 13.sp) },
                                        onClick = {
                                            selectedCategory = cat.name
                                            categoryDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    var modeDropdownOpen by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(1f)) {
                        Column {
                            Text(text = "PAYMENT MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clickable { modeDropdownOpen = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = selectedMode.displayName,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                            DropdownMenu(
                                expanded = modeDropdownOpen,
                                onDismissRequest = { modeDropdownOpen = false }
                            ) {
                                PaymentMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.displayName, fontSize = 13.sp) },
                                        onClick = {
                                            selectedMode = mode
                                            modeDropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save Button
        Button(
            onClick = {
                val amt = amountInput.toDoubleOrNull() ?: 0.0
                if (vendorInput.isBlank()) {
                    validationError = "Please write or enter a vendor / description"
                    return@Button
                }
                if (amt <= 0.0) {
                    validationError = "Please write or enter a valid amount"
                    return@Button
                }

                val finalTimestamp = try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    sdf.parse(dateInput)?.time ?: System.currentTimeMillis()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }

                val finalized = details.copy(
                    vendor = vendorInput.trim(),
                    totalAmount = amt,
                    date = dateInput.trim(),
                    timestamp = finalTimestamp,
                    category = selectedCategory,
                    paymentMode = selectedMode,
                    type = selectedType,
                    isMissingVendor = false,
                    isMissingAmount = false,
                    isMissingDate = false
                )
                onConfirm(finalized)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_save_extracted_transaction"),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Save to Cash Trace Ledger",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
