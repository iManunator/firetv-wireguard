# WireGuard TV

A WireGuard client built for a Fire TV remote. The stick has no camera and no usable file picker, so the usual WireGuard app is awkward there. This one shows a QR code: scan it with your phone, upload the `.conf`, and connect.

<p>
  <a href="https://ko-fi.com/imanunator"><img alt="Buy me a coffee" src="https://ko-fi.com/img/githubbutton_sm.svg"></a>
</p>

If it saves you some hassle, you can [buy me a coffee](https://ko-fi.com/imanunator).

## What it does

- **Import from your phone.** The TV runs a small page on your Wi-Fi. The QR code opens it. Paste the config or pick the file. Broken configs are rejected before they are saved.
- **Remote-friendly screen.** One Connect button, plus server, address, traffic, and handshake age. The focused button has a white outline.
- **Split tunneling.** Send every app through the VPN, leave some apps out, or send only the ones you pick.
- **Auto-connect.** Comes back up after a reboot, and after the app updates. Fire OS blocks a background app from starting a VPN, so boot uses a short foreground service.
- **Old sticks.** Fire OS 5 through 8 (Android 5.1 through 11): Fire TV Stick, Fire TV box, Cube, and Fire TV Edition sets from 2014 onward. The APK includes the WireGuard engine for every CPU those devices use.

Vega OS devices, such as the Fire TV Stick 4K Select (2025), cannot run Android apps, so this cannot run there.

## Install

Download `WireGuardTV-1.0.0.apk` from the [releases](https://github.com/iManunator/firetv-wireguard/releases) page.

On the Fire TV, allow apps from unknown sources: **Settings → My Fire TV → Developer options → Install unknown apps**, and enable it for the app you install with (Downloader, or adb).

**Downloader**

1. Install **Downloader** from the Amazon Appstore.
2. Open the release page on a computer and copy the APK link, or host the file on your network.
3. Paste the address in Downloader and install.

**adb**

```bash
adb connect <fire-tv-ip>:5555
adb install -r WireGuardTV-1.0.0.apk
```

Turn on ADB debugging under **Settings → My Fire TV → Developer options**. If that menu is missing, open **Settings → My Fire TV → About** and click the device name seven times.

The first time you press **Connect**, Android asks for VPN permission. Choose **OK**. That prompt appears only once. After that, auto-connect can bring the tunnel up on its own.

Only one VPN can run at a time. Turn off any other WireGuard or VPN app first.

## Using it

1. **Import config.** Connect the phone to the same Wi-Fi as the Fire TV. On the TV, open **Import config** and scan the QR code. Send the `.conf`.
2. **Connect.** The screen shows the server, the tunnel address, traffic, and how long ago the last handshake was. "Idle" means nothing has sent traffic yet. WireGuard only talks to the server when something does, unless your config sets `PersistentKeepalive`.
3. **Split tunneling.** "All apps" routes everything that the config's `AllowedIPs` covers. "Exclude selected" lets the checked apps bypass the VPN. "Only selected" sends just those apps through it. Changes apply immediately if the tunnel is already up.
4. **Auto-connect on boot** is on by default. The button on the main screen turns it off.

The filename does not matter, and you do not type the keys with the remote.

## At home and away

The app installs exactly the routes in your config. It does not change `AllowedIPs`.

If those addresses are devices on your own LAN, and the WireGuard server is also at home, the Fire TV will try to reach them through the tunnel even while it is sitting on that LAN. That only works when the server forwards traffic onward (IP forwarding and NAT on the WireGuard interface, or a route on the router for the tunnel subnet). Otherwise those devices stop answering while the tunnel is up. Away from home, the same forwarding is what makes them reachable.

## Privacy

The `.conf`, including the private key, is stored in the app's private storage. Android backup is off. The import page is plain HTTP, bound to the TV's LAN address, and it only runs while the import screen is open. The link contains a one-time token; a wrong token is rejected. Import from a network you trust, then leave the screen. The key is never written to the release, the screenshots, or this repository.

## Build

Android Studio, or a JDK 17+ and the Android SDK.

```bash
# keystore.properties is local and is not in git. See below.
./gradlew assembleRelease
```

The release APK is `app/build/outputs/apk/release/app-release.apk`.

Signing uses `keystore.properties` in the project root (gitignored):

```properties
storeFile=keystore/release.jks
storePassword=...
keyAlias=firetv
keyPassword=...
```

Create the key once with `keytool -genkeypair`. Keep `keystore/release.jks`. An APK signed with another key cannot update an installed copy; Android will ask you to uninstall first, which deletes the imported config.

`minSdk` is 22. `compileSdk` and `targetSdk` are 35.

## Thanks

The tunnel itself is the [WireGuard Android](https://github.com/WireGuard/wireguard-android) `tunnel` library (Apache-2.0). QR codes use ZXing.

## License

Source in this repository is private. The WireGuard tunnel library keeps its own Apache-2.0 license.
