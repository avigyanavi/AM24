package com.am24.am24

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import com.am24.am24.ui.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.*

class RegistrationActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseDatabase.getInstance("https://am-twentyfour-default-rtdb.firebaseio.com/").reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setContent {
            AppTheme {
                RegistrationScreen(
                    onRegisterClick = { email, password, profile ->
                        val trimmedEmail = email.trim()
                        Log.d("RegistrationActivity", "Trimmed Email: $trimmedEmail")

                        auth.createUserWithEmailAndPassword(trimmedEmail, password)
                            .addOnCompleteListener(this) { task ->
                                if (task.isSuccessful) {
                                    val userId = auth.currentUser?.uid
                                    Log.d("RegistrationActivity", "User ID: $userId")
                                    if (userId != null) {
                                        // Update user's profile with username
                                        val profileUpdates = UserProfileChangeRequest.Builder()
                                            .setDisplayName(profile.username)
                                            .build()
                                        auth.currentUser?.updateProfile(profileUpdates)

                                        // Save profile to Firebase Realtime Database
                                        val updatedProfile = profile.copy(uid = userId)
                                        db.child("users").child(userId).setValue(updatedProfile)
                                            .addOnSuccessListener {
                                                // Send email verification
                                                auth.currentUser?.sendEmailVerification()
                                                    ?.addOnCompleteListener { emailTask ->
                                                        if (emailTask.isSuccessful) {
                                                            Toast.makeText(
                                                                this,
                                                                "Verification email sent. Please verify your email.",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                            // Navigate to EmailVerificationActivity
                                                            startActivity(Intent(this, EmailVerificationActivity::class.java))
                                                            finish()
                                                        } else {
                                                            Toast.makeText(
                                                                this,
                                                                "Failed to send verification email.",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    }
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(
                                                    this,
                                                    "Failed to register user in database",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                    } else {
                                        Toast.makeText(this, "User ID is null.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Log.e("RegistrationActivity", "Authentication failed: ${task.exception?.message}")
                                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                )
            }
        }
    }
}

@Composable
fun RegistrationScreen(onRegisterClick: (String, String, Profile) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var highSchool by remember { mutableStateOf("") }

    // Predefined lists
    val genders = listOf("Male", "Female", "Other")
    val states = listOf("West Bengal", "Maharashtra", "Karnataka")
    val highSchoolsByState = mapOf(
        "West Bengal" to listOf("St. James' School", "La Martiniere for Boys"),
        "Maharashtra" to listOf("The Cathedral and John Connon School", "Dhirubhai Ambani International School"),
        "Karnataka" to listOf("Bishop Cotton Boys' School", "National Public School")
    )

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Register", fontSize = 24.sp)
        Spacer(modifier = Modifier.height(16.dp))

        // Date of Birth with Calendar Picker
        DatePickerDocked(onDateSelected = { dob = it })
        Spacer(modifier = Modifier.height(16.dp))

        EmailTextField(email = email, onValueChange = { email = it })
        Spacer(modifier = Modifier.height(16.dp))

        PasswordTextField(password = password, onValueChange = { password = it })
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            textStyle = LocalTextStyle.current.copy(color = androidx.compose.ui.graphics.Color.Black),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            textStyle = LocalTextStyle.current.copy(color = androidx.compose.ui.graphics.Color.Black),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Dropdown for Gender
        CustomExposedDropdownMenu(
            selectedItem = gender,
            items = genders,
            label = "Gender",
            onItemSelected = { gender = it }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Dropdown for State
        CustomExposedDropdownMenu(
            selectedItem = state,
            items = states,
            label = "State",
            onItemSelected = { selectedState ->
                state = selectedState
                highSchool = "" // Reset high school if state changes
            }
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Dropdown for High School based on State
        val highSchools = highSchoolsByState[state] ?: emptyList()
        CustomExposedDropdownMenu(
            selectedItem = highSchool,
            items = highSchools,
            label = "High School",
            onItemSelected = { highSchool = it }
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (email.isBlank() || password.isBlank() || username.isBlank() || name.isBlank() ||
                    gender.isBlank() || dob.isBlank() || state.isBlank() || highSchool.isBlank()
                ) {
                    Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val profile = Profile(
                    uid = "",
                    username = username,
                    name = name,
                    gender = gender,
                    dob = dob,
                    state = state,
                    highSchool = highSchool
                )
                onRegisterClick(email, password, profile)
            },
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Text("Register")
        }
    }
}

// Other Composable functions below...

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomExposedDropdownMenu(
    selectedItem: String,
    items: List<String>,
    label: String,
    onItemSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedOptionText by remember { mutableStateOf(selectedItem) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        TextField(
            readOnly = true,
            value = selectedOptionText,
            onValueChange = { },
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor() // Ensures correct placement of the dropdown
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { selectionOption ->
                DropdownMenuItem(
                    text = { Text(selectionOption) },
                    onClick = {
                        selectedOptionText = selectionOption
                        onItemSelected(selectionOption)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDocked(onDateSelected: (String) -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    // Convert selected millis to a readable date string
    val selectedDate = datePickerState.selectedDateMillis?.let {
        convertMillisToDate(it)
    } ?: ""

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedDate,
            onValueChange = { },
            label = { Text("DOB") },
            textStyle = LocalTextStyle.current.copy(color = androidx.compose.ui.graphics.Color.Black),
            readOnly = true,
            trailingIcon = {
                IconButton(onClick = { showDatePicker = !showDatePicker }) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Select date"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Use Popup for the date picker
        if (showDatePicker) {
            Popup(
                onDismissRequest = { showDatePicker = false }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .shadow(4.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DatePicker(
                            state = datePickerState,
                            showModeToggle = false
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            onDateSelected(selectedDate)
                            showDatePicker = false // Dismiss the popup
                        }) {
                            Text(text = "Confirm Date")
                        }
                    }
                }
            }
        }
    }
}

fun convertMillisToDate(millis: Long): String {
    val formatter = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
    return formatter.format(Date(millis))
}

@Composable
fun PasswordTextField(
    password: String,
    onValueChange: (String) -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = password,
        onValueChange = onValueChange,
        label = { Text("Password") },
        textStyle = LocalTextStyle.current.copy(color = androidx.compose.ui.graphics.Color.Black),
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
        ),
        trailingIcon = {
            val image = if (passwordVisible)
                Icons.Filled.Visibility
            else Icons.Filled.VisibilityOff

            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(imageVector = image, contentDescription = null)
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun EmailTextField(
    email: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = email,
        onValueChange = onValueChange,
        label = { Text("Email") },
        textStyle = LocalTextStyle.current.copy(color = androidx.compose.ui.graphics.Color.Black),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

class EmailVerificationActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setContent {
            AppTheme {
                EmailVerificationScreen(onContinue = {
                    auth.currentUser?.reload()?.addOnCompleteListener { task ->
                        if (task.isSuccessful && auth.currentUser?.isEmailVerified == true) {
                            Toast.makeText(this, "Email verified successfully.", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, ExploreActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this, "Please verify your email.", Toast.LENGTH_SHORT).show()
                        }
                    }
                })
            }
        }
    }
}

@Composable
fun EmailVerificationScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Please verify your email", fontSize = 20.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onContinue() }) {
            Text("Continue")
        }
    }
}
