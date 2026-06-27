# FlamingoXP

A modern Android application built with **Kotlin** and **Jetpack Compose** for location-based API interactions and real-time data management.

## 📱 Features

- **Modern UI**: Built with Jetpack Compose for a responsive and intuitive user interface
- **Location Services**: Access device location with fine and coarse location permissions
- **Firebase Integration**: Real-time database and authentication support via Firebase
- **Material Design 3**: Contemporary Material 3 design system implementation
- **Network Connectivity**: Check and manage network state for reliable API calls
- **Compose-based Navigation**: Smooth navigation and state management

## 🛠️ Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Build System**: Gradle (Kotlin DSL)

### Key Dependencies

- **Jetpack Libraries**:
  - `androidx.core:core-ktx` - Android core extensions
  - `androidx.lifecycle:lifecycle-runtime-ktx` - Lifecycle management
  - `androidx.activity:activity-compose` - Compose integration
  - `androidx.compose.material3` - Material Design 3 components
  - `androidx.constraintlayout` - Constraint-based layouts
  - `androidx.cardview` - Card components

- **Firebase**:
  - Firebase Firestore - Real-time cloud database
  - Firebase Authentication - User authentication

- **Location Services**:
  - Google Play Services Location - GPS and location APIs

- **Testing**:
  - JUnit - Unit testing
  - Espresso - UI testing
  - Compose Testing - Compose-specific UI tests

## 🚀 Getting Started

### Prerequisites

- Android Studio (latest version recommended)
- JDK 11 or higher
- Android SDK 35 (API level 35)

### Installation

1. **Clone the repository**:
   ```bash
   git clone https://github.com/ifeelaryan/FlamingoXP.git
   cd FlamingoXP
   ```

2. **Open in Android Studio**:
   - Launch Android Studio
   - Select "Open an existing Android Studio project"
   - Navigate to the FlamingoXP directory

3. **Build the project**:
   ```bash
   ./gradlew build
   ```

4. **Run the app**:
   ```bash
   ./gradlew installDebug
   ```
   Or use Android Studio's Run button.

## 📋 Project Structure

```
FlamingoXP/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/api_interface/    # Kotlin source code
│   │   │   ├── res/                                # Resources (layouts, strings, etc.)
│   │   │   ├── AndroidManifest.xml
│   │   │   └── ic_launcher-playstore.png
│   │   ├── test/                                   # Unit tests
│   │   └── androidTest/                            # Instrumented tests
│   ├── build.gradle.kts                            # App module dependencies
│   └── proguard-rules.pro                          # ProGuard rules
├── gradle/                                         # Gradle wrapper files
├── build.gradle.kts                                # Root build configuration
├── settings.gradle.kts                             # Project settings
└── gradle.properties                               # Gradle properties
```

## 📝 Permissions

The app requires the following permissions (declared in `AndroidManifest.xml`):

- `android.permission.INTERNET` - For network communication
- `android.permission.ACCESS_NETWORK_STATE` - To check network connectivity
- `android.permission.ACCESS_FINE_LOCATION` - For precise GPS location
- `android.permission.ACCESS_COARSE_LOCATION` - For approximate location

## 🔐 Firebase Setup

> **Note**: Google Services configuration (`google-services.json`) is currently not included. To enable Firebase features:

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project or select an existing one
3. Add an Android app to your Firebase project
4. Download `google-services.json`
5. Place it in the `app/` directory
6. Uncomment the Google Services plugin in `app/build.gradle.kts`:
   ```gradle
   alias(libs.plugins.google.services)
   ```

## 🧪 Testing

### Run Unit Tests
```bash
./gradlew test
```

### Run Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

## 🏗️ Build Configuration

- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 15)
- **Compile SDK**: 36
- **Java Compatibility**: Java 11

## 📦 Build & Release

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

ProGuard is configured for release builds to optimize and obfuscate the code.

## 🤝 Contributing

Contributions are welcome! Feel free to:
- Report bugs
- Suggest new features
- Submit pull requests

## 📄 License

This project is open source and available under the MIT License (or specify your chosen license).

## 👤 Author

[ifeelaryan](https://github.com/ifeelaryan)

## 📧 Support

For questions or issues, please open an issue on the [GitHub repository](https://github.com/ifeelaryan/FlamingoXP/issues).

---

**Happy coding! 🚀**
