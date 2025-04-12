import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.am24.am24.ProfileViewModel
import kotlinx.coroutines.launch

@Composable
fun GovtIdVerificationScreen(
    navController: NavController,
    profileViewModel: ProfileViewModel
) {
    val ctx      = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val pickImg  = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> imageUri = uri }

    val scope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("Government‑ID verification", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        if (imageUri != null) {
            Image(
                painter = rememberAsyncImagePainter(imageUri),
                contentDescription = null,
                modifier = Modifier.size(220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(2.dp, Color(0xFFFF6F00), RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(16.dp))
        }

        Button(
            onClick = { pickImg.launch("image/*") },
            colors  = ButtonDefaults.buttonColors(Color(0xFFFF6F00))
        ) { Text("Choose photo", color = Color.White) }

        Spacer(Modifier.height(24.dp))

        Button(
            enabled = imageUri != null,
            onClick = {
                imageUri?.let { uri ->
                    scope.launch {
//                        profileViewModel.uploadGovtId(uri)        // implement inside your VM
                        navController.popBackStack()             // go back to profile
                    }
                }
            },
            colors  = ButtonDefaults.buttonColors(Color(0xFF00C853))
        ) { Text("Submit – review takes up to 48 h", color = Color.White) }
    }
}
