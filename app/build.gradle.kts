import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Chave de release fora do Git: assinatura.properties (modelo em assinatura.properties.exemplo)
val propriedadesAssinatura = Properties().apply {
    val arquivo = rootProject.file("assinatura.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}
val arquivoChave = rootProject.file(propriedadesAssinatura.getProperty("arquivo", "assinatura-iptv.jks"))
val temChave = arquivoChave.exists() && propriedadesAssinatura.getProperty("senha") != null
if (!temChave) {
    // Sem a chave, o release sai com a de debug e outro id (.dev): instala ao lado do app assinado
    // e nunca tenta sobrescreve-lo, o que obrigaria a desinstalar e apagaria login e progresso
    logger.warn("CineTV: sem assinatura.properties ou chave; release assinado com a chave de debug como br.projetor.cinetv.dev")
}

android {
    namespace = "br.projetor.cinetv"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.projetor.cinetv"
        minSdk = 28
        targetSdk = 30
        versionCode = 1
        versionName = "0.1.0"
        // O projetor e 32 bits; o emulador x86 roda o mesmo APK (nao ha codigo nativo proprio)
        ndk { abiFilters += listOf("armeabi-v7a", "x86") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (temChave) {
            create("release") {
                storeFile = arquivoChave
                storePassword = propriedadesAssinatura.getProperty("senha")
                keyAlias = propriedadesAssinatura.getProperty("apelido", "cinetv")
                keyPassword = propriedadesAssinatura.getProperty("senha_apelido", propriedadesAssinatura.getProperty("senha"))
            }
        }
    }

    buildTypes {
        release {
            // R8 pesa muito no desempenho do Compose em aparelho fraco
            isMinifyEnabled = true
            isShrinkResources = true
            if (temChave) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
                applicationIdSuffix = ".dev"
                versionNameSuffix = "-dev"
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // O Registro escreve no android.util.Log, que nos testes JVM e so um esboco
    testOptions { unitTests.isReturnDefaultValues = true }
    // App pessoal, fora da Play Store; o alvo e o Android 11 do projetor
    lint { disable += "ExpiredTargetSdkVersion" }
}

dependencies {
    val bomCompose = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(bomCompose)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.media3:media3-exoplayer:1.6.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.6.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.6.1")
    implementation("androidx.media3:media3-ui:1.6.1")

    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    implementation("androidx.room:room-paging:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.paging:paging-compose:3.3.6")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
