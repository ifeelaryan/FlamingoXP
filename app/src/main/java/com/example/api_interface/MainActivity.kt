@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.api_interface

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.api_interface.ui.theme.API_InterfaceTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.compose.animation.core.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

// Colors updated to match the image precisely
val FlamingoOrange = Color(0xFFFFA000)
val FlamingoRed = Color(0xFFB71C1C)
val ProfileBg = Color(0xFFFDF7F2)
val HomeBg = Color(0xFFFFF9F1)
val CardBg = Color(0xFFFFFFFF)
val TagBg = Color(0xFFFFF3E0)

// Data Models
data class CartItem(val spice: Spice, var quantity: Int)
data class Order(
    val id: String, 
    val items: List<CartItem>, 
    val total: Double, 
    val date: String, 
    val address: String,
    val status: String = "In Transit",
    val timestamp: Long = System.currentTimeMillis()
)
data class UserProfile(var name: String, var email: String, var phone: String, var photoUri: String? = null)
data class Review(val user: String, val rating: Int, val comment: String, val date: String)

val dummyReviews = listOf(
    Review("Priya Sharma", 5, "Extremely fresh and aromatic. The packaging was also very secure!", "2 days ago"),
    Review("Amit Verma", 4, "Very high quality. Use it for my daily cooking. Only issue was delivery delay.", "1 week ago"),
    Review("Sneha Kapur", 5, "Best organic spices I've found online. The color is so natural.", "3 days ago")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var darkTheme by remember { mutableStateOf(false) }
            API_InterfaceTheme(darkTheme = darkTheme) {
                AppNavigator(
                    darkTheme = darkTheme,
                    onThemeChange = { darkTheme = it }
                )
            }
        }
    }
}

enum class Screen {
    Splash, Login, Register, Home, ItemDetail, OrderSummary, ShippingDetails, Checkout, Success, Cart, Orders, Account, HelpCenter, Chat, Tracking, Offers
}

@Composable
fun AppNavigator(darkTheme: Boolean, onThemeChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sharedPrefs = remember { context.getSharedPreferences("FlamingoPrefs", Context.MODE_PRIVATE) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    // Auth State
    var isLoggedIn by remember { mutableStateOf(sharedPrefs.getBoolean("isLoggedIn", false)) }
    var currentScreen by remember { mutableStateOf(if (isLoggedIn) Screen.Home else Screen.Splash) }
    
    // Cart & Order State
    val cart = remember { mutableStateListOf<CartItem>() }
    
    // User Profile
    var userProfile by remember { 
        mutableStateOf(UserProfile(
            name = sharedPrefs.getString("userName", "Aryan Gupta") ?: "Aryan Gupta",
            email = sharedPrefs.getString("userEmail", "aryangupta@example.com") ?: "aryangupta@example.com",
            phone = sharedPrefs.getString("userPhone", "+91 9876543210") ?: "+91 9876543210"
        ))
    }

    // Persistent State for Orders & Payment Methods
    val orders = remember { mutableStateListOf<Order>() }
    
    // User Preference Learning (Flavor Intelligence)
    var favoriteFlavorProfile by remember { mutableStateOf(sharedPrefs.getString("prefFlavor", "Authentic") ?: "Authentic") }
    
    // Saved Payment Methods
    var savedCardNumber by remember { mutableStateOf(sharedPrefs.getString("savedCard", "") ?: "") }
    var savedUpiId by remember { mutableStateOf(sharedPrefs.getString("savedUpi", "") ?: "") }
    
    // Last Shipping Details
    var lastAddress by remember { mutableStateOf(sharedPrefs.getString("lastAddress", "New York, USA") ?: "New York, USA") }
    var lastPhone by remember { mutableStateOf(sharedPrefs.getString("lastPhone", "+91 9876543210") ?: "+91 9876543210") }
    var lastName by remember { mutableStateOf(sharedPrefs.getString("lastName", "Aryan Gupta") ?: "Aryan Gupta") }

    // Shipping Details State (Current Order)
    var name by remember { mutableStateOf(lastName) }
    var phone by remember { mutableStateOf(lastPhone) }
    var address by remember { mutableStateOf(lastAddress) }
    
    var selectedSpice by remember { mutableStateOf<Spice?>(null) }
    var selectedOrder by remember { mutableStateOf<Order?>(null) }

    // Location State
    var currentCity by remember { mutableStateOf("New York, USA") }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                try {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { location ->
                            location?.let {
                                coroutineScope.launch {
                                    val geocoder = Geocoder(context, Locale.getDefault())
                                    val addresses = withContext(Dispatchers.IO) {
                                        @Suppress("DEPRECATION")
                                        geocoder.getFromLocation(it.latitude, it.longitude, 1)
                                    }
                                    if (!addresses.isNullOrEmpty()) {
                                        val addr = addresses[0]
                                        currentCity = addr.locality ?: addr.subAdminArea ?: "Current Location"
                                        address = addr.getAddressLine(0)
                                    }
                                }
                            }
                        }
                } catch (e: SecurityException) {}
            }
        }
    )

    fun refreshLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    location?.let {
                        coroutineScope.launch {
                            val geocoder = Geocoder(context, Locale.getDefault())
                            val addresses = withContext(Dispatchers.IO) {
                                @Suppress("DEPRECATION")
                                geocoder.getFromLocation(it.latitude, it.longitude, 1)
                            }
                            if (!addresses.isNullOrEmpty()) {
                                currentCity = addresses[0].locality ?: addresses[0].subAdminArea ?: "Current Location"
                                address = addresses[0].getAddressLine(0)
                            }
                        }
                    }
                }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            refreshLocation()
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState == Screen.Splash) {
                fadeIn() togetherWith fadeOut()
            } else {
                (fadeIn(animationSpec = tween(500)) + scaleIn(initialScale = 0.92f)) togetherWith 
                fadeOut(animationSpec = tween(500))
            }
        },
        label = "ScreenTransition"
    ) { targetScreen ->
        when (targetScreen) {
            Screen.Splash -> AppSplashScreen(onTimeout = { currentScreen = if (isLoggedIn) Screen.Home else Screen.Login })
            Screen.Login -> LoginScreen(
                onLoginSuccess = { email, rememberMe ->
                    isLoggedIn = true
                    userProfile = userProfile.copy(email = email)
                    if (rememberMe) {
                        sharedPrefs.edit().putBoolean("isLoggedIn", true).putString("userEmail", email).apply()
                    }
                    currentScreen = Screen.Home
                },
                onNavigateToRegister = { currentScreen = Screen.Register }
            )
            Screen.Register -> RegisterScreen(
                onRegisterSuccess = { firstName, email, phoneNum ->
                    isLoggedIn = true
                    userProfile = UserProfile(firstName, email, phoneNum)
                    sharedPrefs.edit().putString("userName", firstName).putString("userEmail", email).putString("userPhone", phoneNum).apply()
                    currentScreen = Screen.Home
                },
                onNavigateToLogin = { currentScreen = Screen.Login }
            )
            Screen.Home -> MainScreen(
                onAddToCart = { spice ->
                    val existing = cart.find { it.spice.id == spice.id }
                    if (existing != null) existing.quantity++ else cart.add(CartItem(spice, 1))
                },
                onItemClick = { spice ->
                    selectedSpice = spice
                    currentScreen = Screen.ItemDetail
                },
                onNavigate = { currentScreen = it },
                cartCount = cart.sumOf { it.quantity },
                currentLocation = currentCity,
                onRefreshLocation = { refreshLocation() }
            )
            Screen.Cart -> CartScreen(
                cartItems = cart,
                onBack = { currentScreen = Screen.Home },
                onCheckout = { currentScreen = Screen.ShippingDetails },
                onUpdateQuantity = { item, q -> 
                    val index = cart.indexOf(item)
                    if (q > 0) cart[index] = item.copy(quantity = q) else cart.removeAt(index)
                },
                onNavigate = { currentScreen = it }
            )
            Screen.ItemDetail -> selectedSpice?.let { spice ->
                ItemDetailScreen(
                    spice = spice,
                    onAddToCart = { qty -> 
                        val existing = cart.find { it.spice.id == spice.id }
                        if (existing != null) existing.quantity += qty else cart.add(CartItem(spice, qty))
                    },
                    onBuyNow = { qty ->
                        cart.clear()
                        cart.add(CartItem(spice, qty))
                        currentScreen = Screen.ShippingDetails
                    },
                    onBack = { currentScreen = Screen.Home },
                    cartCount = cart.sumOf { it.quantity },
                    onNavigateToCart = { currentScreen = Screen.Cart }
                )
            }
            Screen.ShippingDetails -> ShippingDetailsScreen(
                name = name,
                phone = phone,
                address = address,
                onNameChange = { name = it },
                onPhoneChange = { phone = it },
                onAddressChange = { address = it },
                onBack = { currentScreen = Screen.Cart },
                onProceed = { currentScreen = Screen.OrderSummary }
            )
            Screen.OrderSummary -> OrderSummaryScreen(
                cartItems = cart,
                name = name,
                phone = phone,
                address = address,
                onBack = { currentScreen = Screen.ShippingDetails },
                onConfirmOrder = { 
                    val total = cart.sumOf { it.spice.price * it.quantity } + 40
                    val newOrder = Order(
                        id = "FLM${System.currentTimeMillis().toString().takeLast(6)}",
                        items = cart.toList(),
                        total = total,
                        date = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()).format(Date()),
                        address = address
                    )
                    orders.add(0, newOrder)
                    cart.clear()
                    
                    // Save last details
                    sharedPrefs.edit()
                        .putString("lastAddress", address)
                        .putString("lastPhone", phone)
                        .putString("lastName", name)
                        .apply()

                    // Firebase Storage Placeholder (Requires google-services.json)
                    try {
                        val db = Firebase.firestore
                        val orderData = hashMapOf(
                            "orderId" to newOrder.id,
                            "total" to newOrder.total,
                            "address" to newOrder.address,
                            "status" to newOrder.status,
                            "timestamp" to newOrder.timestamp
                        )
                        db.collection("orders").add(orderData)
                    } catch (e: Exception) {
                        // Firebase not initialized or missing config
                    }
                    
                    currentScreen = Screen.Success 
                }
            )
            Screen.Checkout -> { /* Not used separately in this flow */ }
            Screen.Success -> OrderSuccessScreen(onContinue = { currentScreen = Screen.Home })
            Screen.Orders -> OrdersHistoryScreen(
                orders = orders, 
                onBack = { currentScreen = Screen.Home },
                onTrackOrder = { order ->
                    selectedOrder = order
                    currentScreen = Screen.Tracking
                }
            )
            Screen.Account -> AccountScreen(
                profile = userProfile,
                onBack = { currentScreen = Screen.Home },
                onNavigate = { currentScreen = it },
                onLogout = {
                    isLoggedIn = false
                    sharedPrefs.edit().putBoolean("isLoggedIn", false).apply()
                    currentScreen = Screen.Login
                }
            )
            Screen.HelpCenter -> HelpCenterScreen(onBack = { currentScreen = Screen.Account })
            Screen.Chat -> AIChatScreen(
                onBack = { currentScreen = Screen.Home },
                onAddToCart = { spice ->
                    val existing = cart.find { it.spice.id == spice.id }
                    if (existing != null) existing.quantity++ else cart.add(CartItem(spice, 1))
                    
                    // Learn from user interaction
                    if (spice.name.contains("Chilli")) favoriteFlavorProfile = "Spicy"
                    sharedPrefs.edit().putString("prefFlavor", favoriteFlavorProfile).apply()
                }
            )
            Screen.Tracking -> selectedOrder?.let { order ->
                TrackingScreen(order = order, onBack = { currentScreen = Screen.Orders })
            }
            Screen.Offers -> OffersScreen(onBack = { currentScreen = Screen.Account })
        }
    }
}

