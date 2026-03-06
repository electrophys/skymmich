#!/system/bin/sh
# Bring up USB Ethernet (RTL8152) and enable ADB over TCP
# Lives at /data/local/tmp/eth_up.sh on the device

LOG=/data/local/tmp/eth_up.log
exec >> $LOG 2>&1

echo "--- eth_up.sh started $(date) ---"

setprop persist.adb.tcp.port 5555
setprop service.adb.tcp.port 5555
setprop sys.usb.config none
sleep 1

i=0
while [ $i -lt 60 ]; do
    if [ -d /sys/class/net/eth0 ]; then
        echo "eth0 appeared at ${i}s, MAC=$(cat /sys/class/net/eth0/address 2>/dev/null)"
        ifconfig eth0 up
        sleep 1
        dhcptool eth0 </dev/null
        ip=$(ifconfig eth0 2>/dev/null | grep 'inet addr' | sed 's/.*inet addr:\([0-9.]*\).*/\1/')
        echo "DHCP done: ip=${ip}"
        if [ -z "$ip" ]; then echo "DHCP failed"; break; fi
        ndc network create 100
        ndc network interface add 100 eth0
        ndc network route add 100 eth0 0.0.0.0/0 192.168.86.1
        ndc resolver setnetdns 100 '' 192.168.86.1 8.8.8.8
        ndc network default set 100
        setprop net.dns1 192.168.86.1
        setprop net.dns2 8.8.8.8
        echo "Network up: ip=${ip}"
        stop adbd
        sleep 1
        start adbd
        echo "adbd restarted on port $(getprop persist.adb.tcp.port)"
        break
    fi
    sleep 1
    i=$((i + 1))
done

if [ $i -ge 60 ]; then echo "TIMEOUT: eth0 never appeared"; fi
echo "eth_up.sh done"
