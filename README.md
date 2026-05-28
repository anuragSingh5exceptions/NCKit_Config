# NCKitConfig

Android library that exposes a single public API:

- `NoiceClear(context, inputFile, outputFile, attenLimDb, postFilterBeta)`

It uses `com.fiveexceptions.nckit:nckit` internally to load the model and denoise audio from
audio/video input files into a WAV output file.

## GitHub Packages Publishing

This project is configured to publish `com.example.nckitconfig:nckitconfig` to GitHub Packages.

### 1) Set required credentials

Add these to `~/.gradle/gradle.properties` (recommended):

- `gpr.user` (GitHub username)
- `gpr.key` (GitHub PAT)

Example:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_GITHUB_PAT
```

### 2) Publish

```bash
./gradlew :app:publishReleasePublicationToGitHubPackagesRepository
```

If a version already exists (HTTP 409 conflict), publish with a new version:

```bash
./gradlew :app:publishReleasePublicationToGitHubPackagesRepository "-PlibVersion=1.0.2"
```

### 3) Local verification

```bash
./gradlew :app:publishReleasePublicationToMavenLocal
```