@Composable
fun MainScreen(
    onAddToCart: (Spice) -> Unit,
    onItemClick: (Spice) -> Unit,
    onNavigate: (Screen) -> Unit,
    cartCount: Int,
    currentLocation: String,
    onRefreshLocation: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Flamingo",
                            color = FlamingoRed,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "XP",
                            color = FlamingoOrange,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate(Screen.Chat) }) {
                        Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color.Black)
                    }
                    IconButton(onClick = { /* Search */ }) {
                        Icon(Icons.Default.Search, null, tint = Color.Black)
                    }
                    IconButton(onClick = { onNavigate(Screen.Cart) }) {
                        BadgedBox(badge = { 
                            if (cartCount > 0) {
                                Badge(containerColor = FlamingoRed) { 
                                    Text(cartCount.toString(), color = Color.White) 
                                } 
                            } 
                        }) {
                            Icon(Icons.Default.ShoppingCart, null, tint = Color.Black)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = FlamingoRed,
                        selectedTextColor = FlamingoRed,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = FlamingoOrange.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Category, null) },
                    label = { Text("Categories") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = FlamingoRed,
                        selectedTextColor = FlamingoRed,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = FlamingoOrange.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { 
                        selectedTab = 2
                        onNavigate(Screen.Orders)
                    },
                    icon = { 
                        BadgedBox(badge = { Badge { Text("0") } }) {
                            Icon(Icons.Default.ShoppingBag, null)
                        }
                    },
                    label = { Text("Orders") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = FlamingoRed,
                        selectedTextColor = FlamingoRed,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = FlamingoOrange.copy(alpha = 0.1f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { 
                        selectedTab = 3
                        onNavigate(Screen.Account)
                    },
                    icon = { Icon(Icons.Default.Person, null) },
                    label = { Text("Profile") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = FlamingoRed,
                        selectedTextColor = FlamingoRed,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = FlamingoOrange.copy(alpha = 0.1f)
                    )
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(HomeBg)
        ) {
            // Location Bar
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = HomeBg
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .clickable { onRefreshLocation() }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.LocationOn, null, tint = FlamingoRed, modifier = Modifier.size(20.dp))
                        Text(
                            text = currentLocation,
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Icon(Icons.Default.ArrowDropDown, null, tint = Color.Black)
                    }
                }
            }

            // Search Bar
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        placeholder = { 
                            Column {
                                Text("Search spices...", color = Color.Gray, fontSize = 14.sp)
                                Text("Search Turmeric, Chilli, Garam Masala...", color = Color.Gray.copy(alpha = 0.5f), fontSize = 10.sp)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedIndicatorColor = Color.LightGray.copy(alpha = 0.5f),
                            unfocusedIndicatorColor = Color.LightGray.copy(alpha = 0.5f)
                        ),
                        singleLine = true,
                        readOnly = true
                    )
                }
            }

            // Popular Categories
            item {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🌶", fontSize = 18.sp)
                    Text(
                        " Popular Categories",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                CategoryGrid()
            }

            // Best Selling
            item {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", fontSize = 18.sp)
                    Text(
                        " Best Selling",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Best Selling Section as 2-column items
            val bestSelling = allSpices.filter { it.rating >= 4.6 }.take(4)
            items(bestSelling.chunked(2)) { rowItems ->
                Row(modifier = Modifier.padding(horizontal = 8.dp).fillMaxWidth()) {
                    rowItems.forEach { spice ->
                        Box(modifier = Modifier.weight(1f)) {
                            SpiceItem(spice, onAddToCart = { onAddToCart(spice) }, onClick = { onItemClick(spice) })
                        }
                    }
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }

            // Banner
            item {
                ExportBanner()
            }

            // Recommended
            item {
                FlavorIntelligenceBanner(onNavigate = { onNavigate(Screen.Chat) })
            }

            item {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", fontSize = 18.sp)
                    Text(
                        " Recommended For You",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Recommended Section as horizontal row
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(allSpices.filter { it.price < 200 }.take(4)) { spice ->
                        SpiceItem(spice, onAddToCart = { onAddToCart(spice) }, onClick = { onItemClick(spice) })
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun CategoryGrid() {
    val categories = listOf(
        "Turmeric" to "🍌", 
        "Chilli" to "🍎", 
        "Coriander" to "🌿", 
        "Masala" to "🍛", 
        "Whole Spices" to "📦", 
        "Organic" to "🥗", 
        "Combo Packs" to "🎁"
    )
    
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            categories.take(4).forEach { (name, emoji) ->
                CategoryChip(name, emoji)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            categories.drop(4).forEach { (name, emoji) ->
                CategoryChip(name, emoji)
            }
        }
    }
}

@Composable
fun CategoryChip(name: String, emoji: String) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(600)) + slideInHorizontally(animationSpec = tween(600)) { -20 }
    ) {
        Surface(
            modifier = Modifier.padding(end = 8.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFFFECB3).copy(alpha = 0.4f),
            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(emoji, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun FlavorIntelligenceBanner(onNavigate: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable { onNavigate() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE1F5FE))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🧠", fontSize = 20.sp)
                    Text(
                        " Flavor Intelligence",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF01579B)
                    )
                }
                Text(
                    "Select your desired taste, and we'll build the recipe and spice kit for you.",
                    fontSize = 12.sp,
                    color = Color(0xFF0277BD)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNavigate,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0288D1)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Get Advice", fontSize = 10.sp, color = Color.White)
                }
            }
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF0288D1),
                modifier = Modifier.size(60.dp)
            )
        }
    }
}

