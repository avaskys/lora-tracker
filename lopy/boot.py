import os
import machine
from network import WLAN
uart = machine.UART(0, 115200)
os.dupterm(uart)

wlan = WLAN() # get current object, without changing the mode

if machine.reset_cause() != machine.SOFT_RESET:
    #wlan.init(mode=WLAN.STA)
    wlan.init(mode=WLAN.AP, ssid='tracker', auth=(WLAN.WPA2,'followme'), channel=11, antenna=WLAN.INT_ANT)
    wlan.ifconfig(config=('192.168.0.254', '255.255.255.0', '192.168.0.254', '8.8.8.8'), id=1)

#if not wlan.isconnected():
    # change the line below to match your network ssid, security and password
#    wlan.connect('av123', auth=(WLAN.WPA2, 'frigidaire123'), timeout=5000)
#    while not wlan.isconnected():
#        machine.idle() # save power while waiting
