def mapboxDownloadsToken = project.findProperty("MAPBOX_DOWNLOADS_TOKEN") ?: System.getenv("MAPBOX_DOWNLOADS_TOKEN")

if (!mapboxDownloadsToken && project.hasProperty("cdvHelpers")) {
    mapboxDownloadsToken = cdvHelpers.getConfigPreference("MAPBOX_DOWNLOADS_TOKEN", "")
}

repositories {
    google()
    mavenCentral()
    maven {
        url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
        authentication {
            basic(BasicAuthentication)
        }
        credentials {
            username = "mapbox"
            password = mapboxDownloadsToken ?: ""
        }
    }
}

android {
    defaultConfig {
        minSdkVersion 23
    }
}

dependencies {
    implementation "androidx.annotation:annotation:1.8.2"
    implementation "com.mapbox.maps:android-ndk27:$MAPBOX_SDK_VERSION"
}
