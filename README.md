# NCKitConfig

Android library that exposes a single public API:

- `NoiceClear(context, inputFile, outputFile, attenLimDb, postFilterBeta)`

It uses `com.fiveexceptions.nckit:nckit` internally to load the model and denoise audio from
audio/video input files into a WAV output file.

## Publish

Set credentials in `gradle.properties` or environment variables:

- `gpr.user` / `GPR_USER`
- `gpr.key` / `GPR_KEY`

Then publish:

```bash
./gradlew :app:publishReleasePublicationToGitHubPackagesRepository
```

For local verification:

```bash
./gradlew :app:publishReleasePublicationToMavenLocal
```