@Composable
fun ExportBanner() {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(800)) + expandVertically()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🍜", fontSize = 20.sp)
                        Text(
                            " Export Quality Spices",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                    Text(
                        "Premium Indian spices shipped globally",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { },
                        colors = ButtonDefaults.buttonColors(containerColor = FlamingoRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Explore Collection", fontSize = 10.sp, color = Color.White)
                    }
                }
                Image(
                    painter = painterResource(id = R.drawable.turmeric), 
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun ItemDetailScreen(
    spice: Spice, 
    onAddToCart: (Int) -> Unit, 
    onBuyNow: (Int) -> Unit, 
    onBack: () -> Unit,
    cartCount: Int,
    onNavigateToCart: () -> Unit
) {
    var quantity by remember { mutableStateOf(1) }
    var selectedWeight by remember { mutableStateOf("100g") }
    var selectedTab by remember { mutableStateOf(0) } // 0: Details, 1: Reviews, 2: Related
    
    val basePrice = spice.price
    val displayPrice = if (selectedWeight == "50g") (basePrice * 0.6).toInt() else basePrice.toInt()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Flamingo",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "XP",
                            color = FlamingoOrange,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCart) {
                        BadgedBox(badge = { 
                            if (cartCount > 0) Badge { Text(cartCount.toString()) } 
                        }) {
                            Icon(Icons.Default.ShoppingCart, null, tint = Color.White)
                        }
                    }
                    IconButton(onClick = { /* Toggle favorite */ }) {
                        Icon(Icons.Default.FavoriteBorder, null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FlamingoRed)
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 16.dp,
                color = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .navigationBarsPadding()
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { onAddToCart(quantity) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FlamingoOrange.copy(alpha = 0.9f))
                    ) {
                        Text(
                            "Add to Cart",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Button(
                        onClick = { onBuyNow(quantity) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FlamingoRed)
                    ) {
                        Text(
                            "Buy Now",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(Color.White)
                .animateContentSize()
        ) {
            // Image Section
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(spice.imageRes),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp),
                    contentScale = ContentScale.Crop
                )
                
                // Badge overlay
                Surface(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.BottomStart),
                    color = Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        "100% Pure & Authentic",
                        color = Color(0xFF8D6E63),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Column(modifier = Modifier.padding(16.dp)) {
                // Title and Weight
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        spice.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Row {
                        WeightChip("50g", selectedWeight == "50g") { selectedWeight = "50g" }
                        Spacer(modifier = Modifier.width(8.dp))
                        WeightChip("100g", selectedWeight == "100g") { selectedWeight = "100g" }
                    }
                }
                
                // Rating
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    repeat(5) { index ->
                        Icon(
                            Icons.Default.Star,
                            null,
                            tint = if (index < 4) Color(0xFFFFB300) else Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(" 4.8", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(" (1,932 reviews)", color = Color.Gray, fontSize = 12.sp)
                }
                
                // Price and Quantity
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "₹$displayPrice",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black
                    )
                    
                    // Small quantity selector
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFF5F5F5), RoundedCornerShape(20.dp))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            modifier = Modifier.size(28.dp).background(FlamingoOrange, CircleShape)
                        ) {
                            Icon(Icons.Default.Remove, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            quantity.toString(),
                            modifier = Modifier.padding(horizontal = 12.dp),
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { quantity++ },
                            modifier = Modifier.size(28.dp).background(FlamingoOrange, CircleShape)
                        ) {
                            Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                
                // Description
                Text(
                    "FlamingoXP ${spice.name} is pure and authentic, sourced from the finest turmeric roots in India. Enhance your dishes with its rich color and warm, earthy flavor.",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 16.dp),
                    lineHeight = 20.sp
                )
                
                // Highlights
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F1)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            HighlightItem("100% Pure & Natural", Modifier.weight(1f))
                            HighlightItem("Premium Export Quality", Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        HighlightItem("Directly Sourced from India", Modifier)
                    }
                }
                
                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TabItem("Product Details", selectedTab == 0) { selectedTab = 0 }
                    TabItem("Reviews", selectedTab == 1) { selectedTab = 1 }
                    TabItem("Related Products", selectedTab == 2) { selectedTab = 2 }
                }
                
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp), thickness = 1.dp, color = Color.LightGray.copy(alpha = 0.3f))
                
                // Tab Content with animation
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "TabContent"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> ProductDetailsContent(spice)
                        1 -> ReviewsContent()
                        2 -> RelatedProductsContent()
                    }
                }
            }
        }
    }
}

