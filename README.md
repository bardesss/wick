# Wick

Wick is a minimal, dark RSS reader for Android. It shows titles only. Scrolling past an item marks it read, swiping right shows the feed's text, swiping left toggles read, and a tap opens the article in your browser. There are no notifications.

<img src="art/list.png" width="320" alt="The Wick article list">

## Requirements

Works with any server that offers the Google Reader API. Tested with CommaFeed.

You need your API key (in CommaFeed: Profile).

## Install

Wick is released as a signed APK on GitHub. The easiest way to install and update it is [Obtainium](https://obtainium.imranr.dev/): add an app and enter the repo URL.

```
https://github.com/bardesss/wick
```

## First run

Enter your server address, your username and your API key. Wick then loads your unread items.

## Building from source

You need JDK 21 and the Android SDK.

```
./gradlew :app:assembleDebug
```

## License

GPL-3.0. See [LICENSE](LICENSE).
