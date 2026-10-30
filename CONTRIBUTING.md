# Contributing to Travel Diary

Thanks for helping! Bug reports, ideas, fixes and new features are all welcome. This page explains
how to propose a change so that it can be reviewed and merged quickly.

## Before you start

- **Bugs**: open an issue with the app version (Settings > About), your Android version and the
  steps to reproduce the problem. A screenshot helps for anything visual.
- **Small fixes** (typos, crashes, obvious bugs): open a pull request directly.
- **New features or bigger changes**: open an issue first and describe what you have in mind, so
  we can agree on the approach before you spend time on it.

## What the app stands for

Every change should keep these promises, which are also made in the README and in the app:

- **Private**: no account, no ads, no analytics, no crash reporting services. The diary never
  leaves the phone unless the user exports it.
- **Offline**: everything works without internet. The only network access is downloading a bigger
  city list, and only when the user asks for it. Do not add new permissions or network calls
  without discussing them in an issue first.
- **Small and simple**: prefer the Android and Jetpack libraries already in use over new
  dependencies. If you need a new library, explain why in the pull request.

## Setting up

Follow the [Development](README.md#development) section of the README: you need JDK 17 or newer
(21 recommended) and the Android SDK. Android Studio installs everything you need.

```sh
./gradlew assembleDebug        # build the app
./gradlew testDebugUnitTest    # run the unit tests
./gradlew lintDebug            # run Android lint
```

The [Build workflow](.github/workflows/build.yml) runs the same three commands on every pull
request, so please run them locally before pushing.

## Writing code

- **Kotlin and Jetpack Compose**, following the
  [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) with 4-space
  indentation and trailing commas. Keep lines readable: most of the code stays under 120
  characters.
- **Match the code around you**: one package per screen under `ui/`, a `ViewModel` exposing
  `StateFlow`s, screens split into a `XxxScreen` that reads the view model and a stateless
  `XxxScreenContent`. Objects shared by several screens are created in `AppContainer`.
- **Comments explain why**, not what. A short KDoc on public classes and functions is enough.
- **Text shown to the user** goes in `app/src/main/res/values/strings.xml`, never hard-coded.
  Write it in plain, friendly English (the app is English only for now; translations are
  welcome as a separate pull request).
- **Tests**: add or update unit tests in `app/src/test` for any logic you add or change
  (parsers, statistics, map geometry, repositories). Database code is tested with Robolectric.
- **Database changes**: if you change a Room entity, bump the database version, add a migration
  and commit the updated schema in `app/schemas`. Users must never lose their diary on update.

## Bundled data

The files in `app/src/main/assets` are generated, do not edit them by hand:

- map shapes, countries and cities: `tools/build_assets.py` (see the README);
- flag images: `tools/build_flags.sh`, which needs `rsvg-convert` and ImageMagick.

If you change one of these scripts, commit the regenerated files in the same pull request.
New data or images must come with a license compatible with the GPL (public domain, CC0,
CC BY, MIT, Apache 2.0 and similar); credit them in the README and in Settings > About.

## Pull requests

- Create a branch from `main` and keep each pull request about one thing.
- Write commit messages in the imperative, with a short first line: "Add flag colours to the
  world map", "Fix crash when importing an empty backup".
- In the description, explain what changes for the user and why. Add screenshots or a short
  video for anything visual, in both the light and the dark theme.
- Update the README when you add a feature or change how something works.

Releases are tagged and published by the maintainers, see [Releasing](README.md#releasing).

## License

Travel Diary is free software released under the [GNU General Public License, version 3 or
later](LICENSE). By submitting a contribution you agree that it is licensed under the same terms,
and you confirm that you have the right to do so (it is your own work, or it comes from a source
with a compatible license that you name in the pull request).
