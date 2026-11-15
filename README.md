# Bootmark

Change your Samsung boot animation with root. Use the Galaxy preset with “Powered by KernelSU”, or import an image or GIF.

[Download](https://github.com/ArduinoQuack/Bootmark/releases/latest)

## Usage

1. Install the APK and allow root access.
2. Choose Galaxy or import your own image.
3. Tap **Apply**. The animation changes on your next normal restart.

Tap the preview to view it full screen. **Restore original** removes the customization. Restore before uninstalling or clearing app data.

The app follows your phone's light and dark theme and accent color. It has no background service, Internet permission or analytics.

## Compatibility

Currently supports the **Galaxy S24+ SM-S926U on S926USQU5DZDR**. Other devices and firmware are blocked by the installer.

Bootmark changes Samsung's later boot animation. It does not replace the first Samsung or Knox splash screens, unlock the bootloader, or keep temporary root active after a reboot. Root is required to apply and restore; the animation itself does not need the app running.

## Custom images

PNG, JPEG, WebP and GIF are supported. Output is **1440 × 3120**. Other sizes are fitted onto a black background without stretching.

GIFs can be up to 6 seconds long and are converted at 12 fps. The input file limit is 20 MB. Importing a file does not apply it.

## Build

See [BUILDING.md](docs/BUILDING.md). Release APKs use a private signing key, which is not included.

MIT license for the code. Samsung and KernelSU names and artwork belong to their respective owners. This is an independent project.