@Composable
fun WeightChip(weight: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = if (isSelected) FlamingoOrange else Color(0xFFF5F5F5),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            weight,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            color = if (isSelected) Color.White else Color.Gray,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

@Composable
fun HighlightItem(text: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            Icons.Default.Check,
            null,
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 4.dp),
            color = Color(0xFF5D4037),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TabItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text,
            color = if (isSelected) FlamingoRed else Color.Gray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(2.dp)
                    .background(FlamingoRed)
            )
        }
    }
}

@Composable
fun ProductDetailsContent(spice: Spice) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text("Detailed Information about ${spice.name}", fontWeight = FontWeight.Bold)
        Text(
            "Our ${spice.name} is harvested at peak ripeness to ensure maximum flavor and potency. " +
            "The packaging is designed to keep moisture out and freshness in.",
            color = Color.Gray,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row {
            Column(modifier = Modifier.weight(1f)) {
                Text("Standard Weight", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(spice.weight, color = Color.Gray, fontSize = 12.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Shelf Life", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("18 Months", color = Color.Gray, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Shipping Details", fontWeight = FontWeight.Bold)
        Text(
            "Delivered within 3-5 business days. Free shipping on orders above ₹500.",
            color = Color.Gray,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun ReviewsContent() {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        dummyReviews.forEach { review ->
            ReviewItem(review)
        }
    }
}

@Composable
fun RelatedProductsContent() {
    LazyRow(
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(allSpices.take(4)) { spice ->
             Card(
                 modifier = Modifier.width(150.dp),
                 colors = CardDefaults.cardColors(containerColor = Color.White),
                 elevation = CardDefaults.cardElevation(2.dp)
             ) {
                 Column {
                     Image(
                         painterResource(spice.imageRes), 
                         null, 
                         modifier = Modifier.height(100.dp).fillMaxWidth(), 
                         contentScale = ContentScale.Crop
                     )
                     Text(
                         spice.name, 
                         modifier = Modifier.padding(8.dp), 
                         maxLines = 1, 
                         fontWeight = FontWeight.Bold,
                         fontSize = 12.sp
                     )
                     Text(
                         "₹${spice.price}", 
                         modifier = Modifier.padding(start = 8.dp, bottom = 8.dp), 
                         color = FlamingoRed,
                         fontWeight = FontWeight.Bold,
                         fontSize = 12.sp
                     )
                 }
             }
        }
    }
}

@Composable
fun ReviewItem(review: Review) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(review.user, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(review.date, color = Color.Gray, fontSize = 12.sp)
        }
        Row(modifier = Modifier.padding(top = 4.dp)) {
            repeat(5) { index ->
                Icon(
                    Icons.Default.Star, 
                    null, 
                    tint = if (index < review.rating) Color(0xFFFFB300) else Color.LightGray,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Text(review.comment, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp), thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
    }
}

@Composable
fun CartScreen(
    cartItems: List<CartItem>, 
    onBack: () -> Unit, 
    onCheckout: () -> Unit, 
    onUpdateQuantity: (CartItem, Int) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val subtotal = cartItems.sumOf { it.spice.price * it.quantity }
    val shipping = if (cartItems.isEmpty()) 0 else 40
    val total = subtotal + shipping

    Scaffold(
        containerColor = Color(0xFFFDF7F2),
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("My Cart", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        if (cartItems.isNotEmpty()) {
                            Text("${cartItems.size} items", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                },
                navigationIcon = { 
                    IconButton(onClick = onBack) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = FlamingoRed) 
                    } 
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            if (cartItems.isNotEmpty()) {
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                    shadowElevation = 16.dp,
                    color = Color.White
                ) {
                    Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Total Amount", color = Color.Gray, fontSize = 14.sp)
                                Text("₹$total", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = FlamingoRed)
                            }
                            Button(
                                onClick = onCheckout,
                                modifier = Modifier.height(56.dp).width(180.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = FlamingoOrange)
                            ) {
                                Text("Checkout Now", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (cartItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(120.dp),
                        shape = CircleShape,
                        color = Color(0xFFFFE0B2).copy(alpha = 0.3f)
                    ) {
                        Icon(Icons.Default.ShoppingCart, null, modifier = Modifier.padding(30.dp), tint = FlamingoOrange)
                    }
                    Text("Your cart is empty", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(top = 24.dp))
                    Text("Looks like you haven't added anything to your cart yet.", color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp))
                    Button(
                        onClick = { onNavigate(Screen.Home) },
                        modifier = Modifier.padding(top = 24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FlamingoRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Start Shopping")
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                // Free Shipping Progress
                item {
                    val progress = (subtotal.toFloat() / 500f).coerceAtMost(1f)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalShipping, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                if (subtotal >= 500) {
                                    Text("You've unlocked FREE Shipping!", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2E7D32))
                                } else {
                                    Text("Add ₹${500 - subtotal} more for FREE shipping", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2E7D32))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp).clip(CircleShape),
                                        color = Color(0xFF4CAF50),
                                        trackColor = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                items(cartItems) { item ->
                    CartItemRow(item, onUpdateQuantity)
                }

                // Promo Code Section
                item {
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        placeholder = { Text("Enter Promo Code", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = { Text("Apply", color = FlamingoRed, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 12.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
                    )
                }

                // Detailed Bill
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Bill Details", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            BillRow("Item Total", "₹$subtotal")
                            BillRow("Delivery Fee", "₹$shipping", isFree = subtotal >= 500)
                            BillRow("Taxes and Charges", "₹${(subtotal * 0.05).toInt()}")
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("To Pay", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                Text("₹${total + (subtotal * 0.05).toInt()}", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = FlamingoRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BillRow(label: String, value: String, isFree: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray, fontSize = 14.sp)
        if (isFree) {
            Row {
                Text(value, color = Color.Gray, fontSize = 14.sp, style = androidx.compose.ui.text.TextStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough))
                Spacer(modifier = Modifier.width(4.dp))
                Text("FREE", color = Color(0xFF4CAF50), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Text(value, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        }
    }
}

@Composable
fun CartItemRow(item: CartItem, onUpdateQuantity: (CartItem, Int) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(400)) + slideInHorizontally(animationSpec = tween(400)) { it / 2 },
        exit = fadeOut(animationSpec = tween(400)) + slideOutHorizontally(animationSpec = tween(400)) { -it }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(item.spice.imageRes),
                    contentDescription = null,
                    modifier = Modifier.size(90.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(item.spice.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(item.spice.weight, color = Color.Gray, fontSize = 12.sp)
                    Text("₹${item.spice.price}", color = FlamingoRed, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                        Surface(
                            modifier = Modifier.clickable { onUpdateQuantity(item, item.quantity - 1) },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF5F5F5),
                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Remove, null, modifier = Modifier.size(24.dp).padding(4.dp), tint = FlamingoRed)
                        }
                        
                        Text(item.quantity.toString(), modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        
                        Surface(
                            modifier = Modifier.clickable { onUpdateQuantity(item, item.quantity + 1) },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF5F5F5),
                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(24.dp).padding(4.dp), tint = FlamingoRed)
                        }
                    }
                }
                IconButton(
                    onClick = { 
                        visible = false
                        // Delay removal to allow animation to play
                        // In a real app, this would be handled by the state management
                        onUpdateQuantity(item, 0)
                    },
                    modifier = Modifier.align(Alignment.Top)
                ) {
                    Icon(Icons.Default.DeleteOutline, null, tint = Color.LightGray)
                }
            }
        }
    }
}

@Composable
fun ShippingDetailsScreen(
    name: String,
    phone: String,
    address: String,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onBack: () -> Unit,
    onProceed: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shipping Details") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Where should we send your spices?", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = phone,
                onValueChange = onPhoneChange,
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = address,
                onValueChange = onAddressChange,
                label = { Text("Complete Address") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onProceed,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FlamingoOrange),
                enabled = name.isNotBlank() && phone.isNotBlank() && address.isNotBlank()
            ) {
                Text("REVIEW ORDER", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun OrderSummaryScreen(
    cartItems: List<CartItem>,
    name: String,
    phone: String,
    address: String,
    onBack: () -> Unit,
    onConfirmOrder: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("FlamingoPrefs", Context.MODE_PRIVATE) }

    var upiId by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var cardName by remember { mutableStateOf(name) }
    var cardExpiry by remember { mutableStateOf("") }
    var cardCvv by remember { mutableStateOf("") }
    
    val subtotal = cartItems.sumOf { it.spice.price * it.quantity }
    val shipping = 40
    val total = subtotal + shipping

    // Initialize with saved values
    LaunchedEffect(Unit) {
        if (upiId.isEmpty()) upiId = sharedPrefs.getString("savedUpi", "") ?: ""
        if (cardNumber.isEmpty()) cardNumber = sharedPrefs.getString("savedCard", "") ?: ""
    }

    // Validation
    val isUpiValid = upiId.matches(Regex("^[a-zA-Z0-9.-]{2,256}@[a-zA-Z][a-zA-Z]{2,64}$"))
    val isCardValid = cardNumber.length == 16 && cardExpiry.matches(Regex("(0[1-9]|1[0-2])/[0-9]{2}")) && cardCvv.length == 3

    val onPaymentSuccess = {
        // Save payment methods for future use
        sharedPrefs.edit()
            .putString("savedUpi", upiId)
            .putString("savedCard", cardNumber)
            .apply()
        onConfirmOrder()
    }

    Scaffold(
        containerColor = Color(0xFFFDF7F2),
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Checkout", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                            Icon(Icons.Default.Lock, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(16.dp))
                            Text(" Secure Payment", color = Color.White, fontSize = 12.sp)
                        }
                    }
                },
                navigationIcon = { 
                    IconButton(onClick = onBack) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) 
                    } 
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FlamingoRed)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Order Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (cartItems.isNotEmpty()) {
                        val firstItem = cartItems.first()
                        Row {
                            Image(
                                painter = painterResource(firstItem.spice.imageRes),
                                contentDescription = null,
                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(firstItem.spice.name, fontWeight = FontWeight.Bold)
                                Text("Qty: ${firstItem.quantity}", color = Color.Gray, fontSize = 14.sp)
                                if (cartItems.size > 1) {
                                    Text("+ ${cartItems.size - 1} more items", color = FlamingoOrange, fontSize = 12.sp)
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LocationOn, null, tint = FlamingoRed, modifier = Modifier.size(16.dp))
                                    Column(modifier = Modifier.padding(start = 4.dp).weight(1f)) {
                                        Text(name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(address, color = Color.Gray, fontSize = 10.sp, maxLines = 1)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Change Address", 
                                        color = FlamingoOrange, 
                                        fontSize = 10.sp, 
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(Color(0xFFFFF3E0), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        softWrap = false
                                    )
                                }
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)
                        
                        PriceRow("Subtotal", "₹$subtotal")
                        PriceRow("Shipping", "₹$shipping")
                        PriceRow("Total", "₹$total", isTotal = true)
                    }
                }
            }

            // Payment Method Section
            Row(modifier = Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                Text(" Payment Method", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            // UPI Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("UPI", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, fontStyle = FontStyle.Italic)
                        Text(" (Fast Payment)", color = Color.Gray, fontSize = 12.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Row {
                            repeat(5) { Box(modifier = Modifier.padding(2.dp).size(6.dp).background(FlamingoOrange, CircleShape)) }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = upiId,
                            onValueChange = { upiId = it },
                            placeholder = { Text("example@upi", fontSize = 14.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            isError = upiId.isNotEmpty() && !isUpiValid,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = Color(0xFFF5F5F5),
                                focusedContainerColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onPaymentSuccess,
                            enabled = isUpiValid,
                            colors = ButtonDefaults.buttonColors(containerColor = FlamingoRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Pay ₹$total")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CreditCard, null, tint = FlamingoOrange)
                        Text(" Credit / Debit Card", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                        Spacer(modifier = Modifier.weight(1f))
                        Row {
                             // Placeholders for Visa/Mastercard logos
                             Icon(Icons.Default.Payment, null, modifier = Modifier.size(20.dp), tint = Color.Blue)
                             Spacer(modifier = Modifier.width(4.dp))
                             Icon(Icons.Default.Payment, null, modifier = Modifier.size(20.dp), tint = Color.Red)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = { if (it.length <= 16) cardNumber = it },
                        placeholder = { Text("xxxx xxxx xxxx xxxx") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color(0xFFF5F5F5))
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row {
                        OutlinedTextField(
                            value = cardName,
                            onValueChange = { cardName = it },
                            placeholder = { Text("Name on Card") },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color(0xFFF5F5F5))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = cardExpiry,
                            onValueChange = { if (it.length <= 5) cardExpiry = it },
                            placeholder = { Text("MM/YY") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color(0xFFF5F5F5))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = cardCvv,
                            onValueChange = { if (it.length <= 3) cardCvv = it },
                            placeholder = { Text("***") },
                            modifier = Modifier.weight(0.8f),
                            shape = RoundedCornerShape(8.dp),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color(0xFFF5F5F5))
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = onPaymentSuccess,
                        enabled = isCardValid,
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(containerColor = FlamingoRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Pay Securely")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Delivery Details Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = FlamingoOrange, modifier = Modifier.size(20.dp))
                        Text(" Deliver To", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "Change Address", 
                            color = FlamingoOrange, 
                            fontSize = 12.sp, 
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color(0xFFFFF3E0), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            softWrap = false
                        )
                    }
                    Text(name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    Text(address, color = Color.Gray, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Text(" Payments are encrypted and secure", color = Color.Gray, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildAnnotatedString {
                        this.append("Powered by ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0052CC))) {
                            this.append("Razorpay")
                        }
                    },
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun PriceRow(label: String, value: String, isTotal: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label, 
            color = if (isTotal) Color.Black else Color.Gray, 
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (isTotal) 18.sp else 14.sp
        )
        Text(
            value, 
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (isTotal) 18.sp else 14.sp
        )
    }
}

@Composable
fun OrderSuccessScreen(onContinue: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(initialScale = 0.5f, animationSpec = tween(600)) + fadeIn(animationSpec = tween(600))
            ) {
                Surface(shape = CircleShape, color = Color(0xFFE8F5E9), modifier = Modifier.size(100.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(60.dp).padding(20.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(600, delayMillis = 300)) + slideInVertically(animationSpec = tween(600, delayMillis = 300)) { 20 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Order Placed Successfully!", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Your spices are being packed with love and will reach you soon.", color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                }
            }
            
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp).height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FlamingoOrange)
            ) {
                Text("CONTINUE SHOPPING")
            }
        }
    }
}

@Composable
fun OrdersHistoryScreen(orders: List<Order>, onBack: () -> Unit, onTrackOrder: (Order) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Order History") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        if (orders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No orders yet", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().background(ProfileBg)) {
                items(orders) { order ->
                    OrderItemCard(order, onTrackOrder)
                }
            }
        }
    }
}

@Composable
fun OrderItemCard(order: Order, onTrackOrder: (Order) -> Unit) {
    val context = LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Order #${order.id}", fontWeight = FontWeight.Bold)
                Text(order.status, color = FlamingoOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Text(order.date, color = Color.Gray, fontSize = 12.sp)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)
            
            order.items.forEach { item ->
                Text("${item.quantity}x ${item.spice.name}", fontSize = 14.sp)
            }
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("Total Amount", fontSize = 12.sp, color = Color.Gray)
                    Text("₹${order.total}", fontWeight = FontWeight.Bold, color = FlamingoRed)
                }
                Row {
                    OutlinedButton(
                        onClick = { onTrackOrder(order) }, 
                        modifier = Modifier.height(36.dp), 
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Track", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { 
                            generateAndDownloadInvoice(context, order)
                        }, 
                        modifier = Modifier.height(36.dp), 
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FlamingoOrange),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Invoice", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TrackingScreen(order: Order, onBack: () -> Unit) {
    val steps = listOf("Order Placed", "Order Packed", "Out for Delivery", "Delivered")
    val currentStepIndex = 2 // Simulated current status

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Track Order") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(ProfileBg)
        ) {
            // Simulated Map View
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(Color(0xFFE0E0E0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Map, null, modifier = Modifier.size(80.dp), tint = Color.Gray)
                
                // Simulated Tracking Line/Pin
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.LocationOn, null, tint = FlamingoRed, modifier = Modifier.size(40.dp))
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 4.dp
                    ) {
                        Text(
                            "Courier is 2km away", 
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Status Steps
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Estimated Delivery", color = Color.Gray, fontSize = 14.sp)
                        Text("Today, 6:00 PM", fontWeight = FontWeight.Bold, color = FlamingoRed)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    steps.forEachIndexed { index, step ->
                        TrackingStepItem(
                            title = step,
                            isCompleted = index <= currentStepIndex,
                            isCurrent = index == currentStepIndex,
                            isLast = index == steps.size - 1
                        )
                    }
                }
            }

            // Delivery Personnel Info
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = Color.LightGray, modifier = Modifier.size(50.dp)) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.padding(10.dp), tint = Color.White)
                    }
                    Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                        Text("Suresh Kumar", fontWeight = FontWeight.Bold)
                        Text("Your Delivery Partner", fontSize = 12.sp, color = Color.Gray)
                    }
                    IconButton(
                        onClick = { /* Call */ },
                        modifier = Modifier.background(Color(0xFFE8F5E9), CircleShape)
                    ) {
                        Icon(Icons.Default.Phone, null, tint = Color(0xFF4CAF50))
                    }
                }
            }
        }
    }
}

