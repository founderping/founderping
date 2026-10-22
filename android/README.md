# FounderPing for Android

The FounderPing feedback widget inside your Android app: the same widget that
runs on the web, in a transparent WebView over each of your activities. It
depends on nothing but the Android framework and the Kotlin standard library.

Adding it to your app, every call, and what to check when something goes wrong
are in the [Android integration guide](../skill/doc/android.md), also published
at <https://founderping.app/doc/android.md>. The short version:

```kotlin
// app/build.gradle.kts
implementation("app.founderping:widget:0.1.0")
```

```xml
<!-- AndroidManifest.xml, inside <application>: the library starts itself from this -->
<meta-data android:name="app.founderping.PROJECT_KEY" android:value="pk_…" />
```

```kotlin
// Then, wherever the moment happens:
FounderPing.mount("post_checkout")
```

How each part works, and why, is written up in the source, file by file.

## Building

```
./gradlew :founderping:assembleRelease :founderping:test
./gradlew :founderping:publishToMavenLocal   # to consume it from another build
```

Needs JDK 17 and an Android SDK with API 34.

On an arm64 Linux host, add `-Pandroid.aapt2FromMavenOverride=/usr/bin/aapt2`
(from Debian/Ubuntu's `aapt` package). Google publishes `aapt2` for x86-64 only,
so without this every resource task fails with an exec format error.

## Publishing

`./publish.sh` tests, builds and signs the library, then uploads it to Maven
Central as `app.founderping:widget`, at the version in
`founderping/build.gradle.kts`. The Portal validates the upload first, and the
script asks before making it public, because a published version can never be
changed.

It needs GnuPG, which does the signing, and reads two gitignored files next to
it: `publish.env`, copied from `publish.env.example`, which explains each
value, and `signing-key.asc`.
