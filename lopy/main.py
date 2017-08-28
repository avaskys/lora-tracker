import machine
from network import LoRa
import socket
import struct
import time
import ucollections
import ujson
import utime

# Format of LoRa messages
# 4 bytes: header (ignored) sent by RadioHead driver
# 2 bytes: magic number: (0x2c, 0x0b)
# 4 bytes: UTF-8 callsign, padded with nulls if necessary
# 4 bytes: Sender latitude, millionths of a degree, little endian, WGS 84
# 4 bytes: Sender longitude, millionths of a degree, little endian, WGS 84
# 1 byte: Boolean, is sender accurate
wanformat = '<i2b4s2ib'
wansize = struct.calcsize(wanformat)
PositionUpdate = ucollections.namedtuple('PositionUpdate', ('rhheader', 'magic1', 'magic2', 'callsign', 'lat', 'long', 'isaccurate'))
MAGIC1 = 0x2c
MAGIC2 = 0x0b
def posToDict(pos):
    return {
        'callsign': pos.callsign.decode('utf-8').rstrip(),
        'lat': pos.lat,
        'long': pos.long,
        'isaccurate': pos.isaccurate,
    }

def log(str):
    print('%d: %s' % (time.time(), str))

# Initialize LoRa socket
#lora = LoRa(mode=LoRa.LORA, tx_power=20, bandwidth=LoRa.BW_125KHZ, sf=12, coding_rate=LoRa.CODING_4_8)
# Attempting to match Drew/Mel's LoRa config
lora = LoRa(mode=LoRa.LORA, tx_power=20, bandwidth=LoRa.BW_125KHZ, sf=7, coding_rate=LoRa.CODING_4_5, public=False)
wan = socket.socket(socket.AF_LORA, socket.SOCK_RAW)
wan.setblocking(False)

# Create LAN socket
lanport = 5309
lan = socket.socket(socket.AF_INET, socket.SOCK_DGRAM, socket.IPPROTO_UDP)
lan.bind(("", lanport))
lan.setblocking(False)

# Keep track of recent lan clients. Map from address to last seen time.
# We'll stop sending to a client after 10 minutes
lanclients = {}

# Array of position updates seen in this round of the main loop
posUpdates = []

# Map from call sign string to (positionDict, updatedTime) tuple
positions = {}

def recvWan():
    data = wan.recv(wansize)
    while len(data) > 0:
        log('Received (WAN)')
        if len(data) == wansize:
            pos = PositionUpdate(*struct.unpack(wanformat, data))
            if pos.magic1 == MAGIC1 and pos.magic2 == MAGIC2:
                posUpdates.append(pos)
                positions[pos.callsign] = (posToDict(pos), time.time())
            else:
                log('Unexpected WAN magic numbers: saw 0x%x 0x%x' % (pos.magic1, pos.magic2))
        else:
            log('Unexpected WAN message size: saw %d, expected %d' % (len(data), wansize))
        data = wan.recv(wansize)

def maybeRecvLan():
    try:
        return lan.recvfrom(1500)
    except OSError:
        # For some reason recvfrom() returns an OsError when there's no
        # data to receive on a non-blocking socket. recv() doesn't behave
        # this way. Let's just return a zero-byte array
        return ([], None)

def recvLan():
    (data, addr) = maybeRecvLan()
    while len(data) > 0:
        log('Received (LAN) from %s' % str(addr))
        try:
            msg = ujson.loads(data.decode('utf-8'))
            lanclients[addr] = time.time()
            lanHandlers[msg['type']](msg, addr)
        except ValueError:
            log('Error decoding JSON')
        except KeyError:
            log('Unknown message type in LAN message')
        (data, addr) = maybeRecvLan()

def sendLan():
    for pos in posUpdates:
        posJson = ujson.dumps(posToDict(pos)).encode('utf-8')
        for addr, lastseen in lanclients.items():
            if time.time() - lastseen > 600:
                del lanclients[addr]
            else:
                try:
                    lan.sendto(ujson.dumps(posToDict(pos)).encode('utf-8'), addr)
                except OSError:
                    pass # Maybe the client went away?

def sendWan():
    for pos in posUpdates:
        wan.send(struct.pack(wanformat, *pos))

def handleLanGetAll(msg, addr):
    for k, (pos, updated) in positions.items():
        age = time.time() - updated
        if age > 600:
            # Position is over 10 minutes old. Remove it
            del positions[k]
        else:
            pos['age'] = age
            lan.sendto(ujson.dumps(pos).encode('utf-8'), addr)

def handleLanPosUpdate(msg, addr):
    try:
        pos = PositionUpdate(0, MAGIC1, MAGIC2, msg['callsign'].encode('utf-8'), msg['lat'], msg['long'], msg['isaccurate'])
        posUpdates.append(pos)
        positions[pos.callsign] = (posToDict(pos), time.time())
    except KeyError:
        log('LAN position update missing required fields')

lanHandlers = {
    'getall' : handleLanGetAll,
    'posupdate' : handleLanPosUpdate,
}

# Main loop
while True:
    recvLan()
    sendWan()
    recvWan()
    sendLan()
    time.sleep_ms(500)
    posUpdates = []