@Composable
fun TrackingStepItem(title: String, isCompleted: Boolean, isCurrent: Boolean, isLast: Boolean) {
    Row(modifier = Modifier.height(60.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        if (isCompleted) FlamingoRed else Color.LightGray.copy(alpha = 0.5f), 
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp), tint = Color.White)
                }
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(if (isCompleted) FlamingoRed else Color.LightGray.copy(alpha = 0.5f))
                )
            }
        }
        
        Column(modifier = Modifier.padding(start = 16.dp, top = 2.dp)) {
            Text(
                text = title,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                color = if (isCompleted) Color.Black else Color.Gray,
                fontSize = 14.sp
            )
            if (isCurrent) {
                Text("Your order is on the way to your location", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

fun generateAndDownloadInvoice(context: Context, order: Order) {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(300, 600, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas
    val paint = Paint()

    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 12f
    paint.isFakeBoldText = true
    canvas.drawText("FLAMINGO SPICES", 150f, 40f, paint)

    paint.textAlign = Paint.Align.LEFT
    paint.textSize = 8f
    paint.isFakeBoldText = false
    canvas.drawText("Order ID: ${order.id}", 20f, 70f, paint)
    canvas.drawText("Date: ${order.date}", 20f, 85f, paint)
    canvas.drawText("Address: ${order.address}", 20f, 100f, paint)

    canvas.drawLine(20f, 115f, 280f, 115f, paint)

    var y = 135f
    order.items.forEach { item ->
        canvas.drawText("${item.quantity}x ${item.spice.name}", 20f, y, paint)
        canvas.drawText("₹${item.spice.price * item.quantity}", 240f, y, paint)
        y += 15f
    }

    canvas.drawLine(20f, y + 10f, 280f, y + 10f, paint)
    paint.isFakeBoldText = true
    canvas.drawText("Total: ₹${order.total}", 200f, y + 30f, paint)

    pdfDocument.finishPage(page)

    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val fileName = "Invoice_${order.id}.pdf"
    val file = File(downloadsDir, fileName)

    try {
        pdfDocument.writeTo(FileOutputStream(file))
        Toast.makeText(context, "Invoice saved to Downloads", Toast.LENGTH_LONG).show()
    } catch (e: IOException) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to save invoice: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    } finally {
        pdfDocument.close()
    }
}

@Composable
fun AccountScreen(profile: UserProfile, onBack: () -> Unit, onNavigate: (Screen) -> Unit, onLogout: () -> Unit) {
    Scaffold(
        containerColor = Color(0xFFFDF7F2),
        topBar = {
            TopAppBar(
                title = { Text("My Account", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { 
                    IconButton(onClick = onBack) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) 
                    } 
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = FlamingoRed)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Profile Header Card
            Box(modifier = Modifier.fillMaxWidth()) {
                // Red background for the top part
                Box(modifier = Modifier.fillMaxWidth().height(60.dp).background(FlamingoRed))
                
                Card(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 10.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9F3)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile Image with border
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            border = BorderStroke(2.dp, FlamingoOrange),
                            color = Color.LightGray
                        ) {
                            Icon(Icons.Default.Person, null, modifier = Modifier.padding(16.dp), tint = Color.White)
                        }
                        
                        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(profile.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4E342E))
                            Text(profile.email, fontSize = 12.sp, color = Color.Gray)
                        }
                        
                        // Settings Icon
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Color(0xFFFFE0B2),
                            onClick = { /* Settings */ }
                        ) {
                            Icon(
                                Icons.Default.Settings, 
                                null, 
                                modifier = Modifier.padding(8.dp).size(20.dp),
                                tint = Color(0xFF795548)
                            )
                        }
                    }
                }
            }

            // Menu List
            Column(modifier = Modifier.padding(16.dp)) {
                AccountMenuItem(
                    title = "My Orders",
                    subtitle = "View order history and status",
                    icon = Icons.Default.ShoppingBag,
                    badgeText = "3 Pending",
                    badgeColor = FlamingoRed,
                    onClick = { onNavigate(Screen.Orders) }
                )
                
                AccountMenuItem(
                    title = "Shipping Addresses",
                    subtitle = "Manage your delivery addresses",
                    icon = Icons.Default.LocalShipping,
                    onClick = { }
                )
                
                AccountMenuItem(
                    title = "Payment Methods",
                    subtitle = "View and manage your cards",
                    icon = Icons.Default.CreditCard,
                    onClick = { }
                )
                
                AccountMenuItem(
                    title = "Wishlist",
                    subtitle = "Your favorite spices in one place",
                    icon = Icons.Default.Favorite,
                    badgeText = "5 items",
                    badgeColor = Color(0xFFFFF3E0),
                    badgeTextColor = Color(0xFFE65100),
                    onClick = { }
                )
                
                AccountMenuItem(
                    title = "Offers",
                    subtitle = "Best deals on premium spices",
                    icon = Icons.Default.CardGiftcard,
                    badgeText = "NEW",
                    badgeColor = Color(0xFFE8F5E9),
                    badgeTextColor = Color(0xFF2E7D32),
                    onClick = { onNavigate(Screen.Offers) }
                )
                
                AccountMenuItem(
                    title = "Notifications",
                    icon = Icons.Default.Notifications,
                    badgeText = "2",
                    badgeColor = FlamingoRed,
                    onClick = { }
                )
                
                AccountMenuItem(
                    title = "Help Center",
                    icon = Icons.Default.Help,
                    onClick = { onNavigate(Screen.HelpCenter) }
                )
                
                AccountMenuItem(
                    title = "Refer & Earn",
                    icon = Icons.Default.Campaign,
                    onClick = { }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Footer
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, null, tint = Color(0xFF8D6E63), modifier = Modifier.size(14.dp))
                        Text(" Payments are encrypted and secure", color = Color(0xFF8D6E63), fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildAnnotatedString {
                            append("Powered by ")
                            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0052CC))) {
                                append("Razorpay")
                            }
                        },
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Logout Button
                TextButton(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = FlamingoRed)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AccountMenuItem(
    title: String, 
    icon: ImageVector, 
    subtitle: String? = null,
    badgeText: String? = null, 
    badgeColor: Color = FlamingoOrange,
    badgeTextColor: Color = Color.White,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Background
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFDF7F2)
            ) {
                Icon(
                    icon, 
                    null, 
                    modifier = Modifier.padding(8.dp),
                    tint = FlamingoOrange
                )
            }
            
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF4E342E))
                if (subtitle != null) {
                    Text(subtitle, fontSize = 11.sp, color = Color.Gray)
                }
            }
            
            if (badgeText != null) {
                Surface(
                    color = badgeColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        badgeText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        color = badgeTextColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun HelpCenterScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = ProfileBg,
        topBar = {
            TopAppBar(
                title = { Text("Help & Support") },
                navigationIcon = { 
                    IconButton(onClick = onBack) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null) 
                    } 
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("How can we help you?", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = FlamingoRed)
            Spacer(modifier = Modifier.height(24.dp))
            
            HelpItem("Track my order", Icons.Default.LocalShipping)
            HelpItem("Return/Refund policy", Icons.AutoMirrored.Filled.AssignmentReturn)
            HelpItem("Payment issues", Icons.Default.Payment)
            HelpItem("Account security", Icons.Default.Security)
            HelpItem("Contact us", Icons.AutoMirrored.Filled.Chat)
            
            Spacer(modifier = Modifier.height(32.dp))
            Text("Frequently Asked Questions", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            FAQItem("Are the spices organic?", "Yes, all our spices are 100% pure, organic, and locally sourced from verified Indian farms.")
            FAQItem("How long does delivery take?", "Standard delivery takes 3-5 business days. Express delivery is available in select cities.")
            FAQItem("What is the shelf life?", "Our spices are freshly packed and typically have a shelf life of 12-18 months if stored in a cool, dry place.")
        }
    }
}

@Composable
fun HelpItem(title: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = FlamingoOrange)
            Text(title, modifier = Modifier.padding(start = 16.dp), fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
        }
    }
}

