#!/system/bin/sh
# Bring up USB Ethernet (RTL8152) and enable ADB over TCP
# Lives at /data/local/tmp/eth_up.sh on the device

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
        setprop persist.adb.tcp.port 5555
        setprop service.adb.tcp.port 5555
        kill $(pidof adbd)
        break
    fi
    sleep 1
    i=$((i + 1))
done
