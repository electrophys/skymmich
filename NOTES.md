# Skylight D106 Ethernet Adapter Notes

The Skylight D106's WiFi chip (BCM43456) is dead — it crashes on init. Network connectivity is provided by a USB Ethernet adapter (Realtek RTL8152) connected to the single USB-C port.

## The USB Problem

The frame has one USB port. It can either be in **device mode** (ADB) or **host mode** (Ethernet adapter). You cannot have both at the same time. The workflow is:

1. Plug in USB cable → ADB works, no network
2. Deploy APK via `adb install`
3. Unplug USB cable, plug in Ethernet adapter
4. Reboot the frame

## Boot Script: eth_up.sh

The script lives at `/data/local/tmp/eth_up.sh` and is run by the skymmich app on startup (via `su`). It does not rely on Android's init system.

```sh
#!/system/bin/sh
setprop sys.usb.config none
sleep 1

i=0
while [ $i -lt 60 ]; do
    if [ -d /sys/class/net/eth0 ]; then
        ifconfig eth0 up
        dhcptool eth0
        sleep 1
        ndc network create 100
        ndc network interface add 100 eth0
        ndc network route add 100 eth0 0.0.0.0/0 192.168.86.1
        ndc resolver setnetdns 100 '' 192.168.86.1 8.8.8.8
        ndc network default set 100
        setprop net.dns1 192.168.86.1
        setprop net.dns2 8.8.8.8
        setprop service.adb.tcp.port 5555
        stop adbd
        start adbd
        break
    fi
    sleep 1
    i=$((i + 1))
done
```

### What it does

1. **`setprop sys.usb.config none`** — Switches USB from device mode (ADB) to host/OTG mode so the Ethernet adapter is recognized as a USB peripheral. This kills ADB.
2. **Waits for eth0** — Polls `/sys/class/net/eth0` every second for up to 60 seconds until the RTL8152 driver creates the interface.
3. **DHCP** — `dhcptool eth0` obtains an IP address from the router.
4. **Network routing via ndc** — Android's netd daemon manages the network stack. The `ndc` commands create a network, assign the interface, set the default route, and configure DNS.
5. **DNS properties** — `setprop net.dns1/dns2` ensures apps using the system resolver can find DNS servers.
6. **ADB over TCP** — `setprop service.adb.tcp.port 5555` + `stop/start adbd` enables network ADB after USB host mode is active (correct ordering).

### How the app runs it

`MainActivity.bringUpNetwork()` checks for active non-loopback interfaces. If none are found, it launches `su` via `Runtime.getRuntime().exec("su")`, writes the script command to stdin, then **polls for a live IPv4 address every 250ms**. `onNetworkReady()` fires as soon as DHCP completes — the app no longer waits for the full script to finish. This reduces startup lag from ~15-20s to ~3-5s.

## Adapting to a Different Network

Edit the script to match your network. The values you'll need to change:

| Value | Current | What it is |
|---|---|---|
| `192.168.86.1` (route) | Gateway/router IP | Default route for outbound traffic |
| `192.168.86.1` (DNS) | Router as DNS | Primary DNS — must resolve local hostnames |
| `8.8.8.8` (DNS) | Google DNS | Fallback for public hostnames |

If your Immich server uses a public hostname or IP that Google DNS can resolve, you can use `8.8.8.8 8.8.4.4` and skip the local DNS.

## Init System (does NOT work)

There is a file at `/system/etc/init/eth_setup.rc` that was an attempt to run the script via Android's init system. **It does not work** on this device — init appears to ignore it despite correct permissions and SELinux context. The app runs the script directly instead.

## Gotchas

- **`ndc` eats stdin** — If you restructure the script with `do`/`then` on their own lines, the `ndc` commands will consume subsequent lines of the script via stdin, causing syntax errors like `break: can't break` and `'fi' unexpected`. Keep the `while`/`if` on single lines with semicolons.
- **`setprop sys.usb.config none` kills ADB** — After the script runs, USB device mode is disabled. To get ADB back, reboot with the USB cable (not Ethernet) plugged in.
- **ADB over network** — enabled by the script after USB switches to host mode (`setprop service.adb.tcp.port 5555` + `stop/start adbd`). Connect with `adb connect <frame-ip>:5555` after boot.
- **Script must be at `/data/local/tmp/eth_up.sh`** — The app hardcodes this path. The `/data/local/tmp/` directory survives reboots and app reinstalls.
- **Device needs root** — The `su` binary at `/system/xbin/su` is required. The frame ships with an unlocked bootloader and root access.