@Composable
fun FAQItem(question: String, answer: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(question, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(answer, color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        HorizontalDivider(modifier = Modifier.padding(top = 12.dp), color = Color.LightGray, thickness = 0.5.dp)
    }
}

@Composable
fun AIChatScreen(onBack: () -> Unit, onAddToCart: (Spice) -> Unit) {
    var messageText by remember { mutableStateOf("") }
    val chatMessages = remember {
        mutableStateListOf(
            ChatMessage("Hello! I'm your Flamingo Spice Expert. Ask me anything about spices, recipes, or health benefits!", false)
        )
    }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Spice Expert AI", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Online | Ready to help", fontSize = 12.sp, color = Color.Gray)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = Color.White) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .navigationBarsPadding()
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Ask about spices...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = Color(0xFFF5F5F5),
                            focusedContainerColor = Color(0xFFF5F5F5),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = FlamingoOrange
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                val userMsg = messageText
                                chatMessages.add(ChatMessage(userMsg, true))
                                messageText = ""
                                
                                // Simulate AI Response
                                coroutineScope.launch {
                                    listState.animateScrollToItem(chatMessages.size - 1)
                                    kotlinx.coroutines.delay(1000)
                                    val response = getSpiceAIResponse(userMsg)
                                    val suggestedSpice = findSpiceInInput(userMsg) ?: findSpiceInInput(response)
                                    chatMessages.add(ChatMessage(response, false, suggestedSpice))
                                    listState.animateScrollToItem(chatMessages.size - 1)
                                }
                            }
                        },
                        containerColor = FlamingoRed,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(ProfileBg),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(chatMessages) { message ->
                ChatBubble(message, onAddToCart)
            }
        }
    }
}

data class ChatMessage(val text: String, val isUser: Boolean, val spice: Spice? = null)

@Composable
fun ChatBubble(message: ChatMessage, onAddToCart: (Spice) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = if (message.isUser) FlamingoRed else Color.White,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isUser) 16.dp else 0.dp,
                bottomEnd = if (message.isUser) 0.dp else 16.dp
            ),
            shadowElevation = if (message.isUser) 2.dp else 1.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = message.text,
                    color = if (message.isUser) Color.White else Color.Black,
                    fontSize = 14.sp
                )
                
                message.spice?.let { spice ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onAddToCart(spice) },
                        colors = ButtonDefaults.buttonColors(containerColor = FlamingoOrange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.AddShoppingCart, null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add ${spice.name} to Cart", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

fun findSpiceInInput(input: String): Spice? {
    val lower = input.lowercase()
    return allSpices.find { it.name.lowercase() in lower }
}

fun getSpiceAIResponse(input: String): String {
    val lowerInput = input.lowercase()
    return when {
        lowerInput.contains("spicy") || lowerInput.contains("hot") -> 
            "For a perfectly Spicy outcome: Use 2 parts Guntur Chilli for heat and 1 part Kashmiri Chilli for vibrant color. Add 0.5 parts Black Pepper for a sharp kick."
        lowerInput.contains("mild") || lowerInput.contains("less spice") -> 
            "For a Mild & Flavorful outcome: Stick to 3 parts Kashmiri Chilli (very low heat) and 1 part Turmeric. Avoid Guntur Chilli and Black Pepper."
        lowerInput.contains("curry") || lowerInput.contains("authentic") -> 
            "For an Authentic Indian Curry: The magic ratio is 2 parts Turmeric, 3 parts Coriander Powder, and 1 part Garam Masala. Sauté these in oil before adding vegetables/meat."
        lowerInput.contains("recommend") || lowerInput.contains("best") -> 
            "For a rich, aromatic flavor, I highly recommend our Flamingo Turmeric and Kashmiri Chilli combo. They are our best sellers!"
        lowerInput.contains("turmeric") -> 
            "Turmeric is known for its anti-inflammatory properties. Our Turmeric is 100% organic and contains high curcumin content."
        lowerInput.contains("chilli") -> 
            "If you like it spicy but with great color, try our Guntur Chilli powder. For mild heat and vibrant red, go for Kashmiri Chilli."
        lowerInput.contains("biryani") || lowerInput.contains("recipe") -> 
            "For a perfect Biryani, you need a blend of Shahi Jeera, Star Anise, and our special Garam Masala. Would you like to see these items?"
        lowerInput.contains("hello") || lowerInput.contains("hi") -> 
            "Hi there! How can I help you spice up your cooking today? Tell me if you want something Spicy, Mild, or an Authentic Curry!"
        lowerInput.contains("benefit") || lowerInput.contains("health") -> 
            "Spices like Ginger, Turmeric, and Black Pepper are great for immunity. Many of our customers use them for their health benefits!"
        else -> "That's an interesting question! As a spice expert, I can tell you that the right blend can transform any dish. Do you have a specific flavor profile (Spicy, Mild, Authentic) in mind?"
    }
}

@Composable
fun OffersScreen(onBack: () -> Unit) {
    val offers = listOf(
        Offer("FLAMINGO50", "Flat ₹50 OFF", "On orders above ₹500", "Valid till 31st Oct", Color(0xFFE3F2FD), Color(0xFF1976D2)),
        Offer("WELCOME100", "Flat ₹100 OFF", "On your first spice kit purchase", "First time users only", Color(0xFFF1F8E9), Color(0xFF388E3C)),
        Offer("SPICEITUP", "20% Discount", "On all organic masalas", "Special Weekend Deal", Color(0xFFFFF3E0), Color(0xFFE64A19)),
        Offer("FREESHIP", "FREE Delivery", "No minimum order value", "Limited time offer", Color(0xFFF3E5F5), Color(0xFF7B1FA2))
    )

    Scaffold(
        containerColor = ProfileBg,
        topBar = {
            TopAppBar(
                title = { Text("Exclusive Offers", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = FlamingoRed)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(offers) { offer ->
                OfferCard(offer)
            }
        }
    }
}

data class Offer(val code: String, val title: String, val desc: String, val validity: String, val bgColor: Color, val textColor: Color)

@Composable
fun OfferCard(offer: Offer) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(600)) + slideInVertically(animationSpec = tween(600)) { 20 }
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = offer.bgColor),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(offer.title, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = offer.textColor)
                    Text(offer.desc, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                    Text(offer.validity, fontSize = 11.sp, color = Color.Gray.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        color = Color.White.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, offer.textColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            offer.code,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontWeight = FontWeight.Bold,
                            color = offer.textColor,
                            letterSpacing = 1.sp
                        )
                    }
                    TextButton(onClick = { /* Copy Code */ }) {
                        Text("Copy Code", color = offer.textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AppSplashScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        onTimeout()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(FlamingoOrange, FlamingoRed))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Spa, null, modifier = Modifier.size(100.dp), tint = Color.White)
            Text("FLAMINGO SPICES", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 16.dp))
            Text("Pure • Organic • Aromatic", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (String, Boolean) -> Unit, onNavigateToRegister: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(800)) + slideInVertically(animationSpec = tween(800)) { it / 4 },
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Welcome Back", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = FlamingoRed)
                Text("Sign in to continue shopping", color = Color.Gray)
                
                Spacer(modifier = Modifier.height(32.dp))
                
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    shape = RoundedCornerShape(12.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp)
                )
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(checked = rememberMe, onCheckedChange = { rememberMe = it }, colors = CheckboxDefaults.colors(checkedColor = FlamingoOrange))
                    Text("Remember Me")
                }
                
                Button(
                    onClick = { onLoginSuccess(email, rememberMe) },
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FlamingoOrange)
                ) {
                    Text("LOGIN", fontWeight = FontWeight.Bold)
                }
                
                Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.Center) {
                    Text("Don't have an account? ")
                    Text(
                        "Register", 
                        color = FlamingoOrange, 
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onNavigateToRegister() }
                    )
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(onRegisterSuccess: (String, String, String) -> Unit, onNavigateToLogin: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(800)) + slideInHorizontally(animationSpec = tween(800)) { it / 4 }
    ) {
        Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
            Text("Create Account", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = FlamingoRed)
            Text("Join the Flamingo Spices family", color = Color.Gray)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Person, null) },
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Email, null) },
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Phone, null) },
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp)
            )
            
            Button(
                onClick = { onRegisterSuccess(name, email, phone) },
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 32.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FlamingoOrange)
            ) {
                Text("REGISTER", fontWeight = FontWeight.Bold)
            }
            
            Row(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalArrangement = Arrangement.Center) {
                Text("Already have an account? ")
                Text(
                    "Login", 
                    color = FlamingoOrange, 
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToLogin() }
                )
            }
        }
    }
}
